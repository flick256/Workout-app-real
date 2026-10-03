package app.forge.fitness.data.ai

import app.forge.domain.insights.OutputGuard
import app.forge.domain.insights.StallReport
import app.forge.domain.insights.WeeklyFacts
import app.forge.domain.insights.WeeklyReport
import app.forge.domain.nutrition.MealItem
import app.forge.domain.nutrition.PageNutrition
import app.forge.domain.nutrition.WebNutrition
import app.forge.domain.parse.SetCommand
import app.forge.fitness.data.prefs.UserPreferencesRepository
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** Text shown to you, and whether the AI wrote it (or Forge's plain version is shown). */
data class InsightText(val text: String, val byAi: Boolean, val note: String? = null)

/**
 * The only place Forge talks to the on-device model. The pattern is always the same:
 * Forge's own rules work out the facts; the model may only put those facts into words;
 * the result is checked (no new numbers, no risky diet advice) before you see it.
 */
@Singleton
class AiAssistant @Inject constructor(
    private val models: ModelManager,
    private val engine: LlmEngine,
    private val preferences: UserPreferencesRepository,
) {
    val isAvailable: Boolean get() = models.isInstalled

    suspend fun weeklySummary(facts: WeeklyFacts): InsightText {
        val plain = WeeklyReport.template(facts)
        val sheet = WeeklyReport.factSheet(facts)
        return reword(
            plain = plain,
            facts = sheet,
            prompt = "These are the facts about my last 7 days of training, worked out by my fitness app:\n$sheet\n\n" +
                "Write a short, friendly summary of my week in 3 to 5 sentences, speaking to me as \"you\". " +
                "Start with the most encouraging true fact, mention one thing to improve next week, and use only " +
                "numbers that appear in the facts.",
        )
    }

    suspend fun explainStall(exerciseName: String, report: StallReport): InsightText {
        val plain = report.asText()
        val facts = report.findings.joinToString("\n") { f -> "- ${f.text}" + (f.tip?.let { " Suggestion: $it" } ?: "") }
        return reword(
            plain = plain,
            facts = facts,
            prompt = "My fitness app analysed my $exerciseName and found:\n$facts\n\n" +
                "Explain this to me in 3 to 5 sentences: what's most likely holding it back and what to try first. " +
                "Only use the findings and suggestions above; don't add new causes or numbers.",
        )
    }

    private suspend fun reword(plain: String, facts: String, prompt: String): InsightText {
        if (!isAvailable) return InsightText(plain, byAi = false)
        val output = runCatching { engine.generate(systemPrompt(), prompt) }
            .getOrElse { return InsightText(plain, false, "The AI couldn't run (${it.message}), so here's the plain version.") }
        val verdict = OutputGuard.check(output, facts)
        return if (verdict.ok) InsightText(output, byAi = true)
        else InsightText(plain, false, "Forge hid the AI's version (${verdict.reason}) and is showing its own.")
    }

    /**
     * For log lines the simple parser can't read ("did three heavy sets of eight on bench
     * with sixty"). The model fills a small JSON form; Forge validates every field.
     */
    suspend fun parseSet(text: String): SetCommand? {
        if (!isAvailable) return null
        val output = runCatching {
            engine.generate(
                system = "You convert a gym log line into JSON. Reply with JSON only, no other text.",
                prompt = "Log line: \"$text\"\n\nReply with exactly this JSON shape: " +
                    "{\"exercise\": string, \"sets\": number, \"reps\": number or null, \"weight_kg\": number or null, " +
                    "\"seconds\": number or null}. Convert pounds to kg. If it isn't a set of an exercise, reply {\"exercise\": \"\"}.",
                maxTokens = 80,
                temperature = 0.0,
            )
        }.getOrNull() ?: return null
        val json = output.substringAfter('{', "").substringBeforeLast('}', "").let { "{$it}" }
        val parsed = runCatching { lenient.decodeFromString(ParsedSet.serializer(), json) }.getOrNull() ?: return null
        val sets = parsed.sets ?: 1
        if (parsed.exercise.isBlank() || sets !in 1..20) return null
        if (parsed.reps == null && parsed.seconds == null) return null
        if ((parsed.reps ?: 1) !in 1..200 || (parsed.weight_kg ?: 0.0) !in 0.0..600.0 || (parsed.seconds ?: 1) !in 1..3_600) return null
        return SetCommand(parsed.exercise.trim().lowercase(), sets, parsed.reps, parsed.weight_kg, parsed.seconds)
    }

    /**
     * Reads a food's nutrition from a web page's text. Every number must appear on the page
     * and the energy must match the macros, or the answer is thrown away.
     */
    suspend fun readNutrition(food: String, pageText: String): PageNutrition? {
        if (!isAvailable) return null
        val output = runCatching {
            engine.generate(
                system = "You read nutrition information from web page text and reply with JSON only. Never guess: " +
                    "copy numbers exactly as they appear in the text.",
                prompt = "Food I'm looking for: \"$food\"\n\nPage text:\n$pageText\n\n" +
                    "Reply with exactly this JSON shape: {\"found\": true or false, \"name\": string, " +
                    "\"serving\": string (e.g. \"1 burger\"), \"serving_g\": number or null, \"per_100g\": true or false, " +
                    "\"energy_kj\": number or null, \"kcal\": number or null, \"protein_g\": number, \"carbs_g\": number, " +
                    "\"fat_g\": number, \"sugars_g\": number or null, \"sodium_mg\": number or null}. Use the values per " +
                    "serving if the page gives them, otherwise per 100 g. If this page isn't about that food, reply {\"found\": false}.",
                maxTokens = 160,
                temperature = 0.0,
            )
        }.getOrNull() ?: return null
        val json = output.substringAfter('{', "").substringBeforeLast('}', "").let { "{$it}" }
        val r = runCatching { lenient.decodeFromString(ReadFood.serializer(), json) }.getOrNull() ?: return null
        if (!r.found) return null
        val kcal = r.kcal ?: r.energy_kj?.let { it / 4.184 } ?: return null
        val n = PageNutrition(
            name = r.name?.trim(),
            kcal = kcal,
            proteinG = r.protein_g ?: return null,
            carbsG = r.carbs_g ?: return null,
            fatG = r.fat_g ?: return null,
            servingG = r.serving_g,
            servingLabel = r.serving?.trim()?.takeIf { it.isNotEmpty() }?.let { s -> r.serving_g?.let { "$s (${it.toInt()} g)" } ?: s },
            perHundred = r.per_100g == true,
            sugarG = r.sugars_g,
            sodiumMg = r.sodium_mg,
        )
        return n.takeIf { WebNutrition.isPlausible(it) && WebNutrition.numbersOnPage(it, pageText) }
    }

    /**
     * For meal descriptions the simple reader can't split ("maccas brekkie, hash brown and a
     * flat white"). The model lists the foods; amounts and nutrition still come from Forge.
     */
    suspend fun parseMeal(text: String): List<MealItem>? {
        if (!isAvailable) return null
        val output = runCatching {
            engine.generate(
                system = "You split a description of a meal into separate foods and reply with JSON only.",
                prompt = "Meal: \"$text\"\n\nReply with a JSON array, one object per food: " +
                    "[{\"food\": string (plain food name, include the brand or chain if said, e.g. \"McDonald's hash brown\"), " +
                    "\"quantity\": number or null, \"unit\": one of \"g\", \"ml\", \"cup\", \"slice\", \"piece\", " +
                    "\"tablespoon\", \"teaspoon\", \"serve\" or null}]. Expand slang (maccas = McDonald's, brekkie = breakfast). " +
                    "Don't add foods that weren't mentioned.",
                maxTokens = 220,
                temperature = 0.0,
            )
        }.getOrNull() ?: return null
        val json = output.substringAfter('[', "").substringBeforeLast(']', "").let { "[$it]" }
        val items = runCatching { lenient.decodeFromString(ListSerializer(MealPart.serializer()), json) }.getOrNull() ?: return null
        val units = setOf("g", "ml", "cup", "slice", "piece", "tablespoon", "teaspoon", "serve")
        return items.mapNotNull { p ->
            val food = p.food.trim().lowercase().takeIf { it.isNotEmpty() && it.length <= 60 } ?: return@mapNotNull null
            // Only words from what you typed (plus brand spellings) count, so the model can't add foods.
            if (food.split(' ').none { w -> w.length > 2 && text.lowercase().contains(w.take(4)) }) return@mapNotNull null
            MealItem(p.quantity?.takeIf { it > 0 && it <= 2_000 }, p.unit?.takeIf { it in units }, food)
        }.takeIf { it.isNotEmpty() && it.size <= 12 }
    }

    @Serializable
    private data class MealPart(val food: String = "", val quantity: Double? = null, val unit: String? = null)

    @Serializable
    @Suppress("PropertyName")
    private data class ReadFood(
        val found: Boolean = false,
        val name: String? = null,
        val serving: String? = null,
        val serving_g: Double? = null,
        val per_100g: Boolean? = false,
        val energy_kj: Double? = null,
        val kcal: Double? = null,
        val protein_g: Double? = null,
        val carbs_g: Double? = null,
        val fat_g: Double? = null,
        val sugars_g: Double? = null,
        val sodium_mg: Double? = null,
    )

    private suspend fun systemPrompt(): String {
        val prefs = preferences.preferences.first()
        val teen = prefs.birthYear?.let { LocalDate.now().year - it < 18 } ?: false
        return "You are a supportive, knowledgeable strength coach inside a personal fitness app" +
            (if (teen) " used by a teenager who trains at home." else ".") +
            " Rules: use only the facts you are given and never invent numbers or data; be brief, specific and " +
            "encouraging; plain sentences, no headings, lists or markdown; never recommend dieting, calorie " +
            "restriction, fasting, weight cutting or supplements; for pain or injury, suggest seeing a doctor or physio."
    }

    @Serializable
    @Suppress("PropertyName")
    private data class ParsedSet(
        val exercise: String = "",
        val sets: Int? = 1,
        val reps: Int? = null,
        val weight_kg: Double? = null,
        val seconds: Int? = null,
    )

    private companion object {
        val lenient = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }
    }
}

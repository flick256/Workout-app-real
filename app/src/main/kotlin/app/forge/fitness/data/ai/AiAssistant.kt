package app.forge.fitness.data.ai

import app.forge.domain.insights.OutputGuard
import app.forge.domain.insights.StallReport
import app.forge.domain.insights.WeeklyFacts
import app.forge.domain.insights.WeeklyReport
import app.forge.domain.parse.SetCommand
import app.forge.fitness.data.prefs.UserPreferencesRepository
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
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

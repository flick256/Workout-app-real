package app.forge.domain.nutrition

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject

/** One search result: enough to show it in a list ("Per 1 burger - Calories: 740kcal | ..."). */
data class FatSecretHit(
    val id: String,
    val name: String,
    val brand: String?,
    /** e.g. "1 burger", "100g". */
    val per: String?,
    val kcal: Double?,
    val proteinG: Double?,
    val carbsG: Double?,
    val fatG: Double?,
)

/** A FatSecret error, e.g. code 21 "Invalid IP address detected". */
data class FatSecretError(val code: Int, val message: String)

/**
 * Reads FatSecret Platform API responses (server.api, format=json). FatSecret sends a
 * single object instead of a list when there's only one item, and numbers as strings.
 */
object FatSecret {
    private val json = Json { ignoreUnknownKeys = true }

    fun error(body: String): FatSecretError? {
        val root = parse(body) ?: return null
        val e = root["error"] as? JsonObject ?: return null
        return FatSecretError((e["code"] as? JsonPrimitive)?.intOrNull ?: 0, e.string("message") ?: "FatSecret error")
    }

    /** `foods.search` results, in FatSecret's order. */
    fun parseSearch(body: String): List<FatSecretHit> {
        val foods = parse(body)?.get("foods") as? JsonObject ?: return emptyList()
        return foods["food"].objects().mapNotNull { f ->
            val id = f.string("food_id") ?: return@mapNotNull null
            val name = f.string("food_name")?.trim()?.takeIf(String::isNotEmpty) ?: return@mapNotNull null
            val description = f.string("food_description").orEmpty()
            FatSecretHit(
                id = id,
                name = name,
                brand = f.string("brand_name")?.trim()?.takeIf(String::isNotEmpty),
                per = Regex("^Per (.+?) -", RegexOption.IGNORE_CASE).find(description)?.groupValues?.get(1)?.trim(),
                kcal = described(description, "Calories"),
                proteinG = described(description, "Protein"),
                carbsG = described(description, "Carbs"),
                fatG = described(description, "Fat"),
            )
        }
    }

    /**
     * `food.get`: the food with a serving that has a weight (preferring the default one),
     * turned into per-100 g values. Without any weighed serving, the default serving stands
     * in as "100 g" so the numbers per serving are still right, and the label says so.
     */
    fun parseFood(body: String): FoodInfo? {
        val food = parse(body)?.get("food") as? JsonObject ?: return null
        val name = food.string("food_name")?.trim()?.takeIf(String::isNotEmpty) ?: return null
        val servings = (food["servings"] as? JsonObject)?.get("serving").objects()
        if (servings.isEmpty()) return null
        fun weighed(s: JsonObject): Double? =
            s.number("metric_serving_amount")?.takeIf { it > 0 && s.string("metric_serving_unit")?.lowercase() in setOf("g", "ml") }
        val serving = servings.filter { weighed(it) != null }.let { w ->
            w.firstOrNull { it.string("is_default") == "1" } ?: w.firstOrNull()
        } ?: servings.firstOrNull { it.string("is_default") == "1" } ?: servings.first()
        val kcal = serving.number("calories") ?: return null
        val grams = weighed(serving)
        val scale = 100.0 / (grams ?: 100.0)
        val description = serving.string("serving_description")?.trim()
        return FoodInfo(
            name = name,
            brand = food.string("brand_name")?.trim()?.takeIf(String::isNotEmpty),
            per100g = Nutrients(
                kcal = kcal * scale,
                proteinG = (serving.number("protein") ?: 0.0) * scale,
                carbsG = (serving.number("carbohydrate") ?: 0.0) * scale,
                fatG = (serving.number("fat") ?: 0.0) * scale,
                fiberG = serving.number("fiber")?.times(scale),
                sugarG = serving.number("sugar")?.times(scale),
                // FatSecret gives sodium in mg.
                saltG = serving.number("sodium")?.let { it * 2.5 / 1000 * scale },
            ),
            servingG = grams ?: 100.0,
            servingLabel = when {
                description == null -> null
                grams != null -> description
                else -> "$description (weight unknown, counted as 100 g)"
            },
        )
    }

    /** "Calories: 740kcal | Fat: 42.00g" → 740.0 for "Calories". */
    private fun described(text: String, label: String): Double? =
        Regex("$label:\\s*([0-9]+(?:[.,][0-9]+)?)", RegexOption.IGNORE_CASE).find(text)
            ?.groupValues?.get(1)?.replace(',', '.')?.toDoubleOrNull()

    private fun parse(body: String): JsonObject? = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull()

    private fun JsonElement?.objects(): List<JsonObject> = when (this) {
        is JsonArray -> mapNotNull { it as? JsonObject }
        is JsonObject -> listOf(this)
        else -> emptyList()
    }

    private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

    private fun JsonObject.number(key: String): Double? {
        val p = this[key] as? JsonPrimitive ?: return null
        return (p.doubleOrNull ?: p.contentOrNull?.toDoubleOrNull())?.takeIf { it.isFinite() && it >= 0 }
    }
}

package app.forge.domain.nutrition

import kotlin.math.roundToInt

/** A household portion of a food: "1 slice" = 30 g. */
data class Portion(val label: String, val grams: Double)

/** One thing from a typed meal: "2 slices of toast" → 2, "slice", "toast". */
data class MealItem(val quantity: Double?, val unit: String?, val food: String)

/** How much of a food, in grams, and how Forge got there ("2 × 1 slice (30 g)"). */
data class Amount(val grams: Double, val explanation: String)

/**
 * Turns "2 weet-bix with milk and a banana" into items, then into grams using the food's
 * own portions (from the Australian food database or a saved serving). Rules first; the
 * AI is only asked when these can't make sense of it.
 */
object MealSentence {

    fun parse(text: String): List<MealItem> {
        var t = " " + text.lowercase()
            .replace('&', ' ').replace("+", ",").replace('\n', ',').replace(Regex("[.!?]"), ",") + " "
        t = t.replace(" & ", " and ")
        return t.split(Regex("\\s*(?:,|;|\\band then\\b|\\bthen\\b|\\bplus\\b|\\balso\\b)\\s*"))
            .flatMap(::splitAnd)
            .mapNotNull(::item)
    }

    /** "a burger and fries" → two items, but "fish and chips" stays one food. */
    private fun splitAnd(part: String): List<String> {
        var protected = part
        COMPOUNDS.forEachIndexed { i, c -> protected = protected.replace(c, "§$i§") }
        return protected.split(Regex("\\s+(?:and|with|n)\\s+"))
            .map { piece -> COMPOUNDS.foldIndexed(piece) { i, acc, c -> acc.replace("§$i§", c) } }
    }

    private fun item(raw: String): MealItem? {
        var s = raw.trim()
        FILLERS.forEach { f -> s = s.replace(Regex("^$f\\b\\s*"), "") }
        s = s.trim()
        if (s.isEmpty()) return null

        var quantity: Double? = null
        var unit: String? = null
        // "100g rice", "250 ml milk", "1.5 cups".
        Regex("^(\\d+(?:[.,]\\d+)?|\\d+/\\d+)\\s*([a-z]+)?\\b\\s*").find(s)?.let { m ->
            quantity = number(m.groupValues[1])
            val word = m.groupValues[2]
            if (word.isNotEmpty() && unitOf(word) != null) {
                unit = unitOf(word)
                s = s.substring(m.range.last + 1)
            } else {
                s = s.substring(m.groupValues[1].length).trim()
            }
        } ?: run {
            WORD_NUMBERS.entries.sortedByDescending { it.key.length }.firstOrNull { (w, _) -> s.startsWith("$w ") }?.let { (w, n) ->
                quantity = n
                s = s.removePrefix(w).trim()
            }
        }
        if (unit == null) {
            val first = s.substringBefore(' ')
            unitOf(first)?.let { u ->
                unit = u
                s = s.removePrefix(first).trim()
            }
        }
        s = s.removePrefix("of ").trim().removePrefix("the ").trim().trim(',', ' ')
        if (s.isEmpty() || s.length > 60) return null
        return MealItem(quantity, unit, s)
    }

    /**
     * Grams for an item. Weights and volumes are used as given; household units use the
     * food's matching portion (or a standard one: a cup is 250 ml); a plain count uses the
     * food's serving or its "whole item" portion.
     */
    fun amount(item: MealItem, portions: List<Portion>, servingG: Double?, servingLabel: String?): Amount {
        val q = item.quantity ?: 1.0
        fun times(label: String, grams: Double) =
            Amount(q * grams, (if (q == 1.0) "" else "${format(q)} × ") + "$label (${grams.roundToInt()} g)")
        when (val u = item.unit) {
            "g" -> return Amount(q, "${format(q)} g")
            "kg" -> return Amount(q * 1000, "${format(q)} kg")
            "ml" -> return Amount(q, "${format(q)} ml")
            "l" -> return Amount(q * 1000, "${format(q)} L")
            null -> Unit
            "serve" -> {
                servingG?.let { return times(servingLabel ?: "1 serve", it) }
                portions.firstOrNull()?.let { return times(it.label, it.grams) }
                return times("1 serve", 100.0)
            }
            else -> {
                portions.firstOrNull { p -> wordsOf(p.label).any { it == u || it == u + "s" || it.removeSuffix("s") == u } }
                    ?.let { return times(it.label, it.grams) }
                STANDARD[u]?.let { return times("1 $u", it) }
            }
        }
        servingG?.let { return times(servingLabel ?: "1 serve", it) }
        val whole = portions.firstOrNull { p -> WHOLE_WORDS.any { w -> w in wordsOf(p.label) } && VOLUME_WORDS.none { it in wordsOf(p.label) } }
            ?: portions.firstOrNull { p -> VOLUME_WORDS.none { it in wordsOf(p.label) } }
            ?: portions.firstOrNull()
        whole?.let { return times(it.label, it.grams) }
        return times("100 g", 100.0)
    }

    private fun wordsOf(label: String) = label.lowercase().split(Regex("[^a-z]+")).filter { it.isNotEmpty() }

    private fun number(s: String): Double? =
        if ('/' in s) s.split('/').let { (a, b) -> a.toDoubleOrNull()?.let { x -> b.toDoubleOrNull()?.takeIf { it != 0.0 }?.let { x / it } } }
        else s.replace(',', '.').toDoubleOrNull()

    private fun format(q: Double) = if (q % 1.0 == 0.0) q.toInt().toString() else "%.1f".format(q).trimEnd('0').trimEnd('.')

    private fun unitOf(word: String): String? = UNITS[word]

    private val UNITS: Map<String, String> = buildMap {
        listOf("g", "gm", "gms", "gram", "grams").forEach { put(it, "g") }
        listOf("kg", "kgs", "kilo", "kilos", "kilogram", "kilograms").forEach { put(it, "kg") }
        listOf("ml", "mls", "millilitre", "millilitres", "milliliter", "milliliters").forEach { put(it, "ml") }
        listOf("l", "litre", "litres", "liter", "liters").forEach { put(it, "l") }
        listOf("cup", "cups").forEach { put(it, "cup") }
        listOf("slice", "slices").forEach { put(it, "slice") }
        listOf("piece", "pieces", "pc", "pcs").forEach { put(it, "piece") }
        listOf("tbsp", "tablespoon", "tablespoons", "tbs").forEach { put(it, "tablespoon") }
        listOf("tsp", "teaspoon", "teaspoons").forEach { put(it, "teaspoon") }
        listOf("bowl", "bowls").forEach { put(it, "bowl") }
        listOf("glass", "glasses").forEach { put(it, "glass") }
        listOf("can", "cans", "tin", "tins").forEach { put(it, "can") }
        listOf("bottle", "bottles").forEach { put(it, "bottle") }
        listOf("scoop", "scoops").forEach { put(it, "scoop") }
        listOf("handful", "handfuls").forEach { put(it, "handful") }
        listOf("serve", "serves", "serving", "servings", "portion", "portions").forEach { put(it, "serve") }
        listOf("packet", "packets", "pack", "packs", "bag", "bags").forEach { put(it, "packet") }
    }

    /** Standard sizes when the food has no portion of that kind. */
    private val STANDARD = mapOf(
        "cup" to 250.0, "tablespoon" to 20.0, "teaspoon" to 5.0, "glass" to 250.0, "can" to 375.0, "bottle" to 600.0,
        "bowl" to 300.0, "slice" to 35.0, "piece" to 50.0, "scoop" to 30.0, "handful" to 30.0, "packet" to 45.0,
    )

    private val WHOLE_WORDS = setOf("medium", "whole", "piece", "slice", "biscuit", "serve", "item", "small", "large", "egg", "roll", "bar", "fillet")
    private val VOLUME_WORDS = setOf("cup", "tablespoon", "teaspoon", "tbsp", "tsp", "ml")

    private val WORD_NUMBERS = mapOf(
        "a" to 1.0, "an" to 1.0, "one" to 1.0, "two" to 2.0, "three" to 3.0, "four" to 4.0, "five" to 5.0, "six" to 6.0,
        "seven" to 7.0, "eight" to 8.0, "nine" to 9.0, "ten" to 10.0, "half a" to 0.5, "half an" to 0.5, "half" to 0.5,
        "a half" to 0.5, "a couple of" to 2.0, "couple of" to 2.0, "a couple" to 2.0, "a few" to 3.0, "few" to 3.0,
        "a dozen" to 12.0, "dozen" to 12.0, "quarter of a" to 0.25, "a quarter of a" to 0.25,
    )

    private val FILLERS = listOf(
        "i had", "i ate", "i've had", "had", "ate", "for breakfast", "for lunch", "for dinner", "for tea", "for a snack",
        "some", "a bit of", "a little", "just", "about", "around", "like",
    )

    private val COMPOUNDS = listOf(
        "fish and chips", "mac and cheese", "macaroni and cheese", "salt and vinegar", "bacon and egg", "ham and cheese",
        "peanut butter and jelly", "sweet and sour", "bread and butter", "cheese and tomato", "spinach and ricotta",
        "chicken and mushroom", "steak and kidney", "fruit and nut", "salt and pepper", "sausage and egg",
    )
}

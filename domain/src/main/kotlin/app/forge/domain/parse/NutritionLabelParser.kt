package app.forge.domain.parse

import kotlin.math.roundToInt

enum class LabelBasis { PER_100G, PER_SERVING }

enum class LabelField(val label: String) {
    ENERGY_KCAL("Energy"), PROTEIN("Protein"), FAT("Fat"), CARBS("Carbohydrate"),
    SUGARS("Sugars"), FIBRE("Fibre"), SALT("Salt"),
}

/** What was read off a nutrition panel. Always shown to you to check before saving. */
data class LabelReading(
    val basis: LabelBasis,
    val values: Map<LabelField, Double>,
    val servingG: Double? = null,
) {
    val missing: List<LabelField> get() = listOf(LabelField.ENERGY_KCAL, LabelField.PROTEIN, LabelField.FAT, LabelField.CARBS).filter { it !in values }
    val isUseful: Boolean get() = LabelField.ENERGY_KCAL in values || values.size >= 2
}

/**
 * Reads the text of a nutrition information panel (from on-device text recognition).
 * Built for Australian/NZ panels ("per serving" and "per 100 g" columns, energy in kJ,
 * sodium in mg) and also copes with US/EU-style labels.
 *
 * Input is the panel as rows of text, left to right, as the camera saw them.
 */
object NutritionLabelParser {

    private val number = Regex("""(\d+(?:[.,]\d+)?)\s*(kj|kcal|cal|mg|g|ml)?""")
    private val servingSize = Regex("""serv\w*\s*size[^\d]{0,12}(\d+(?:[.,]\d+)?)\s*(g|ml)""")

    fun parse(rows: List<String>): LabelReading? {
        val lines = rows.map(::normalize).filter { it.isNotBlank() }
        if (lines.isEmpty()) return null
        val all = lines.joinToString(" ")
        val serving = servingSize.find(all)?.groupValues?.get(1)?.toNumber()

        val header = lines.firstOrNull { ("100g" in it.replace(" ", "") || "100ml" in it.replace(" ", "")) }
        val hasPer100 = header != null
        // Which number in each row is the per-100 g one. AU panels put "per serving" first.
        val per100Index = header?.let { h ->
            val servingAt = h.indexOf("serv")
            val hundredAt = h.replace(" ", "").indexOf("100g").takeIf { it >= 0 } ?: h.replace(" ", "").indexOf("100ml")
            if (servingAt in 0 until hundredAt) 1 else 0
        } ?: 0

        val values = mutableMapOf<LabelField, Double>()
        for (line in lines) {
            val field = fieldOf(line) ?: continue
            if (field in values) continue
            val numbers = number.findAll(line.substringAfter(keywordOf(field, line)))
                .map { it.groupValues[1].toNumber() to it.groupValues[2] }
                .filter { it.first != null }
                .map { it.first!! to it.second }
                .toList()
            if (numbers.isEmpty()) continue
            val value = when (field) {
                LabelField.ENERGY_KCAL -> {
                    val kj = numbers.filter { it.second == "kj" }
                    val cal = numbers.filter { it.second == "kcal" || it.second == "cal" }
                    when {
                        kj.isNotEmpty() -> pick(kj.map { it.first }, per100Index, hasPer100)?.div(KJ_PER_KCAL)
                        cal.isNotEmpty() -> pick(cal.map { it.first }, per100Index, hasPer100)
                        // No unit: AU panels are kJ, anything over ~950 per 100 g must be kJ.
                        else -> pick(numbers.map { it.first }, per100Index, hasPer100)?.let { if (it > 950) it / KJ_PER_KCAL else it }
                    }
                }
                LabelField.SALT -> {
                    val v = pick(numbers.map { it.first }, per100Index, hasPer100)
                    val unit = numbers.firstOrNull()?.second
                    when {
                        v == null -> null
                        line.contains("sodium") && unit == "mg" -> v * 2.5 / 1000
                        line.contains("sodium") -> v * 2.5
                        unit == "mg" -> v / 1000
                        else -> v
                    }
                }
                else -> pick(numbers.map { it.first }, per100Index, hasPer100)
            } ?: continue
            if (value in 0.0..MAX_PER_100) values[field] = (value * 10).roundToInt() / 10.0
        }
        if (values.isEmpty()) return null
        return LabelReading(if (hasPer100) LabelBasis.PER_100G else LabelBasis.PER_SERVING, values, serving)
    }

    private fun pick(numbers: List<Double>, per100Index: Int, hasPer100: Boolean): Double? = when {
        numbers.isEmpty() -> null
        !hasPer100 -> numbers.first()
        numbers.size == 1 -> numbers.first()
        else -> numbers[per100Index.coerceAtMost(numbers.lastIndex)]
    }

    private fun fieldOf(line: String): LabelField? = when {
        line.contains("energy") || line.startsWith("calories") -> LabelField.ENERGY_KCAL
        line.contains("protein") -> LabelField.PROTEIN
        line.contains("saturated") || line.contains("trans") || line.contains("mono") || line.contains("poly") -> null
        line.contains("sugar") -> LabelField.SUGARS
        line.contains("fibre") || line.contains("fiber") -> LabelField.FIBRE
        line.contains("sodium") || line.contains("salt") -> LabelField.SALT
        line.contains("carbohydrate") || line.contains("carbs") -> LabelField.CARBS
        line.contains("fat") -> LabelField.FAT
        else -> null
    }

    /** Numbers are read after the nutrient's name (so "Fat, total" doesn't read a stray digit before it). */
    private fun keywordOf(field: LabelField, line: String): String = when (field) {
        LabelField.ENERGY_KCAL -> if ("energy" in line) "energy" else "calories"
        LabelField.PROTEIN -> "protein"
        LabelField.SUGARS -> "sugar"
        LabelField.FIBRE -> if ("fibre" in line) "fibre" else "fiber"
        LabelField.SALT -> if ("sodium" in line) "sodium" else "salt"
        LabelField.CARBS -> if ("carbohydrate" in line) "carbohydrate" else "carbs"
        LabelField.FAT -> "fat"
    }

    private fun normalize(s: String): String = s.lowercase()
        // OCR often reads 0 as the letter O right after a digit ("2O0", "1O g").
        .replace(Regex("""(?<=\d)o"""), "0")
        .replace(Regex("""(?<=\d),(?=\d)"""), ".")
        .replace("kilojoules", "kj")

    private fun String.toNumber(): Double? = replace(',', '.').toDoubleOrNull()

    private const val KJ_PER_KCAL = 4.184
    private const val MAX_PER_100 = 1_000.0
}

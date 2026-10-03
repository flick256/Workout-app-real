package app.forge.domain.nutrition

import kotlin.math.roundToInt

/** Energy and macros. Per 100 g for foods; absolute for a logged portion or a day. */
data class Nutrients(
    val kcal: Double = 0.0,
    val proteinG: Double = 0.0,
    val carbsG: Double = 0.0,
    val fatG: Double = 0.0,
    val fiberG: Double? = null,
    val sugarG: Double? = null,
    val saltG: Double? = null,
) {
    operator fun plus(o: Nutrients) = Nutrients(
        kcal + o.kcal,
        proteinG + o.proteinG,
        carbsG + o.carbsG,
        fatG + o.fatG,
        sumOrNull(fiberG, o.fiberG),
        sumOrNull(sugarG, o.sugarG),
        sumOrNull(saltG, o.saltG),
    )

    operator fun times(factor: Double) = Nutrients(
        kcal * factor,
        proteinG * factor,
        carbsG * factor,
        fatG * factor,
        fiberG?.times(factor),
        sugarG?.times(factor),
        saltG?.times(factor),
    )

    /** For a portion of [grams] of a food whose values are per 100 g. */
    fun forGrams(grams: Double): Nutrients = this * (grams / 100.0)

    /**
     * Calories implied by the macros (4/4/9 kcal per gram). Used to fill in energy when
     * a label leaves it out, and to sanity-check food you enter yourself.
     */
    val kcalFromMacros: Double get() = proteinG * 4 + carbsG * 4 + fatG * 9

    companion object {
        val ZERO = Nutrients()
        private fun sumOrNull(a: Double?, b: Double?) = if (a == null && b == null) null else (a ?: 0.0) + (b ?: 0.0)
    }
}

enum class Meal(val label: String) {
    BREAKFAST("Breakfast"),
    LUNCH("Lunch"),
    DINNER("Dinner"),
    SNACKS("Snacks");

    companion object {
        /** A sensible default for "add food" at this time of day. */
        fun forHour(hour: Int): Meal = when (hour) {
            in 4..10 -> BREAKFAST
            in 11..14 -> LUNCH
            in 17..21 -> DINNER
            else -> SNACKS
        }

        fun fromKey(key: String?): Meal = entries.firstOrNull { it.name == key } ?: SNACKS
    }
}

/** A food as Forge stores it: values per 100 g (or 100 ml), plus an optional serving. */
data class FoodInfo(
    val name: String,
    val brand: String? = null,
    val barcode: String? = null,
    val per100g: Nutrients,
    /** e.g. 30.0 for "1 bar (30 g)". */
    val servingG: Double? = null,
    val servingLabel: String? = null,
)

object NutritionFormat {
    fun grams(value: Double): String = if (value >= 10 || value == 0.0) "${value.roundToInt()} g" else "${(value * 10).roundToInt() / 10.0} g"
}

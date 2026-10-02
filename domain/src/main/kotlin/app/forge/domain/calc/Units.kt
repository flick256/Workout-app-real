package app.forge.domain.calc

import app.forge.domain.model.WeightUnit
import kotlin.math.roundToLong

/** Weights are always stored in kg; these helpers convert for display and input. */
object Units {
    const val KG_PER_LB = 0.45359237

    fun kgToLb(kg: Double): Double = kg / KG_PER_LB

    fun lbToKg(lb: Double): Double = lb * KG_PER_LB

    fun fromKg(kg: Double, unit: WeightUnit): Double = when (unit) {
        WeightUnit.KG -> kg
        WeightUnit.LB -> kgToLb(kg)
    }

    fun toKg(value: Double, unit: WeightUnit): Double = when (unit) {
        WeightUnit.KG -> value
        WeightUnit.LB -> lbToKg(value)
    }

    /** Rounds to the nearest multiple of [step], e.g. 2.5 for plates or 1.0 for dumbbells. */
    fun roundTo(value: Double, step: Double): Double {
        require(step > 0) { "step must be positive" }
        return (value / step).roundToLong() * step
    }

    /** "60", "62.5", "61.25": no trailing zeros, at most 2 decimals. */
    fun format(value: Double): String {
        val rounded = (value * 100).roundToLong() / 100.0
        return if (rounded % 1.0 == 0.0) rounded.toLong().toString() else rounded.toString()
    }
}

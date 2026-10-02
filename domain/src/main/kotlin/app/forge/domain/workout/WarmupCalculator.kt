package app.forge.domain.workout

import app.forge.domain.calc.Units

data class WarmupSet(val weightKg: Double, val reps: Int)

/**
 * Builds a short warm-up ramp before your first working set.
 *
 * The heavier the working weight, the more steps:
 *  - under 20 kg: 1 set (50% × 10)
 *  - 20–40 kg:    2 sets (50% × 8, 75% × 4)
 *  - 40 kg+:      3 sets (40% × 8, 60% × 5, 80% × 3)
 *
 * Each weight is rounded to something you can actually load: one of your owned weights
 * if you've entered any, otherwise the nearest [step]. Duplicates and anything not
 * lighter than the working weight are dropped.
 */
object WarmupCalculator {
    fun plan(
        workingWeightKg: Double,
        step: Double = 2.5,
        ownedWeights: List<Double> = emptyList(),
    ): List<WarmupSet> {
        if (workingWeightKg <= 0) return emptyList()
        val ramp = when {
            workingWeightKg < 20 -> listOf(0.5 to 10)
            workingWeightKg < 40 -> listOf(0.5 to 8, 0.75 to 4)
            else -> listOf(0.4 to 8, 0.6 to 5, 0.8 to 3)
        }
        val seen = mutableSetOf<Double>()
        return ramp.mapNotNull { (fraction, reps) ->
            val raw = workingWeightKg * fraction
            val weight = if (ownedWeights.isNotEmpty()) {
                val lighter = ownedWeights.filter { it < workingWeightKg }
                if (lighter.isEmpty()) return@mapNotNull null
                AvailableWeights.snap(raw, lighter)
            } else {
                Units.roundTo(raw, step)
            }
            if (weight <= 0 || weight >= workingWeightKg || !seen.add(weight)) null
            else WarmupSet(weight, reps)
        }
    }
}

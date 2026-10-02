package app.forge.domain.workout

import app.forge.domain.calc.Units
import kotlin.math.abs

/**
 * The specific weights you own for one piece of equipment (e.g. bag: 10, 15 kg).
 * Used for quick-pick chips and to snap suggested weights to something real.
 */
object AvailableWeights {
    /** Every weight from [min] to [max] in [step]s, e.g. an adjustable dumbbell 2.5–24 kg. */
    fun range(min: Double, max: Double, step: Double): List<Double> {
        require(step > 0) { "step must be positive" }
        require(min >= 0 && max >= min) { "need 0 ≤ min ≤ max" }
        val count = ((max - min) / step + 1e-9).toInt()
        return (0..count).map { Units.roundTo(min + it * step, 0.01) }
    }

    /** Sorted, positive, de-duplicated (to the nearest 10 g). */
    fun normalize(weights: Collection<Double>): List<Double> =
        weights.filter { it > 0 }.map { Units.roundTo(it, 0.01) }.distinct().sorted()

    /**
     * The owned weight closest to [target]. Ties go to the lighter one (safer).
     * Returns [target] unchanged if nothing is owned.
     */
    fun snap(target: Double, owned: List<Double>): Double =
        owned.minWithOrNull(compareBy<Double> { abs(it - target) }.thenBy { it }) ?: target
}

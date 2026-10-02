package app.forge.domain.calc

/**
 * Estimated one-rep max (e1RM) formulas.
 *
 * All estimates get less reliable at high reps, so anything above [MAX_RELIABLE_REPS]
 * effective reps returns null instead of a misleading number.
 */
object OneRepMax {
    const val MAX_RELIABLE_REPS = 12

    /** Epley: w × (1 + r / 30). Exact for a single (r = 1 returns w). */
    fun epley(weight: Double, reps: Int): Double? {
        if (!valid(weight, reps)) return null
        if (reps == 1) return weight
        return weight * (1 + reps / 30.0)
    }

    /** Brzycki: w × 36 / (37 − r). */
    fun brzycki(weight: Double, reps: Int): Double? {
        if (!valid(weight, reps)) return null
        return weight * 36.0 / (37.0 - reps)
    }

    /**
     * Best-guess e1RM: the average of Epley and Brzycki, with reps in reserve added
     * when an RPE is given. For example, 5 reps at RPE 8 counts as 7 effective reps,
     * because you had about 2 more in the tank.
     */
    fun estimate(weight: Double, reps: Int, rpe: Double? = null): Double? {
        val effectiveReps = reps + repsInReserve(rpe)
        val e = epley(weight, effectiveReps) ?: return null
        val b = brzycki(weight, effectiveReps) ?: return null
        return (e + b) / 2.0
    }

    /** RPE 10 = 0 reps left, RPE 8 = 2 left. Values outside 6–10 are clamped. */
    fun repsInReserve(rpe: Double?): Int {
        if (rpe == null) return 0
        return (10.0 - rpe.coerceIn(6.0, 10.0)).toInt()
    }

    private fun valid(weight: Double, reps: Int) =
        weight > 0 && reps in 1..MAX_RELIABLE_REPS
}

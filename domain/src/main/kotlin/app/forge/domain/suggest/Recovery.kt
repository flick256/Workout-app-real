package app.forge.domain.suggest

import app.forge.domain.model.Muscle
import kotlin.math.pow

/** Sets done at one moment. Primary muscles count a full set each, secondary ones half. */
data class MuscleWork(
    val atMillis: Long,
    val primary: List<Muscle>,
    val secondary: List<Muscle>,
    val sets: Double = 1.0,
)

data class MuscleStatus(
    val muscle: Muscle,
    /** 1.0 = fully recovered, 0.0 = just hammered. */
    val readiness: Double,
    /** Sets in the last 7 days (secondary muscles count half). */
    val weeklySets: Double,
    val weeklyTarget: Int,
) {
    /** How far short of the weekly target, 0..1. */
    val deficit: Double get() = ((weeklyTarget - weeklySets) / weeklyTarget).coerceIn(0.0, 1.0)

    /** How much it makes sense to train this muscle now. */
    val priority: Double get() = readiness * (0.5 + deficit)
}

/**
 * A simple, explainable recovery model.
 *
 * Every hard set leaves some fatigue in the muscles it works, and that fatigue halves
 * every 48 hours for smaller muscles and every 72 hours for big ones (legs, back, chest).
 * About 6 recent sets' worth of fatigue counts as "not recovered yet".
 *
 * Weekly targets of roughly 10 sets for big muscles and 6–8 for small ones sit in the
 * range research links to good muscle growth (about 10+ hard sets per muscle per week).
 */
object Recovery {
    const val FATIGUED_SETS = 6.0
    const val SECONDARY_SHARE = 0.5
    private const val HOUR = 3_600_000.0
    private const val WEEK = 7 * 24 * HOUR

    private val large = setOf(
        Muscle.QUADRICEPS, Muscle.HAMSTRINGS, Muscle.GLUTES, Muscle.LATS,
        Muscle.CHEST, Muscle.MIDDLE_BACK, Muscle.LOWER_BACK,
    )
    private val small = setOf(Muscle.CALVES, Muscle.FOREARMS, Muscle.NECK, Muscle.ABDUCTORS, Muscle.ADDUCTORS, Muscle.TRAPS)

    fun halfLifeHours(m: Muscle): Double = if (m in large) 72.0 else 48.0

    fun weeklyTarget(m: Muscle): Int = when (m) {
        in large -> 10
        in small -> 6
        else -> 8
    }

    fun status(work: List<MuscleWork>, nowMillis: Long): List<MuscleStatus> {
        val fatigue = mutableMapOf<Muscle, Double>()
        val weekly = mutableMapOf<Muscle, Double>()
        work.filter { it.atMillis <= nowMillis }.forEach { w ->
            val ageMs = (nowMillis - w.atMillis).toDouble()
            fun add(m: Muscle, share: Double) {
                val sets = w.sets * share
                fatigue.merge(m, sets * 0.5.pow(ageMs / HOUR / halfLifeHours(m)), Double::plus)
                if (ageMs <= WEEK) weekly.merge(m, sets, Double::plus)
            }
            w.primary.forEach { add(it, 1.0) }
            w.secondary.filter { it !in w.primary }.forEach { add(it, SECONDARY_SHARE) }
        }
        return Muscle.entries.map { m ->
            MuscleStatus(
                muscle = m,
                readiness = (1.0 - (fatigue[m] ?: 0.0) / FATIGUED_SETS).coerceIn(0.0, 1.0),
                weeklySets = weekly[m] ?: 0.0,
                weeklyTarget = weeklyTarget(m),
            )
        }
    }
}

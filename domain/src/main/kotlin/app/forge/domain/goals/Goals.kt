package app.forge.domain.goals

import kotlin.math.abs

enum class GoalKind(val label: String, val description: String) {
    WORKOUTS_PER_WEEK("Workouts per week", "Train this many times every week"),
    BODYWEIGHT("Bodyweight", "Reach a bodyweight (up or down)"),
    LIFT("Strength", "Lift this weight for one rep (estimated from your sets)"),
    REPS("Reps in one set", "e.g. 20 push-ups or 10 pull-ups in a row"),
    PROTEIN_DAYS("Protein days per week", "Hit your protein target this many days a week"),
}

/** How far along a goal is. [fraction] is 0..1; [done] when reached. */
data class GoalProgress(
    val current: Double?,
    val target: Double,
    val fraction: Double,
    val done: Boolean,
)

object GoalMath {

    /** Weekly goals restart every Monday. */
    fun weekly(countThisWeek: Int, target: Int): GoalProgress {
        val t = target.coerceAtLeast(1)
        return GoalProgress(countThisWeek.toDouble(), t.toDouble(), (countThisWeek.toDouble() / t).coerceIn(0.0, 1.0), countThisWeek >= t)
    }

    /**
     * Bodyweight works both ways: losing from 80 → 75, or gaining from 60 → 65. Progress is
     * how much of the distance from where you started you've covered.
     */
    fun bodyweight(startKg: Double, currentKg: Double?, targetKg: Double): GoalProgress {
        if (currentKg == null) return GoalProgress(null, targetKg, 0.0, false)
        val distance = targetKg - startKg
        if (abs(distance) < 0.05) return GoalProgress(currentKg, targetKg, 1.0, true)
        val covered = (currentKg - startKg) / distance
        val done = if (distance > 0) currentKg >= targetKg else currentKg <= targetKg
        return GoalProgress(currentKg, targetKg, covered.coerceIn(0.0, 1.0), done)
    }

    /** Lifts and reps: your best so far against the target. */
    fun best(bestSoFar: Double?, target: Double): GoalProgress {
        if (bestSoFar == null || target <= 0) return GoalProgress(bestSoFar, target, 0.0, false)
        return GoalProgress(bestSoFar, target, (bestSoFar / target).coerceIn(0.0, 1.0), bestSoFar >= target)
    }
}

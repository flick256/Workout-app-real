package app.forge.domain.workout

import app.forge.domain.model.SetType

/** The minimum a calculation needs to know about one logged set. */
data class LoggedSet(
    val type: SetType,
    val weightKg: Double?,
    val reps: Int?,
    val durationSeconds: Int? = null,
    val completed: Boolean = true,
)

data class WorkoutSummary(
    val completedSets: Int,
    val totalReps: Int,
    /** Sum of weight × reps over completed work sets (warm-ups excluded), in kg. */
    val volumeKg: Double,
)

object WorkoutStats {
    fun summarize(sets: List<LoggedSet>): WorkoutSummary {
        val work = sets.filter { it.completed && it.type.countsAsWork }
        return WorkoutSummary(
            completedSets = work.size,
            totalReps = work.sumOf { it.reps ?: 0 },
            volumeKg = work.sumOf { (it.weightKg ?: 0.0) * (it.reps ?: 0) },
        )
    }

    /**
     * Lines up last time's sets with today's rows, the way Strong and Hevy do:
     * warm-ups match warm-ups in order, and every other row matches the next unused
     * work set from last time. Rows with nothing to compare against get null.
     */
    fun <T> matchPrevious(
        currentTypes: List<SetType>,
        previous: List<T>,
        typeOf: (T) -> SetType,
    ): List<T?> {
        val prevWarmups = previous.filter { typeOf(it) == SetType.WARMUP }.iterator()
        val prevWork = previous.filter { typeOf(it) != SetType.WARMUP }.iterator()
        return currentTypes.map { type ->
            val source = if (type == SetType.WARMUP) prevWarmups else prevWork
            if (source.hasNext()) source.next() else null
        }
    }
}

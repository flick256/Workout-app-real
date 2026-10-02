package app.forge.domain.program

import java.time.DayOfWeek
import java.time.LocalDate

/** What the active program has in store for today. */
data class TodayPlan(
    /** Index of the routine that's up next. */
    val routineIndex: Int,
    /** False on a rest day of a fixed-days program. */
    val isTrainingDay: Boolean,
    /** You already finished a workout from this program today. */
    val doneToday: Boolean,
    /** Next scheduled day if today is a rest day or already done; null for flexible programs. */
    val nextTrainingDate: LocalDate?,
)

/**
 * Routines rotate in order (A → B → A…, or Push → Pull → Legs…). Missing a day doesn't
 * skip a routine: the next one in line simply waits until you train.
 */
object ProgramSchedule {

    fun nextRoutineIndex(routineCount: Int, lastCompletedIndex: Int?): Int {
        if (routineCount <= 0) return 0
        return if (lastCompletedIndex == null || lastCompletedIndex !in 0 until routineCount) 0
        else (lastCompletedIndex + 1) % routineCount
    }

    fun isTrainingDay(days: Set<DayOfWeek>, date: LocalDate): Boolean = days.isEmpty() || date.dayOfWeek in days

    /** The first training day strictly after [from], or null if any day is fine. */
    fun nextTrainingDay(days: Set<DayOfWeek>, from: LocalDate): LocalDate? {
        if (days.isEmpty()) return null
        return (1..7).map { from.plusDays(it.toLong()) }.first { it.dayOfWeek in days }
    }

    fun plan(
        routineCount: Int,
        lastCompletedIndex: Int?,
        lastCompletedDate: LocalDate?,
        days: Set<DayOfWeek>,
        today: LocalDate,
    ): TodayPlan {
        val doneToday = lastCompletedDate == today
        val trainingDay = isTrainingDay(days, today)
        return TodayPlan(
            routineIndex = nextRoutineIndex(routineCount, lastCompletedIndex),
            isTrainingDay = trainingDay,
            doneToday = doneToday,
            nextTrainingDate = if (!trainingDay || doneToday) nextTrainingDay(days, today) else null,
        )
    }
}

/** Rough workout length so you can pick something that fits the time you have. */
object WorkoutEstimate {
    /** Average time under load per set, including setting up. */
    const val SECONDS_PER_SET = 40

    data class Slot(val sets: Int, val restSeconds: Int, val supersetGroup: Int?, val timedSeconds: Int? = null)

    /**
     * Sum of set time plus rests. In a superset the paired sets run back to back and the
     * rest comes once per round (taken from the group's last exercise).
     */
    fun minutes(slots: List<Slot>): Int {
        var seconds = 0
        var i = 0
        while (i < slots.size) {
            val group = slots[i].supersetGroup
            val members = if (group == null) listOf(slots[i])
            else slots.drop(i).takeWhile { it.supersetGroup == group }
            val rounds = members.maxOf { it.sets }
            val work = members.sumOf { m -> m.sets * (m.timedSeconds ?: SECONDS_PER_SET) }
            seconds += work + rounds * members.last().restSeconds
            i += members.size
        }
        return (seconds + 59) / 60
    }
}

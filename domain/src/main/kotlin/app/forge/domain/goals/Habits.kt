package app.forge.domain.goals

import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Daily habits. Some tick themselves from data Forge already has, so you only tap the
 * ones it can't know about (stretching, creatine, water...).
 */
enum class HabitKind(val label: String, val auto: Boolean, val defaultTarget: Double?, val unit: String?) {
    CUSTOM("Custom habit", false, null, null),
    TRAIN("Train or do an activity", true, null, null),
    PROTEIN("Hit my protein target", true, null, null),
    LOG_FOOD("Log what I eat", true, null, null),
    STEPS("Steps", true, 8_000.0, "steps"),
    SLEEP("Sleep", true, 8.0, "h"),
}

/** Which days a habit is due, as a bit mask (Monday = bit 0). */
@JvmInline
value class DayMask(val bits: Int) {
    fun isDue(day: DayOfWeek): Boolean = bits and (1 shl (day.value - 1)) != 0
    fun isDue(date: LocalDate): Boolean = isDue(date.dayOfWeek)
    fun toggle(day: DayOfWeek) = DayMask(bits xor (1 shl (day.value - 1)))
    val count: Int get() = Integer.bitCount(bits and ALL.bits)

    companion object {
        val ALL = DayMask(0b111_1111)
        val WEEKDAYS = DayMask(0b001_1111)
    }
}

object HabitMath {

    /**
     * Days in a row done, counting only days the habit is due. Today counts if it's done;
     * if not (yet), the streak still stands from yesterday, so it doesn't look broken at
     * breakfast time.
     */
    fun currentStreak(done: Set<LocalDate>, today: LocalDate, due: DayMask = DayMask.ALL): Int {
        if (due.count == 0) return 0
        var day = if (today in done || !due.isDue(today)) today else today.minusDays(1)
        var streak = 0
        var guard = 0
        while (guard++ < MAX_DAYS) {
            if (due.isDue(day)) {
                if (day in done) streak++ else break
            }
            day = day.minusDays(1)
        }
        return streak
    }

    /** Longest run ever, counting only due days. */
    fun bestStreak(done: Set<LocalDate>, due: DayMask = DayMask.ALL): Int {
        if (done.isEmpty() || due.count == 0) return 0
        var best = 0
        var run = 0
        var day = done.min()
        val last = done.max()
        while (!day.isAfter(last)) {
            if (due.isDue(day)) {
                if (day in done) { run++; if (run > best) best = run } else run = 0
            }
            day = day.plusDays(1)
        }
        return best
    }

    /** Share of due days in [from, to] that were done. Null when none were due. */
    fun completion(done: Set<LocalDate>, from: LocalDate, to: LocalDate, due: DayMask = DayMask.ALL): Double? {
        var dueDays = 0
        var hit = 0
        var day = from
        while (!day.isAfter(to)) {
            if (due.isDue(day)) {
                dueDays++
                if (day in done) hit++
            }
            day = day.plusDays(1)
        }
        return if (dueDays == 0) null else hit.toDouble() / dueDays
    }

    private const val MAX_DAYS = 3_660
}

/** What Forge knows about one day, for habits that tick themselves. */
data class DayFacts(
    val trained: Boolean = false,
    val proteinG: Double? = null,
    val proteinTargetG: Int? = null,
    val foodEntries: Int = 0,
    val steps: Long? = null,
    val sleepMinutes: Int? = null,
)

object AutoHabits {
    /** Null when Forge can't tell (e.g. no step data that day): shown as "no data", not "missed". */
    fun isDone(kind: HabitKind, target: Double?, facts: DayFacts): Boolean? = when (kind) {
        HabitKind.CUSTOM -> null
        HabitKind.TRAIN -> facts.trained
        HabitKind.PROTEIN -> {
            val goal = facts.proteinTargetG
            if (goal == null || facts.proteinG == null) null else facts.proteinG >= goal * 0.95
        }
        HabitKind.LOG_FOOD -> facts.foodEntries >= 2
        HabitKind.STEPS -> facts.steps?.let { it >= (target ?: HabitKind.STEPS.defaultTarget!!) }
        HabitKind.SLEEP -> facts.sleepMinutes?.let { it >= (target ?: HabitKind.SLEEP.defaultTarget!!) * 60 }
    }
}

package app.forge.domain.analytics

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/**
 * One day in the training calendar. [level] 0 = nothing, 1–4 = more volume. A day with
 * only sports or cardio ([activities]) shows at level 1.
 */
data class HeatCell(val date: LocalDate, val workouts: Int, val volumeKg: Double, val level: Int, val activities: Int = 0) {
    val trained: Boolean get() = workouts > 0 || activities > 0
}

object Activity {

    /**
     * Weeks in a row (ending this week, or last week if this one has nothing yet) with
     * training. Pass workout and sport/cardio dates together so both count.
     */
    fun weekStreak(dates: Collection<LocalDate>, today: LocalDate): Int {
        val weeks = dates.map { it.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) }.toSet()
        var week = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        if (week !in weeks) week = week.minusWeeks(1)
        var count = 0
        while (week in weeks) {
            count++
            week = week.minusWeeks(1)
        }
        return count
    }

    /**
     * The calendar grid: [weeks] columns of Monday–Sunday ending this week. Levels are
     * quartiles of your own training days, so the shading adapts to how you train.
     */
    fun heatmap(
        perDay: Map<LocalDate, Pair<Int, Double>>,
        today: LocalDate,
        weeks: Int,
        activitiesPerDay: Map<LocalDate, Int> = emptyMap(),
    ): List<HeatCell> {
        val end = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
        val start = end.minusWeeks(weeks.toLong()).plusDays(1)
        val volumes = perDay.filterKeys { it in start..end }.values.map { it.second }.filter { it > 0 }.sorted()
        fun quantile(q: Double) = if (volumes.isEmpty()) 0.0 else volumes[((volumes.size - 1) * q).toInt()]
        val cuts = listOf(quantile(0.25), quantile(0.5), quantile(0.75))
        return generateSequence(start) { it.plusDays(1) }.takeWhile { !it.isAfter(end) }.map { date ->
            val (count, volume) = perDay[date] ?: (0 to 0.0)
            val activities = activitiesPerDay[date] ?: 0
            val level = when {
                count == 0 -> if (activities > 0) 1 else 0
                volume <= cuts[0] -> 1
                volume <= cuts[1] -> 2
                volume <= cuts[2] -> 3
                else -> 4
            }
            HeatCell(date, count, volume, level, activities)
        }.toList()
    }
}

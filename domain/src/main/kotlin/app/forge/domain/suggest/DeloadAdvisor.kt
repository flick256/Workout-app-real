package app.forge.domain.suggest

import java.time.LocalDate
import java.time.temporal.IsoFields

/** One finished workout: when, and its average RPE (if you recorded any). */
data class SessionPoint(val date: LocalDate, val avgRpe: Double?)

/** An exercise's best estimated 1RM per session, newest first. */
data class ExerciseTrend(val name: String, val bestE1rm: List<Double>)

data class DeloadInput(
    val sessions: List<SessionPoint>,
    val trends: List<ExerciseTrend>,
    val today: LocalDate,
    /** You dismissed the hint until this date. */
    val dismissedUntil: LocalDate? = null,
)

data class DeloadHint(val reasons: List<String>, val advice: String)

/**
 * Suggests a lighter week when the signs add up:
 *  - 6+ weeks in a row of regular training (2+ workouts a week) with no break;
 *  - several lifts not improving over their last 3 sessions;
 *  - sets feeling harder (average RPE up) than two weeks before.
 * A long streak is enough on its own; otherwise it takes stalls plus rising effort,
 * or 3+ stalled lifts.
 */
object DeloadAdvisor {
    const val STREAK_WEEKS = 6
    const val ADVICE = "Take a lighter week: keep your weights, do about half the sets, and stop each " +
        "set with 3+ reps in the tank. Then get back to normal. You'll usually come back stronger."

    fun check(input: DeloadInput): DeloadHint? {
        if (input.dismissedUntil != null && !input.today.isAfter(input.dismissedUntil)) return null
        val reasons = mutableListOf<String>()

        val streak = weeksInARow(input.sessions.map { it.date }, input.today)
        val longStreak = streak >= STREAK_WEEKS
        if (longStreak) reasons += "$streak weeks of steady training without a lighter week."

        val stalled = input.trends.filter(::isStalled).map { it.name }
        if (stalled.isNotEmpty()) reasons += "${stalled.take(3).joinToString()} " +
            "${if (stalled.size == 1) "hasn't" else "haven't"} improved over the last 3 sessions."

        val creep = rpeCreep(input.sessions, input.today)
        if (creep != null) reasons += "Sets feel harder: average RPE ${fmt(creep.second)}, up from ${fmt(creep.first)}."

        val show = longStreak || (stalled.isNotEmpty() && creep != null) || stalled.size >= 3
        return if (show) DeloadHint(reasons, ADVICE) else null
    }

    /** Consecutive ISO weeks with 2+ workouts, counting back from this week (or last, if this one's young). */
    fun weeksInARow(dates: List<LocalDate>, today: LocalDate): Int {
        val perWeek = dates.groupingBy { weekKey(it) }.eachCount()
        var week = today
        if ((perWeek[weekKey(week)] ?: 0) < 2) week = week.minusWeeks(1)
        var count = 0
        while ((perWeek[weekKey(week)] ?: 0) >= 2) {
            count++
            week = week.minusWeeks(1)
        }
        return count
    }

    /** Best e1RM of the last 3 sessions isn't above the best of the 3 before. */
    fun isStalled(trend: ExerciseTrend): Boolean {
        if (trend.bestE1rm.size < 6) return false
        val recent = trend.bestE1rm.take(3).max()
        val before = trend.bestE1rm.drop(3).take(3).max()
        return recent <= before * 1.005
    }

    /** (previous 2 weeks, last 2 weeks) average RPE when it's high and clearly rising. */
    fun rpeCreep(sessions: List<SessionPoint>, today: LocalDate): Pair<Double, Double>? {
        val recent = sessions.filter { it.avgRpe != null && it.date.isAfter(today.minusDays(14)) }.mapNotNull { it.avgRpe }
        val before = sessions.filter {
            it.avgRpe != null && !it.date.isAfter(today.minusDays(14)) && it.date.isAfter(today.minusDays(28))
        }.mapNotNull { it.avgRpe }
        if (recent.size < 2 || before.size < 2) return null
        val now = recent.average()
        val then = before.average()
        return if (now >= 8.5 && now - then >= 0.5) then to now else null
    }

    private fun weekKey(d: LocalDate) = d.get(IsoFields.WEEK_BASED_YEAR) * 100 + d.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)

    private fun fmt(v: Double) = "%.1f".format(v)
}

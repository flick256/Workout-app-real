package app.forge.domain.insights

import kotlin.math.roundToInt

/** One workout's best effort on an exercise. */
data class LiftSession(
    val epochDay: Long,
    val bestE1rm: Double,
    /** Average RPE of the work sets, if you logged it. */
    val avgRpe: Double? = null,
)

/** Everything that might explain a plateau, all computed by Forge (not guessed). */
data class StallInput(
    val exerciseName: String,
    /** Oldest first. */
    val sessions: List<LiftSession>,
    val todayEpochDay: Long,
    /** Hard sets for the exercise's main muscle in the last 7 days, and the target. */
    val muscleName: String? = null,
    val weeklySets: Double? = null,
    val weeklyTarget: Int? = null,
    val avgSleepHours: Double? = null,
    /** Average protein on days you logged food (last 14 days), and your target. */
    val avgProteinG: Double? = null,
    val proteinTargetG: Int? = null,
)

enum class FindingKind { NOT_ENOUGH_DATA, PROGRESSING, FLAT, DROPPING, EFFORT_RISING, LOW_VOLUME, HIGH_VOLUME, INFREQUENT, LOW_SLEEP, LOW_PROTEIN }

data class Finding(val kind: FindingKind, val text: String, val tip: String? = null)

data class StallReport(val stalled: Boolean, val findings: List<Finding>) {
    /** Plain summary without any AI: the findings and tips as sentences. */
    fun asText(): String = findings.joinToString(" ") { f -> f.text + (f.tip?.let { " $it" } ?: "") }
}

/**
 * "Why has my bench stalled?" answered from your own logs with simple, explainable rules.
 * The AI (if you've turned it on) only rewords this; it never adds reasons of its own.
 */
object StallAnalyzer {
    private const val WINDOW_DAYS = 42L

    fun analyze(input: StallInput): StallReport {
        val recent = input.sessions.filter { it.epochDay > input.todayEpochDay - WINDOW_DAYS }
        if (recent.size < 4) {
            return StallReport(
                false,
                listOf(Finding(FindingKind.NOT_ENOUGH_DATA, "Only ${recent.size} ${input.exerciseName} workout(s) in the last 6 weeks, so it's too early to call a plateau.", "Log at least 4 to get an analysis.")),
            )
        }
        val findings = mutableListOf<Finding>()
        val firstHalf = recent.take(recent.size / 2).map { it.bestE1rm }
        val secondHalf = recent.drop(recent.size / 2).map { it.bestE1rm }
        val before = firstHalf.max()
        val after = secondHalf.max()
        val change = (after - before) / before * 100
        val stalled = change < 1.0
        when {
            change >= 1.0 -> findings += Finding(
                FindingKind.PROGRESSING,
                "Your estimated 1RM went from ${r(before)} to ${r(after)} kg (+${r1(change)}%) over the last ${recent.size} workouts: still progressing.",
            )
            change > -3.0 -> findings += Finding(
                FindingKind.FLAT,
                "Your estimated 1RM has been flat at about ${r(after)} kg across the last ${recent.size} workouts.",
            )
            else -> findings += Finding(
                FindingKind.DROPPING,
                "Your estimated 1RM dropped from ${r(before)} to ${r(after)} kg (${r1(change)}%).",
                "That usually means fatigue rather than lost strength: a lighter week often brings it straight back.",
            )
        }

        val rpes = recent.mapNotNull { it.avgRpe }
        if (rpes.size >= 4) {
            val early = rpes.take(rpes.size / 2).average()
            val late = rpes.drop(rpes.size / 2).average()
            if (late - early >= 0.75) findings += Finding(
                FindingKind.EFFORT_RISING,
                "Sets feel harder: average RPE rose from ${r1(early)} to ${r1(late)} for similar weights.",
                "Rising effort with flat strength is a classic sign you need a deload (a week at about 60–70% of your usual sets).",
            )
        }

        val weeks = (recent.last().epochDay - recent.first().epochDay + 1) / 7.0
        val perWeek = recent.size / maxOf(weeks, 1.0)
        if (stalled && perWeek < 1.0) findings += Finding(
            FindingKind.INFREQUENT,
            "You've trained it about ${r1(perWeek)} times a week.",
            "Twice a week tends to progress faster than once.",
        )

        if (input.weeklySets != null && input.weeklyTarget != null && input.muscleName != null) {
            if (stalled && input.weeklySets < input.weeklyTarget * 0.6) findings += Finding(
                FindingKind.LOW_VOLUME,
                "${input.muscleName} got ${r(input.weeklySets)} hard sets this week, under the ~${input.weeklyTarget} that most people grow on.",
                "Try adding 2–3 sets a week for it.",
            )
            if (stalled && input.weeklySets > input.weeklyTarget * 2) findings += Finding(
                FindingKind.HIGH_VOLUME,
                "${input.muscleName} got ${r(input.weeklySets)} hard sets this week, about twice the usual target.",
                "More isn't always better: trimming a few sets can help you recover and progress.",
            )
        }
        if (stalled && input.avgSleepHours != null && input.avgSleepHours < 7.0) findings += Finding(
            FindingKind.LOW_SLEEP,
            "You've averaged ${r1(input.avgSleepHours)} h of sleep.",
            "Teens need about 8–10 h; strength gains suffer most when sleep is short.",
        )
        if (stalled && input.avgProteinG != null && input.proteinTargetG != null && input.avgProteinG < input.proteinTargetG * 0.8) {
            findings += Finding(
                FindingKind.LOW_PROTEIN,
                "Protein has averaged ${r(input.avgProteinG)} g on logged days, below your ${input.proteinTargetG} g target.",
                "Getting closer to it supports muscle repair.",
            )
        }
        if (stalled && findings.size == 1) findings += Finding(
            FindingKind.FLAT,
            "Nothing obvious stands out in your sleep, food or volume.",
            "Try a new rep range for 4–6 weeks (e.g. 5–8 instead of 8–12), or a small step back in weight and build up again.",
        )
        return StallReport(stalled, findings)
    }

    private fun r(v: Double) = v.roundToInt().toString()
    private fun r1(v: Double) = ((v * 10).roundToInt() / 10.0).toString()
}

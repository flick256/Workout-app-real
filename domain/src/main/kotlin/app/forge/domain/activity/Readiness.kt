package app.forge.domain.activity

import kotlin.math.roundToInt

/** Last night and today's signals from a watch or strap, with your recent baselines. */
data class ReadinessInput(
    val sleepMinutes: Int? = null,
    /** Average heart-rate variability (RMSSD, ms) today and over the previous weeks. */
    val hrvMs: Double? = null,
    val hrvBaselineMs: Double? = null,
    val restingHr: Double? = null,
    val restingHrBaseline: Double? = null,
)

enum class ReadinessLevel(val label: String) {
    GOOD("Good to go"),
    OK("Normal"),
    LOW("Take it easier"),
    UNKNOWN("No data"),
}

data class Readiness(val level: ReadinessLevel, val reasons: List<String>) {
    val advice: String
        get() = when (level) {
            ReadinessLevel.LOW -> "Your body's signals say you're not fully recovered. Train lighter today: fewer sets, " +
                "stop 2–3 reps short of failure, or swap in mobility. Quick workouts use 2 sets instead of 3."
            ReadinessLevel.OK -> "Train as planned and listen to how the first sets feel."
            ReadinessLevel.GOOD -> "Well recovered: a good day to push for progress."
            ReadinessLevel.UNKNOWN -> ""
        }
}

/**
 * A simple daily readiness check, compared against *your own* normal rather than
 * population averages, because HRV and resting heart rate vary a lot between people:
 *  - sleep under 6 h is a clear negative, under 7 h a mild one;
 *  - HRV more than 15% below your baseline is a clear negative, 7% a mild one, and
 *    5%+ above is a positive;
 *  - resting heart rate 5+ bpm above your baseline is a clear negative, 3+ a mild one.
 */
object ReadinessCheck {

    fun assess(input: ReadinessInput): Readiness {
        var score = 0
        var signals = 0
        val reasons = mutableListOf<String>()

        input.sleepMinutes?.let { sleep ->
            signals++
            val text = "Slept ${sleep / 60}h ${sleep % 60}m"
            when {
                sleep < 360 -> { score -= 2; reasons += "$text (short)" }
                sleep < 420 -> { score -= 1; reasons += "$text (a bit short)" }
                else -> reasons += text
            }
        }
        if (input.hrvMs != null && input.hrvBaselineMs != null && input.hrvBaselineMs > 0) {
            signals++
            val ratio = input.hrvMs / input.hrvBaselineMs
            val pct = ((ratio - 1) * 100).roundToInt()
            val text = "HRV ${input.hrvMs.roundToInt()} ms"
            when {
                ratio < 0.85 -> { score -= 2; reasons += "$text, ${-pct}% below your normal" }
                ratio < 0.93 -> { score -= 1; reasons += "$text, ${-pct}% below your normal" }
                ratio > 1.05 -> { score += 1; reasons += "$text, $pct% above your normal" }
                else -> reasons += "$text (normal for you)"
            }
        }
        if (input.restingHr != null && input.restingHrBaseline != null) {
            signals++
            val diff = input.restingHr - input.restingHrBaseline
            val text = "Resting HR ${input.restingHr.roundToInt()} bpm"
            when {
                diff >= 5 -> { score -= 2; reasons += "$text, ${diff.roundToInt()} above normal" }
                diff >= 3 -> { score -= 1; reasons += "$text, ${diff.roundToInt()} above normal" }
                else -> reasons += "$text (normal)"
            }
        }
        val level = when {
            signals == 0 -> ReadinessLevel.UNKNOWN
            score <= -3 -> ReadinessLevel.LOW
            score <= -1 -> ReadinessLevel.OK
            else -> ReadinessLevel.GOOD
        }
        return Readiness(level, reasons)
    }
}

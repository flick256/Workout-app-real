package app.forge.domain.suggest

import app.forge.domain.calc.Units
import app.forge.domain.model.Equipment
import app.forge.domain.model.LogType
import kotlin.math.floor

/** One completed work set (warm-ups excluded). For bodyweight moves, [weightKg] is added weight. */
data class WorkSet(val weightKg: Double?, val reps: Int?, val seconds: Int? = null, val rpe: Double? = null)

/** The work sets of one exercise in one past workout. */
data class PastSession(val sets: List<WorkSet>)

data class ProgressionInput(
    val logType: LogType,
    /** Newest first. */
    val history: List<PastSession>,
    /** Target range: reps, or seconds for timed exercises. Defaults apply if null. */
    val targetMin: Int? = null,
    val targetMax: Int? = null,
    /** Hardest effort that still counts as "earned" the top of the range. */
    val targetRpe: Double? = null,
    val equipment: Equipment? = null,
    /** Weights you own for this exercise (for bodyweight moves: your vest/bag weights). */
    val ownedWeights: List<Double> = emptyList(),
    /** Name of the next step in the exercise's progression ladder, if there is one. */
    val harderVariation: String? = null,
)

enum class SuggestionKind {
    FIRST_TIME,
    ADD_WEIGHT,
    ADD_REPS,
    ADD_TIME,
    HOLD,
    REDUCE,
    HARDER_VARIATION,
}

/**
 * What to aim for next time, and why. [weightKg]/[reps]/[seconds] are per-set targets
 * (null = keep what you'd normally enter).
 */
data class Suggestion(
    val kind: SuggestionKind,
    val weightKg: Double?,
    val reps: Int?,
    val seconds: Int?,
    val headline: String,
    val reason: String,
)

/**
 * Double progression, the method most strength programs use:
 *  1. Work within a rep range (e.g. 8–12).
 *  2. Add reps until every set reaches the top of the range at a sensible effort.
 *  3. Then add the smallest weight you can (or move to a harder variation) and start
 *     again at the bottom of the range.
 *  4. If you miss the bottom of the range twice in a row, drop the weight about 10%.
 */
object ProgressionEngine {
    const val DEFAULT_MIN_REPS = 8
    const val DEFAULT_MAX_REPS = 12
    const val DEFAULT_MIN_SECONDS = 30
    const val DEFAULT_MAX_SECONDS = 60
    const val DEFAULT_MAX_RPE = 9.0
    private const val TIME_STEP_SECONDS = 5

    fun suggest(input: ProgressionInput): Suggestion? {
        if (input.logType == LogType.DISTANCE_DURATION) return null
        val timed = input.logType == LogType.DURATION
        val min = input.targetMin ?: if (timed) DEFAULT_MIN_SECONDS else DEFAULT_MIN_REPS
        val max = (input.targetMax ?: if (timed) DEFAULT_MAX_SECONDS else DEFAULT_MAX_REPS).coerceAtLeast(min)
        val maxRpe = input.targetRpe ?: DEFAULT_MAX_RPE

        val sessions = input.history.map { s -> s.sets.filter { (if (timed) it.seconds else it.reps) != null } }
            .filter { it.isNotEmpty() }
        val last = sessions.firstOrNull() ?: return firstTime(timed, min, max)
        fun amount(set: WorkSet) = (if (timed) set.seconds else set.reps) ?: 0
        val unit = if (timed) "s" else " reps"

        val lastWeight = last.mapNotNull { it.weightKg }.maxOrNull()
        val lastAmounts = last.map(::amount)
        val allTop = lastAmounts.all { it >= max } && last.all { (it.rpe ?: 0.0) <= maxRpe }
        val missedBottom = lastAmounts.any { it < min }
        // Two misses only count if the earlier one was at the same (or a heavier) weight;
        // missing after a jump up is normal and just means "stay here".
        val missedTwice = missedBottom && sessions.getOrNull(1)?.let { prev ->
            prev.any { amount(it) < min } && (prev.mapNotNull { it.weightKg }.maxOrNull() ?: 0.0) >= (lastWeight ?: 0.0) - 0.01
        } == true
        val done = lastAmounts.joinToString(", ")
        val at = lastWeight?.takeIf { it > 0 }?.let { " at ${Units.format(it)} kg" }.orEmpty()

        return when {
            allTop -> earnedProgress(input, timed, min, max, lastWeight, last.size, at, unit)

            missedTwice -> {
                val lighter = lastWeight?.takeIf { it > 0 }?.let { lighterWeight(it, input.equipment, input.ownedWeights) }
                Suggestion(
                    kind = SuggestionKind.REDUCE,
                    weightKg = lighter,
                    reps = min.takeUnless { timed },
                    seconds = min.takeIf { timed },
                    headline = lighter?.let { "Drop to ${Units.format(it)} kg" } ?: "Ease off for a session",
                    reason = "You fell short of $min$unit twice in a row ($done$at). A slightly lighter " +
                        "load lets you build back up with good form." +
                        if (lighter == null) " Try an easier variation or fewer reps." else "",
                )
            }

            missedBottom -> Suggestion(
                kind = SuggestionKind.HOLD,
                weightKg = lastWeight,
                reps = min.takeUnless { timed },
                seconds = min.takeIf { timed },
                headline = "Same again, aim for $min$unit",
                reason = "Last time: $done$at. Stay here until every set reaches at least $min.",
            )

            else -> {
                // In the range: add a rep (or a few seconds) to each set.
                val target = ((lastAmounts.minOrNull() ?: min) + if (timed) TIME_STEP_SECONDS else 1).coerceAtMost(max)
                Suggestion(
                    kind = if (timed) SuggestionKind.ADD_TIME else SuggestionKind.ADD_REPS,
                    weightKg = lastWeight,
                    reps = target.takeUnless { timed },
                    seconds = target.takeIf { timed },
                    headline = "Aim for $target$unit per set",
                    reason = "Last time: $done$at. Add ${if (timed) "a few seconds" else "a rep"} per set; " +
                        "once every set hits $max, it's time to go heavier.",
                )
            }
        }
    }

    private fun earnedProgress(
        input: ProgressionInput,
        timed: Boolean,
        min: Int,
        max: Int,
        lastWeight: Double?,
        setCount: Int,
        at: String,
        unit: String,
    ): Suggestion {
        val earned = "You hit $max$unit on all $setCount sets$at last time."
        val bodyweight = input.logType == LogType.REPS || timed
        if (bodyweight && input.harderVariation != null) {
            return Suggestion(
                kind = SuggestionKind.HARDER_VARIATION,
                weightKg = null,
                reps = min.takeUnless { timed },
                seconds = min.takeIf { timed },
                headline = "Ready for ${input.harderVariation}",
                reason = "$earned Move up the progression (⋮ → Harder variation) and start again at $min$unit.",
            )
        }
        val heavier = if (bodyweight) {
            // Add a vest/bag: the lightest one heavier than what you used.
            input.ownedWeights.sorted().firstOrNull { it > (lastWeight ?: 0.0) + 0.01 }
        } else {
            nextWeight(lastWeight ?: 0.0, input.equipment, input.ownedWeights)
        }
        if (heavier != null && !timed) {
            return Suggestion(
                kind = SuggestionKind.ADD_WEIGHT,
                weightKg = heavier,
                reps = min,
                seconds = null,
                headline = "Go up to ${Units.format(heavier)} kg" + if (bodyweight) " added" else "",
                reason = "$earned Add weight and start again at $min reps.",
            )
        }
        // Nothing heavier available: keep progressing with volume.
        val target = max + if (timed) TIME_STEP_SECONDS * 2 else 2
        return Suggestion(
            kind = if (timed) SuggestionKind.ADD_TIME else SuggestionKind.ADD_REPS,
            weightKg = lastWeight,
            reps = target.takeUnless { timed },
            seconds = target.takeIf { timed },
            headline = "Push past the range: $target$unit",
            reason = "$earned " + if (input.ownedWeights.isNotEmpty() && !bodyweight) {
                "That's your heaviest weight, so add reps, slow the lowering to 3 seconds, or add a set."
            } else {
                "No heavier option yet, so add reps, slow down, or add a set."
            },
        )
    }

    private fun firstTime(timed: Boolean, min: Int, max: Int) = Suggestion(
        kind = SuggestionKind.FIRST_TIME,
        weightKg = null,
        reps = min.takeUnless { timed },
        seconds = min.takeIf { timed },
        headline = "First time: find your level",
        reason = "Pick a load where you can do $min–$max${if (timed) " seconds" else " reps"} with about " +
            "2–3 left in the tank (RPE 7–8). Forge will take it from there.",
    )

    /** Smallest sensible jump for each kind of equipment, used when you haven't entered owned weights. */
    fun stepFor(equipment: Equipment?): Double = when (equipment) {
        Equipment.DUMBBELL -> 2.0
        Equipment.KETTLEBELL -> 4.0
        Equipment.MACHINE, Equipment.CABLE -> 5.0
        else -> 2.5
    }

    /** The next heavier load: your next owned weight, or the next step up on the grid. */
    fun nextWeight(current: Double, equipment: Equipment?, owned: List<Double>): Double? {
        if (owned.isNotEmpty()) return owned.sorted().firstOrNull { it > current + 0.01 }
        val step = stepFor(equipment)
        return (floor(current / step + 1e-9) + 1) * step
    }

    /** About 10% lighter: the heaviest owned weight at or below that, else the step grid. */
    fun lighterWeight(current: Double, equipment: Equipment?, owned: List<Double>): Double? {
        val target = current * 0.9
        if (owned.isNotEmpty()) {
            return owned.sorted().lastOrNull { it <= target + 0.01 } ?: owned.sorted().lastOrNull { it < current - 0.01 }
        }
        val step = stepFor(equipment)
        val snapped = floor(target / step + 1e-9) * step
        return snapped.takeIf { it > 0 && it < current } ?: (current - step).takeIf { it > 0 }
    }
}

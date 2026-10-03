package app.forge.domain.suggest

import app.forge.domain.model.Muscle
import app.forge.domain.program.WorkoutEstimate
import kotlin.math.roundToInt

/** A routine you could do, described by the sets it gives each muscle. */
data class RoutineOption(
    val id: String,
    val name: String,
    val minutes: Int,
    /** Planned sets per muscle (primary 1, secondary 0.5 per set). */
    val muscleSets: Map<Muscle, Double>,
    val isPlanned: Boolean = false,
)

data class RoutineChoice(val option: RoutineOption, val score: Double, val reason: String)

/** One slot of a generated quick workout; the app picks an exercise for [muscle]. */
data class QuickSlot(val muscle: Muscle, val sets: Int, val restSeconds: Int, val supersetGroup: Int?)

data class QuickPlan(val slots: List<QuickSlot>, val minutes: Int, val reason: String)

/**
 * "What should I train today?" Scores options by how recovered and how under-trained
 * this week their muscles are, and only offers things that fit the time you have.
 */
object TrainToday {

    /** Weighted average priority of the muscles a routine works (0 = pointless today). */
    fun score(option: RoutineOption, status: Map<Muscle, MuscleStatus>): Double {
        val total = option.muscleSets.values.sum()
        if (total <= 0) return 0.0
        return option.muscleSets.entries.sumOf { (m, sets) -> sets * (status[m]?.priority ?: 0.5) } / total
    }

    /**
     * Best routine for today that fits [minutes] (with 5 minutes' grace). Your planned
     * routine wins unless something else is clearly better (25%+) for your recovery.
     */
    fun bestRoutine(options: List<RoutineOption>, statuses: List<MuscleStatus>, minutes: Int): RoutineChoice? {
        val status = statuses.associateBy { it.muscle }
        val fitting = options.filter { it.muscleSets.isNotEmpty() && it.minutes <= minutes + 5 }
        if (fitting.isEmpty()) return null
        val scored = fitting.map { it to score(it, status) }
        val best = scored.maxBy { it.second }
        val planned = scored.firstOrNull { it.first.isPlanned }
        val pick = if (planned != null && planned.second >= best.second * 0.8) planned else best
        return RoutineChoice(pick.first, pick.second, explain(pick.first, status, planned?.first?.takeIf { it != pick.first }))
    }

    private fun explain(option: RoutineOption, status: Map<Muscle, MuscleStatus>, skippedPlan: RoutineOption?): String {
        val main = option.muscleSets.entries.sortedByDescending { it.value }.take(3).mapNotNull { status[it.key] }
        val parts = main.joinToString("; ") {
            "${it.muscle.label} ${(it.readiness * 100).roundToInt()}% recovered, " +
                "${it.weeklySets.roundToInt()}/${it.weeklyTarget} sets this week"
        }
        val prefix = when {
            option.isPlanned -> "It's next in your program and your muscles are ready. "
            skippedPlan != null -> "Better than your planned ${skippedPlan.name} today: those muscles are still recovering. "
            else -> ""
        }
        return "$prefix$parts."
    }

    /**
     * A quick workout for the muscles that most need training now. Short sessions pair
     * exercises as supersets; longer ones cover more muscles. Very fatigued muscles
     * (below 50% recovered) are left out.
     */
    fun quickPlan(statuses: List<MuscleStatus>, minutes: Int): QuickPlan? {
        val ready = statuses.filter { it.readiness >= 0.5 && it.muscle in TRAINABLE }
            .sortedByDescending { it.priority }
        if (ready.isEmpty()) return null
        val (count, rest, superset) = when {
            minutes <= 20 -> Triple(4, 60, true)
            minutes <= 35 -> Triple(4, 75, false)
            else -> Triple(6, 90, false)
        }
        var picks = ready.take(count).map { it.muscle }
        fun build(muscles: List<Muscle>) = muscles.mapIndexed { i, m ->
            QuickSlot(m, sets = 3, restSeconds = rest, supersetGroup = if (superset) i / 2 + 1 else null)
        }.let { slots ->
            // A superset needs a partner; a lone last exercise is done on its own.
            if (superset && slots.size % 2 == 1) slots.dropLast(1) + slots.last().copy(supersetGroup = null) else slots
        }
        fun estimate(slots: List<QuickSlot>) =
            WorkoutEstimate.minutes(slots.map { WorkoutEstimate.Slot(it.sets, it.restSeconds, it.supersetGroup) })
        var slots = build(picks)
        while (slots.size > 1 && estimate(slots) > minutes + 3) {
            picks = picks.dropLast(1)
            slots = build(picks)
        }
        val reason = "Focus: " + picks.joinToString { m ->
            val s = statuses.first { it.muscle == m }
            "${m.label} (${s.weeklySets.roundToInt()}/${s.weeklyTarget} sets this week)"
        } + ". The most recovered and least trained right now."
        return QuickPlan(slots, estimate(slots), reason)
    }

    /**
     * Go-to home exercises per muscle (library source IDs), best first. Used for quick
     * workouts when you haven't trained that muscle with something else before.
     */
    val DEFAULT_EXERCISES: Map<Muscle, List<String>> = mapOf(
        Muscle.CHEST to listOf("Pushups", "forge:incline_push_up_low", "Dumbbell_Floor_Press"),
        Muscle.LATS to listOf("forge:table_row", "One-Arm_Dumbbell_Row", "forge:doorway_row"),
        Muscle.MIDDLE_BACK to listOf("forge:bag_bent_over_row", "Bent_Over_Two-Dumbbell_Row", "forge:doorway_row"),
        Muscle.SHOULDERS to listOf("forge:pike_push_up", "Dumbbell_Shoulder_Press", "forge:bag_shoulder_press"),
        Muscle.QUADRICEPS to listOf("forge:bag_bear_hug_squat", "Bodyweight_Squat", "forge:split_squat"),
        Muscle.HAMSTRINGS to listOf("forge:bag_romanian_deadlift", "Stiff-Legged_Dumbbell_Deadlift", "forge:single_leg_rdl"),
        Muscle.GLUTES to listOf("forge:bag_hip_thrust", "Butt_Lift_Bridge", "forge:split_squat"),
        Muscle.BICEPS to listOf("Dumbbell_Bicep_Curl", "Hammer_Curls", "forge:doorway_row"),
        Muscle.TRICEPS to listOf("Bench_Dips", "forge:diamond_push_up", "Tricep_Dumbbell_Kickback"),
        Muscle.ABDOMINALS to listOf("Dead_Bug", "Plank", "forge:hollow_body_hold"),
        Muscle.CALVES to listOf("forge:calf_raise", "forge:single_leg_calf_raise"),
    )

    /** Muscles worth building a workout around (not tiny stabilisers). */
    val TRAINABLE = setOf(
        Muscle.CHEST, Muscle.LATS, Muscle.MIDDLE_BACK, Muscle.SHOULDERS, Muscle.QUADRICEPS,
        Muscle.HAMSTRINGS, Muscle.GLUTES, Muscle.BICEPS, Muscle.TRICEPS, Muscle.ABDOMINALS, Muscle.CALVES,
    )
}

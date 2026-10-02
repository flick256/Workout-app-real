package app.forge.domain.program

import app.forge.domain.model.Equipment
import java.time.DayOfWeek
import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.WEDNESDAY

/**
 * One exercise in a routine template. [targetMin]..[targetMax] are reps, or seconds for
 * timed exercises (plank 30–45 s). Exercises with the same [supersetGroup] alternate.
 */
data class SlotTemplate(
    val sourceId: String,
    val sets: Int,
    val targetMin: Int?,
    val targetMax: Int?,
    val restSeconds: Int,
    val supersetGroup: Int? = null,
)

data class RoutineTemplate(val name: String, val notes: String?, val slots: List<SlotTemplate>)

/** A ready-made program: routines that rotate in order on your training days. */
data class ProgramTemplate(
    val key: String,
    val name: String,
    val summary: String,
    val level: String,
    /** Suggested training days; empty = train whenever you can. */
    val trainingDays: Set<DayOfWeek>,
    val minutes: Int,
    val equipment: Set<Equipment>,
    val routines: List<RoutineTemplate>,
)

/** Forge's prebuilt home programs. They're copied into your routines, so you can edit them. */
object ProgramTemplates {

    private fun s(id: String, sets: Int, min: Int?, max: Int?, rest: Int, group: Int? = null) =
        SlotTemplate(id, sets, min, max, rest, group)

    private const val PROGRESS_NOTE =
        "When you hit the top of the range on every set, add weight or switch to a harder " +
            "variation (exercise ⋮ → Harder variation)."

    val all: List<ProgramTemplate> = listOf(
        ProgramTemplate(
            key = "full_body_home",
            name = "Full Body Home",
            summary = "Two full-body workouts alternating three days a week. The best place to " +
                "start: every muscle twice a week in about 30 minutes.",
            level = "Beginner",
            trainingDays = setOf(MONDAY, WEDNESDAY, FRIDAY),
            minutes = 30,
            equipment = setOf(Equipment.BODY_ONLY, Equipment.WEIGHTED_BAG),
            routines = listOf(
                RoutineTemplate(
                    "Full Body A", PROGRESS_NOTE,
                    listOf(
                        s("forge:bag_bear_hug_squat", 3, 8, 12, 90),
                        s("Pushups", 3, 8, 15, 90),
                        s("forge:table_row", 3, 8, 12, 90),
                        s("forge:bag_romanian_deadlift", 3, 8, 12, 90),
                        s("Plank", 3, 30, 45, 60),
                    ),
                ),
                RoutineTemplate(
                    "Full Body B", PROGRESS_NOTE,
                    listOf(
                        s("forge:split_squat", 3, 8, 12, 75),
                        s("forge:pike_push_up", 3, 6, 10, 90),
                        s("forge:doorway_row", 3, 10, 15, 75),
                        s("Butt_Lift_Bridge", 3, 12, 20, 60),
                        s("Dead_Bug", 3, 8, 12, 60),
                    ),
                ),
            ),
        ),
        ProgramTemplate(
            key = "home_ppl",
            name = "Home Push / Pull / Legs",
            summary = "Push, pull and leg days in rotation. Train 3–6 days a week; whatever " +
                "day you train, the next one in line is ready.",
            level = "Intermediate",
            trainingDays = emptySet(),
            minutes = 40,
            equipment = setOf(Equipment.BODY_ONLY, Equipment.DUMBBELL, Equipment.WEIGHTED_BAG, Equipment.BANDS),
            routines = listOf(
                RoutineTemplate(
                    "Push", PROGRESS_NOTE,
                    listOf(
                        s("Pushups", 4, 8, 15, 90),
                        s("Dumbbell_Shoulder_Press", 3, 8, 12, 90),
                        s("forge:pike_push_up", 3, 6, 10, 90),
                        s("Bench_Dips", 3, 8, 15, 75),
                        s("Side_Lateral_Raise", 3, 12, 15, 60, group = 1),
                        s("Tricep_Dumbbell_Kickback", 3, 10, 15, 60, group = 1),
                    ),
                ),
                RoutineTemplate(
                    "Pull", PROGRESS_NOTE,
                    listOf(
                        s("forge:table_row", 4, 8, 12, 90),
                        s("One-Arm_Dumbbell_Row", 3, 8, 12, 75),
                        s("forge:bag_bent_over_row", 3, 8, 12, 90),
                        s("Dumbbell_Bicep_Curl", 3, 10, 12, 60, group = 1),
                        s("Hammer_Curls", 3, 10, 12, 60, group = 1),
                        s("Band_Pull_Apart", 2, 15, 20, 45),
                    ),
                ),
                RoutineTemplate(
                    "Legs", PROGRESS_NOTE,
                    listOf(
                        s("forge:bag_bear_hug_squat", 4, 8, 12, 120),
                        s("forge:bulgarian_split_squat", 3, 8, 12, 90),
                        s("Stiff-Legged_Dumbbell_Deadlift", 3, 8, 12, 90),
                        s("forge:calf_raise", 3, 12, 20, 60),
                        s("Dead_Bug", 3, 8, 12, 45),
                    ),
                ),
            ),
        ),
        ProgramTemplate(
            key = "calisthenics_foundations",
            name = "Calisthenics Foundations",
            summary = "Bodyweight only. Builds the base for harder skills using the " +
                "progression ladders: start each move at a level where you get the reps.",
            level = "Beginner",
            trainingDays = setOf(MONDAY, WEDNESDAY, FRIDAY),
            minutes = 40,
            equipment = setOf(Equipment.BODY_ONLY),
            routines = listOf(
                RoutineTemplate(
                    "Foundations A", PROGRESS_NOTE,
                    listOf(
                        s("Pushups", 3, 5, 12, 120),
                        s("forge:pike_push_up", 3, 5, 10, 120),
                        s("forge:split_squat", 3, 6, 12, 90),
                        s("forge:hollow_body_hold", 3, 20, 40, 60),
                        s("forge:calf_raise", 2, 12, 20, 45),
                    ),
                ),
                RoutineTemplate(
                    "Foundations B", PROGRESS_NOTE,
                    listOf(
                        s("forge:table_row", 3, 5, 12, 120),
                        s("Bench_Dips", 3, 6, 12, 90),
                        s("Bodyweight_Squat", 3, 10, 20, 90),
                        s("Butt_Lift_Bridge", 3, 10, 20, 60),
                        s("Plank", 3, 30, 60, 60),
                    ),
                ),
            ),
        ),
        ProgramTemplate(
            key = "express_15",
            name = "Express 15",
            summary = "For busy days: two supersets and a plank, no equipment, about " +
                "15 minutes. Better than skipping.",
            level = "Any",
            trainingDays = emptySet(),
            minutes = 15,
            equipment = setOf(Equipment.BODY_ONLY),
            routines = listOf(
                RoutineTemplate(
                    "Express 15", "Alternate the paired exercises, resting after each pair.",
                    listOf(
                        s("Bodyweight_Squat", 3, 15, 20, 60, group = 1),
                        s("Pushups", 3, 8, 15, 60, group = 1),
                        s("forge:table_row", 3, 8, 12, 60, group = 2),
                        s("forge:split_squat", 3, 8, 10, 60, group = 2),
                        s("Plank", 2, 30, 45, 30),
                    ),
                ),
            ),
        ),
    )

    fun byKey(key: String?): ProgramTemplate? = all.firstOrNull { it.key == key }
}

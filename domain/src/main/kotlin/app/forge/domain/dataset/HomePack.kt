package app.forge.domain.dataset

import app.forge.domain.bodyweight.BodyweightProfile
import app.forge.domain.bodyweight.BodyweightProfile.ARCHER_PULL_UP
import app.forge.domain.bodyweight.BodyweightProfile.ARCHER_PUSH_UP
import app.forge.domain.bodyweight.BodyweightProfile.BULGARIAN_SPLIT_SQUAT
import app.forge.domain.bodyweight.BodyweightProfile.CALF_RAISE
import app.forge.domain.bodyweight.BodyweightProfile.DIP
import app.forge.domain.bodyweight.BodyweightProfile.DOORWAY_ROW
import app.forge.domain.bodyweight.BodyweightProfile.ELEVATED_PIKE_PUSH_UP
import app.forge.domain.bodyweight.BodyweightProfile.FEET_ELEVATED_ROW
import app.forge.domain.bodyweight.BodyweightProfile.HANDSTAND_PUSH_UP
import app.forge.domain.bodyweight.BodyweightProfile.HANGING_LEG_RAISE
import app.forge.domain.bodyweight.BodyweightProfile.INCLINE_PUSH_UP
import app.forge.domain.bodyweight.BodyweightProfile.INVERTED_ROW
import app.forge.domain.bodyweight.BodyweightProfile.KNEE_PUSH_UP
import app.forge.domain.bodyweight.BodyweightProfile.PIKE_PUSH_UP
import app.forge.domain.bodyweight.BodyweightProfile.PISTOL_SQUAT
import app.forge.domain.bodyweight.BodyweightProfile.PSEUDO_PLANCHE_PUSH_UP
import app.forge.domain.bodyweight.BodyweightProfile.PULL_UP
import app.forge.domain.bodyweight.BodyweightProfile.PUSH_UP
import app.forge.domain.bodyweight.BodyweightProfile.SINGLE_LEG_CALF_RAISE
import app.forge.domain.bodyweight.BodyweightProfile.SPLIT_SQUAT
import app.forge.domain.bodyweight.BodyweightProfile.SQUAT
import app.forge.domain.bodyweight.BodyweightProfile.WALL_PUSH_UP
import app.forge.domain.model.Equipment
import app.forge.domain.model.ExerciseCategory
import app.forge.domain.model.LogType
import app.forge.domain.model.Muscle
import app.forge.domain.model.Muscle.ABDOMINALS
import app.forge.domain.model.Muscle.BICEPS
import app.forge.domain.model.Muscle.CALVES
import app.forge.domain.model.Muscle.CHEST
import app.forge.domain.model.Muscle.FOREARMS
import app.forge.domain.model.Muscle.GLUTES
import app.forge.domain.model.Muscle.HAMSTRINGS
import app.forge.domain.model.Muscle.LATS
import app.forge.domain.model.Muscle.LOWER_BACK
import app.forge.domain.model.Muscle.MIDDLE_BACK
import app.forge.domain.model.Muscle.QUADRICEPS
import app.forge.domain.model.Muscle.SHOULDERS
import app.forge.domain.model.Muscle.TRAPS
import app.forge.domain.model.Muscle.TRICEPS
import java.util.UUID

/**
 * Forge's own home-training pack: calisthenics progressions and weighted-bag moves the
 * public dataset lacks. Bundled like the dataset (same IDs on every phone), never
 * mixed up with your custom exercises.
 */
object HomePack {
    /** Bump when the pack changes; the app then refreshes these exercises. */
    const val VERSION = 1

    const val SOURCE_PREFIX = "forge:"

    fun stableId(sourceId: String): String =
        UUID.nameUUIDFromBytes("forge-home-pack:$sourceId".toByteArray()).toString()

    private fun bw(
        key: String,
        name: String,
        primary: List<Muscle>,
        secondary: List<Muscle>,
        profile: BodyweightProfile?,
        steps: List<String>,
        equipment: Equipment = Equipment.BODY_ONLY,
        logType: LogType = LogType.REPS,
        level: String = "beginner",
        elevationCm: Double? = null,
    ) = ExerciseSeed(
        id = stableId(SOURCE_PREFIX + key),
        sourceId = SOURCE_PREFIX + key,
        name = name,
        primaryMuscles = primary,
        secondaryMuscles = secondary,
        equipment = equipment,
        category = ExerciseCategory.STRENGTH,
        logType = logType,
        bodyweightProfile = profile,
        bodyweightElevationCm = elevationCm,
        mechanic = "compound",
        force = null,
        level = level,
        instructions = steps,
        images = emptyList(),
    )

    private fun bag(
        key: String,
        name: String,
        primary: List<Muscle>,
        secondary: List<Muscle>,
        steps: List<String>,
        logType: LogType = LogType.WEIGHT_REPS,
    ) = bw(key, name, primary, secondary, null, steps, Equipment.WEIGHTED_BAG, logType, "beginner")

    val exercises: List<ExerciseSeed> = listOf(
        // ---- Push-up progression ---------------------------------------------------------
        bw("wall_push_up", "Wall Push-Up", listOf(CHEST), listOf(TRICEPS, SHOULDERS), WALL_PUSH_UP, listOf(
            "Stand an arm's length from a wall and put your hands on it at shoulder height.",
            "Keep your body straight from head to heels and bend your elbows to bring your chest to the wall.",
            "Push back to straight arms. Step further from the wall to make it harder.",
        )),
        bw("incline_push_up_high", "Incline Push-Up (Kitchen Bench)", listOf(CHEST), listOf(TRICEPS, SHOULDERS), INCLINE_PUSH_UP, listOf(
            "Hands on the edge of a kitchen bench or desk (about 90 cm), slightly wider than your shoulders.",
            "Walk your feet back until your body is a straight line.",
            "Lower your chest to the edge, elbows about 45° from your body, then press back up.",
        ), elevationCm = 90.0),
        bw("incline_push_up_low", "Incline Push-Up (Chair or Couch)", listOf(CHEST), listOf(TRICEPS, SHOULDERS), INCLINE_PUSH_UP, listOf(
            "Hands on a sturdy chair seat, step or couch (about 45 cm). Brace it against a wall so it can't slide.",
            "Body straight, core tight. Lower your chest to the edge.",
            "Press back up without letting your hips sag.",
        ), elevationCm = 45.0),
        bw("knee_push_up", "Knee Push-Up", listOf(CHEST), listOf(TRICEPS, SHOULDERS), KNEE_PUSH_UP, listOf(
            "Start on hands and knees, then walk your hands forward until your body is straight from head to knees.",
            "Lower your chest to the floor with elbows about 45° from your sides.",
            "Push back up, keeping your hips in line.",
        )),
        bw("diamond_push_up", "Diamond Push-Up", listOf(TRICEPS), listOf(CHEST, SHOULDERS), PUSH_UP, listOf(
            "Push-up position with your hands together under your chest, thumbs and index fingers making a diamond.",
            "Lower until your chest touches your hands, elbows tucked close to your body.",
            "Press back up. Same load as a push-up, but much more triceps.",
        ), level = "intermediate"),
        bw("archer_push_up", "Archer Push-Up", listOf(CHEST), listOf(TRICEPS, SHOULDERS), ARCHER_PUSH_UP, listOf(
            "Push-up position with hands very wide, fingers turned out.",
            "Shift your weight to one side and bend that arm while the other stays straight like a bow string.",
            "Press back to the middle and alternate sides. Count reps per side.",
        ), level = "intermediate"),
        bw("pseudo_planche_push_up", "Pseudo Planche Push-Up", listOf(CHEST, SHOULDERS), listOf(TRICEPS, ABDOMINALS), PSEUDO_PLANCHE_PUSH_UP, listOf(
            "Push-up position with hands turned out and placed beside your lower ribs or hips.",
            "Lean your shoulders forward past your hands and keep them there.",
            "Lower and press while staying leaned forward. More lean = harder.",
        ), level = "expert"),

        // ---- Handstand push-up progression ----------------------------------------------
        bw("pike_push_up", "Pike Push-Up", listOf(SHOULDERS), listOf(TRICEPS, TRAPS), PIKE_PUSH_UP, listOf(
            "Start in a push-up, then walk your feet in and lift your hips high so your body makes an upside-down V.",
            "Bend your elbows to lower the top of your head towards the floor just in front of your hands.",
            "Press back up through your shoulders.",
        )),
        bw("elevated_pike_push_up", "Elevated Pike Push-Up", listOf(SHOULDERS), listOf(TRICEPS, TRAPS), ELEVATED_PIKE_PUSH_UP, listOf(
            "Feet on a chair or bed, hands on the floor, hips stacked above your shoulders.",
            "Lower your head towards the floor, elbows pointing back rather than out.",
            "Press back up. The more vertical your body, the harder it gets.",
        ), level = "intermediate"),
        bw("wall_handstand_hold", "Wall Handstand Hold", listOf(SHOULDERS), listOf(TRICEPS, ABDOMINALS), null, listOf(
            "Kick up into a handstand with your back to a wall, hands about 15 cm from it.",
            "Push the floor away, squeeze your glutes and keep your ribs down.",
            "Hold for time, then come down with control.",
        ), logType = LogType.DURATION, level = "intermediate"),
        bw("handstand_push_up_negative", "Handstand Push-Up Negative (Wall)", listOf(SHOULDERS), listOf(TRICEPS), HANDSTAND_PUSH_UP, listOf(
            "Kick up into a wall handstand.",
            "Lower your head to the floor as slowly as you can (aim for 3–5 seconds).",
            "Come down from the handstand and kick up again for the next rep.",
        ), level = "expert"),

        // ---- Pull progression -------------------------------------------------------------
        bw("doorway_row", "Doorway Towel Row", listOf(MIDDLE_BACK), listOf(LATS, BICEPS), DOORWAY_ROW, listOf(
            "Loop a strong towel around a solid door handle (on a closed, latched door) or hold both sides of a door frame.",
            "Lean back with straight arms and your feet close to the door.",
            "Pull your chest to your hands, squeezing your shoulder blades. Walk your feet closer to make it harder.",
        )),
        bw("table_row", "Table Row", listOf(MIDDLE_BACK), listOf(LATS, BICEPS), INVERTED_ROW, listOf(
            "Lie under a sturdy table and grip its edge with both hands, shoulder-width apart.",
            "Keep your heels on the floor and your body straight like a plank.",
            "Pull your chest to the table edge, then lower slowly. Test that the table can't tip first.",
        )),
        bw("feet_elevated_row", "Feet-Elevated Table Row", listOf(MIDDLE_BACK), listOf(LATS, BICEPS), FEET_ELEVATED_ROW, listOf(
            "Set up like a table row but with your heels on a chair, so your body is horizontal.",
            "Pull your chest to the edge, pause, and lower slowly.",
        ), level = "intermediate"),
        bw("negative_pull_up", "Negative Pull-Up", listOf(LATS), listOf(BICEPS, MIDDLE_BACK), PULL_UP, listOf(
            "Use a chair to get your chin over the bar.",
            "Take your feet off and lower yourself as slowly as you can (aim for 3–5 seconds) to straight arms.",
            "Step back up and repeat.",
        ), equipment = Equipment.PULL_UP_BAR),
        bw("archer_pull_up", "Archer Pull-Up", listOf(LATS), listOf(BICEPS, MIDDLE_BACK), ARCHER_PULL_UP, listOf(
            "Hang from a bar with a very wide grip.",
            "Pull towards one hand while the other arm stays nearly straight.",
            "Lower with control and alternate sides. Count reps per side.",
        ), equipment = Equipment.PULL_UP_BAR, level = "expert"),

        // ---- Dip progression -----------------------------------------------------------------
        bw("negative_dip", "Negative Dip", listOf(TRICEPS), listOf(CHEST, SHOULDERS), DIP, listOf(
            "Support yourself on straight arms between two sturdy chairs (backs facing in) or dip bars.",
            "Lower yourself as slowly as you can until your shoulders are just below your elbows.",
            "Use your feet to get back to the top and repeat.",
        )),

        // ---- Single-leg squat progression ------------------------------------------------
        bw("box_squat", "Box Squat (Sit to Stand)", listOf(QUADRICEPS), listOf(GLUTES), SQUAT, listOf(
            "Stand in front of a chair, feet shoulder-width apart.",
            "Sit back until you lightly touch the seat, without flopping down.",
            "Stand back up, driving through your whole foot.",
        )),
        bw("split_squat", "Split Squat", listOf(QUADRICEPS), listOf(GLUTES, HAMSTRINGS), SPLIT_SQUAT, listOf(
            "Take a long stride, back heel lifted.",
            "Lower your back knee towards the floor, keeping your front knee over your foot.",
            "Push up through the front foot. Do all reps on one side, then switch. Count reps per leg.",
        )),
        bw("bulgarian_split_squat", "Bulgarian Split Squat", listOf(QUADRICEPS), listOf(GLUTES, HAMSTRINGS), BULGARIAN_SPLIT_SQUAT, listOf(
            "Rest the top of your back foot on a chair or bed behind you.",
            "Lower until your front thigh is about parallel to the floor.",
            "Drive up through the front foot. Count reps per leg.",
        ), level = "intermediate"),
        bw("shrimp_squat", "Shrimp Squat", listOf(QUADRICEPS), listOf(GLUTES), PISTOL_SQUAT, listOf(
            "Stand on one leg and hold the other foot behind you, knee bent.",
            "Lower until your back knee touches the floor (use a cushion), keeping your chest up.",
            "Stand back up on the working leg. Count reps per leg.",
        ), level = "expert"),
        bw("box_pistol_squat", "Pistol Squat to Box", listOf(QUADRICEPS), listOf(GLUTES), PISTOL_SQUAT, listOf(
            "Stand on one leg in front of a chair with the other leg held out in front.",
            "Sit back onto the chair on one leg, under control.",
            "Stand up on the same leg. Use a lower box as you get stronger.",
        ), level = "intermediate"),
        bw("pistol_squat", "Pistol Squat", listOf(QUADRICEPS), listOf(GLUTES, ABDOMINALS), PISTOL_SQUAT, listOf(
            "Stand on one leg, the other leg straight out in front, arms forward for balance.",
            "Squat all the way down on the standing leg, heel flat.",
            "Stand back up without the free foot touching the floor. Count reps per leg.",
        ), level = "expert"),

        // ---- Core -------------------------------------------------------------------------
        bw("hollow_body_hold", "Hollow Body Hold", listOf(ABDOMINALS), emptyList(), null, listOf(
            "Lie on your back, press your lower back into the floor.",
            "Lift your shoulders, arms and straight legs a few centimetres off the floor.",
            "Hold the banana shape for time. Bend your knees to make it easier.",
        ), logType = LogType.DURATION),
        bw("hanging_knee_raise", "Hanging Knee Raise", listOf(ABDOMINALS), listOf(FOREARMS), HANGING_LEG_RAISE, listOf(
            "Hang from a bar with straight arms.",
            "Bring your knees up towards your chest without swinging.",
            "Lower slowly.",
        ), equipment = Equipment.PULL_UP_BAR),
        bw("toes_to_bar", "Toes to Bar", listOf(ABDOMINALS), listOf(LATS, FOREARMS), HANGING_LEG_RAISE, listOf(
            "Hang from a bar.",
            "Keeping your legs straight, lift your toes all the way to the bar.",
            "Lower with control, no swinging.",
        ), equipment = Equipment.PULL_UP_BAR, level = "expert"),
        bw("tuck_l_sit", "Tuck L-Sit", listOf(ABDOMINALS), listOf(TRICEPS, SHOULDERS), null, listOf(
            "Sit between two chairs or on the floor with hands beside your hips.",
            "Press down to lift your body, knees tucked to your chest.",
            "Hold for time.",
        ), logType = LogType.DURATION, level = "intermediate"),
        bw("l_sit", "L-Sit", listOf(ABDOMINALS), listOf(TRICEPS, QUADRICEPS), null, listOf(
            "Hands beside your hips on the floor, chairs or parallettes.",
            "Press down and lift your body with your legs straight out in front.",
            "Hold for time with your shoulders pushed down.",
        ), logType = LogType.DURATION, level = "expert"),

        // ---- Hips, hamstrings, calves ----------------------------------------------------
        bw("single_leg_rdl", "Single-Leg Romanian Deadlift", listOf(HAMSTRINGS), listOf(GLUTES, LOWER_BACK), null, listOf(
            "Stand on one leg with a soft knee.",
            "Hinge forward from the hip as the other leg goes straight back, back flat.",
            "Return to standing by squeezing the glute. Count reps per leg.",
        )),
        bw("nordic_curl_negative", "Nordic Curl Negative", listOf(HAMSTRINGS), listOf(GLUTES), null, listOf(
            "Kneel on a cushion with your heels anchored under a couch or held by someone.",
            "Keeping a straight line from knees to head, lower yourself forward as slowly as possible.",
            "Catch yourself with your hands and push back up.",
        ), level = "intermediate"),
        bw("calf_raise", "Calf Raise", listOf(CALVES), emptyList(), CALF_RAISE, listOf(
            "Stand with the balls of your feet on a step, heels hanging off.",
            "Rise as high as you can onto your toes, pause.",
            "Lower your heels below the step for a full stretch.",
        )),
        bw("single_leg_calf_raise", "Single-Leg Calf Raise", listOf(CALVES), emptyList(), SINGLE_LEG_CALF_RAISE, listOf(
            "Same as a calf raise on one foot, holding a wall for balance.",
            "Full range: deep stretch at the bottom, pause at the top. Count reps per leg.",
        )),

        // ---- Weighted bag -----------------------------------------------------------------
        bag("bag_bear_hug_squat", "Bag Bear-Hug Squat", listOf(QUADRICEPS), listOf(GLUTES, ABDOMINALS, LOWER_BACK), listOf(
            "Hug the bag tight to your chest.",
            "Squat down until your thighs are at least parallel, chest up.",
            "Drive back up. The bag in front keeps you upright.",
        )),
        bag("bag_zercher_squat", "Bag Zercher Squat", listOf(QUADRICEPS), listOf(GLUTES, ABDOMINALS, BICEPS), listOf(
            "Cradle the bag in the crook of your elbows.",
            "Squat down with a tall chest.",
            "Stand up, keeping the bag tight to your body.",
        )),
        bag("bag_deadlift", "Bag Deadlift", listOf(HAMSTRINGS), listOf(GLUTES, LOWER_BACK, FOREARMS), listOf(
            "Stand over the bag, feet hip-width apart.",
            "Hinge at the hips, bend your knees and grab the handles or the bag's ends with a flat back.",
            "Stand up by driving through your feet, then lower it the same way.",
        )),
        bag("bag_romanian_deadlift", "Bag Romanian Deadlift", listOf(HAMSTRINGS), listOf(GLUTES, LOWER_BACK), listOf(
            "Hold the bag in front of your thighs.",
            "Push your hips back with a slight knee bend until you feel your hamstrings stretch.",
            "Squeeze your glutes to stand back up. Keep your back flat.",
        )),
        bag("bag_clean", "Bag Clean", listOf(HAMSTRINGS, GLUTES), listOf(QUADRICEPS, TRAPS, BICEPS), listOf(
            "Bag on the floor between your feet.",
            "Deadlift it to your knees, then snap your hips forward to pop it up.",
            "Catch it on your chest in a bear hug, stand tall, and lower it back down.",
        )),
        bag("bag_shoulder_press", "Bag Shoulder Press", listOf(SHOULDERS), listOf(TRICEPS, ABDOMINALS), listOf(
            "Hold the bag at your chest (clean it up first).",
            "Brace your core and press it straight overhead.",
            "Lower it back to your chest with control.",
        )),
        bag("bag_bent_over_row", "Bag Bent-Over Row", listOf(MIDDLE_BACK), listOf(LATS, BICEPS, LOWER_BACK), listOf(
            "Hinge forward with a flat back, holding the bag below you.",
            "Pull the bag to your stomach, squeezing your shoulder blades.",
            "Lower slowly without rounding your back.",
        )),
        bag("bag_floor_press", "Bag Floor Press", listOf(CHEST), listOf(TRICEPS, SHOULDERS), listOf(
            "Lie on your back with the bag on your chest.",
            "Press it up to straight arms.",
            "Lower until your elbows touch the floor.",
        )),
        bag("bag_reverse_lunge", "Bag Reverse Lunge", listOf(QUADRICEPS), listOf(GLUTES, HAMSTRINGS), listOf(
            "Hold the bag in a bear hug or on one shoulder.",
            "Step back into a lunge until your back knee nearly touches the floor.",
            "Push back to standing. Alternate legs or do one side at a time.",
        )),
        bag("bag_good_morning", "Bag Good Morning", listOf(HAMSTRINGS), listOf(GLUTES, LOWER_BACK), listOf(
            "Bag across your upper back or hugged to your chest.",
            "With soft knees, push your hips back and lean forward with a flat back.",
            "Stand up by squeezing your glutes. Go light; this is about the stretch.",
        )),
        bag("bag_hip_thrust", "Bag Hip Thrust", listOf(GLUTES), listOf(HAMSTRINGS), listOf(
            "Sit with your upper back against a couch, the bag across your hips.",
            "Drive through your heels to lift your hips until your body is flat from knees to shoulders.",
            "Pause and squeeze, then lower.",
        )),
        bag("bag_shouldering", "Bag Shouldering", listOf(GLUTES, HAMSTRINGS), listOf(LOWER_BACK, TRAPS, BICEPS), listOf(
            "Bag on the floor. Squat down and wrap your arms under it.",
            "Lap it onto your thighs, then explode up and roll it onto one shoulder.",
            "Drop it back down and alternate shoulders.",
        )),
        bag("bag_carry", "Bag Carry", listOf(FOREARMS, ABDOMINALS), listOf(TRAPS, LOWER_BACK), listOf(
            "Pick the bag up in a bear hug or on a shoulder.",
            "Walk tall with short, quick steps.",
            "Log distance and/or time.",
        ), logType = LogType.DISTANCE_DURATION),
    )

    /**
     * Progressions from easiest to hardest. Steps are source IDs: "forge:…" for pack
     * exercises, plain IDs for free-exercise-db ones (so existing history stays linked).
     */
    enum class Chain(val label: String, val steps: List<String>) {
        PUSH_UP(
            "Push-up progression",
            listOf(
                "forge:wall_push_up", "forge:incline_push_up_high", "forge:incline_push_up_low",
                "forge:knee_push_up", "Pushups", "forge:diamond_push_up", "Decline_Push-Up",
                "forge:archer_push_up", "forge:pseudo_planche_push_up", "Single-Arm_Push-Up",
            ),
        ),
        HANDSTAND(
            "Handstand push-up progression",
            listOf(
                "forge:pike_push_up", "forge:elevated_pike_push_up", "forge:wall_handstand_hold",
                "forge:handstand_push_up_negative", "Handstand_Push-Ups",
            ),
        ),
        PULL(
            "Pull-up progression",
            listOf(
                "forge:doorway_row", "forge:table_row", "forge:feet_elevated_row", "Scapular_Pull-Up",
                "forge:negative_pull_up", "Chin-Up", "Pullups", "forge:archer_pull_up", "One_Arm_Chin-Up",
            ),
        ),
        DIP("Dip progression", listOf("Bench_Dips", "forge:negative_dip", "Dips_-_Triceps_Version", "Ring_Dips")),
        SQUAT(
            "Single-leg squat progression",
            listOf(
                "forge:box_squat", "Bodyweight_Squat", "forge:split_squat", "forge:bulgarian_split_squat",
                "forge:box_pistol_squat", "forge:shrimp_squat", "forge:pistol_squat",
            ),
        ),
        LEG_RAISE(
            "Leg raise progression",
            listOf("Dead_Bug", "forge:hanging_knee_raise", "Hanging_Leg_Raise", "forge:toes_to_bar"),
        ),
        CORE_HOLD("Core hold progression", listOf("Plank", "forge:hollow_body_hold", "forge:tuck_l_sit", "forge:l_sit")),
        HINGE(
            "Hip & hamstring progression",
            listOf("Butt_Lift_Bridge", "Single_Leg_Glute_Bridge", "forge:single_leg_rdl", "forge:nordic_curl_negative"),
        ),
        CALF("Calf progression", listOf("forge:calf_raise", "forge:single_leg_calf_raise"));

        companion object {
            fun fromKey(key: String?): Chain? = key?.let { k -> entries.firstOrNull { it.name == k } }
        }
    }

    /** sourceId → (chain, step index) for every exercise that's part of a progression. */
    val chainPositions: Map<String, Pair<Chain, Int>> =
        Chain.entries.flatMap { chain -> chain.steps.mapIndexed { i, id -> id to (chain to i) } }.toMap()
}

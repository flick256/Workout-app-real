package app.forge.domain.model

/** Muscle groups, matching the names used by free-exercise-db. */
enum class Muscle(val label: String) {
    ABDOMINALS("Abs"),
    ABDUCTORS("Abductors"),
    ADDUCTORS("Adductors"),
    BICEPS("Biceps"),
    CALVES("Calves"),
    CHEST("Chest"),
    FOREARMS("Forearms"),
    GLUTES("Glutes"),
    HAMSTRINGS("Hamstrings"),
    LATS("Lats"),
    LOWER_BACK("Lower back"),
    MIDDLE_BACK("Upper back"),
    NECK("Neck"),
    QUADRICEPS("Quads"),
    SHOULDERS("Shoulders"),
    TRAPS("Traps"),
    TRICEPS("Triceps"),
}

/**
 * Training equipment. [hasWeights] marks items that come in specific weights you can
 * record in Settings (e.g. a 10 kg and a 15 kg bag), so logging and suggestions can
 * offer only weights you actually own.
 */
enum class Equipment(val label: String, val hasWeights: Boolean = false) {
    BODY_ONLY("Bodyweight"),
    DUMBBELL("Dumbbells", hasWeights = true),
    BANDS("Resistance bands"),
    PULL_UP_BAR("Pull-up bar"),
    WEIGHTED_BAG("Weighted bag", hasWeights = true),
    WEIGHTED_VEST("Weighted vest", hasWeights = true),
    KETTLEBELL("Kettlebell", hasWeights = true),
    BARBELL("Barbell", hasWeights = true),
    EZ_BAR("EZ curl bar", hasWeights = true),
    CABLE("Cable machine"),
    MACHINE("Machines"),
    MEDICINE_BALL("Medicine ball", hasWeights = true),
    EXERCISE_BALL("Exercise ball"),
    FOAM_ROLL("Foam roller"),
    OTHER("Other"),
}

enum class ExerciseCategory {
    STRENGTH,
    STRETCHING,
    PLYOMETRICS,
    CARDIO,
    POWERLIFTING,
    OLYMPIC_WEIGHTLIFTING,
    STRONGMAN,
}

enum class SetType(val short: String) {
    WARMUP("W"),
    WORKING(""),
    DROP("D"),
    FAILURE("F");

    /** Warm-ups don't count towards volume, PRs or progression. */
    val countsAsWork: Boolean get() = this != WARMUP
}

/** Which fields a set row shows for an exercise. */
enum class LogType {
    /** Weight × reps: dumbbell press, squats with a bag. */
    WEIGHT_REPS,

    /** Bodyweight reps, with optional added weight: push-ups, pull-ups. */
    REPS,

    /** A hold or a stretch: plank, hamstring stretch. */
    DURATION,

    /** Cardio: distance and time. */
    DISTANCE_DURATION;

    companion object {
        fun infer(category: ExerciseCategory, equipment: Equipment?, isStatic: Boolean): LogType {
            val bodyweight = equipment == null || equipment == Equipment.BODY_ONLY ||
                equipment == Equipment.PULL_UP_BAR
            return when {
                category == ExerciseCategory.CARDIO -> DISTANCE_DURATION
                category == ExerciseCategory.STRETCHING -> DURATION
                isStatic && bodyweight -> DURATION
                bodyweight -> REPS
                else -> WEIGHT_REPS
            }
        }
    }
}

enum class SessionStatus { ACTIVE, FINISHED, DISCARDED }

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

enum class Equipment(val label: String) {
    BODY_ONLY("Bodyweight"),
    DUMBBELL("Dumbbells"),
    BANDS("Resistance bands"),
    PULL_UP_BAR("Pull-up bar"),
    KETTLEBELL("Kettlebell"),
    BARBELL("Barbell"),
    EZ_BAR("EZ curl bar"),
    CABLE("Cable machine"),
    MACHINE("Machines"),
    MEDICINE_BALL("Medicine ball"),
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

enum class SetType { WARMUP, WORKING, DROP, FAILURE }

enum class SessionStatus { ACTIVE, FINISHED, DISCARDED }

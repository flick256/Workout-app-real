package app.forge.domain.goals

/** Lifetime numbers the achievements are checked against. */
data class AchievementStats(
    val workouts: Int = 0,
    val weekStreak: Int = 0,
    val prs: Int = 0,
    val bestSessionVolumeKg: Double = 0.0,
    val activities: Int = 0,
    val foodDaysLogged: Int = 0,
    val bestHabitStreak: Int = 0,
    val goalsReached: Int = 0,
)

enum class AchievementGroup(val label: String) { WORKOUTS("Workouts"), CONSISTENCY("Consistency"), STRENGTH("Strength"), LIFESTYLE("Lifestyle") }

/**
 * Milestones worth celebrating. Every one is about showing up and getting stronger,
 * never about eating less or weighing less.
 */
enum class Achievement(
    val title: String,
    val description: String,
    val group: AchievementGroup,
    val target: Double,
    val measure: (AchievementStats) -> Double,
) {
    FIRST_WORKOUT("First rep", "Finish your first workout", AchievementGroup.WORKOUTS, 1.0, { it.workouts.toDouble() }),
    WORKOUTS_10("Getting into it", "10 workouts", AchievementGroup.WORKOUTS, 10.0, { it.workouts.toDouble() }),
    WORKOUTS_50("Regular", "50 workouts", AchievementGroup.WORKOUTS, 50.0, { it.workouts.toDouble() }),
    WORKOUTS_100("Centurion", "100 workouts", AchievementGroup.WORKOUTS, 100.0, { it.workouts.toDouble() }),
    WORKOUTS_250("Lifer", "250 workouts", AchievementGroup.WORKOUTS, 250.0, { it.workouts.toDouble() }),
    STREAK_4("A month strong", "Train every week for 4 weeks", AchievementGroup.CONSISTENCY, 4.0, { it.weekStreak.toDouble() }),
    STREAK_12("Habit formed", "Train every week for 12 weeks", AchievementGroup.CONSISTENCY, 12.0, { it.weekStreak.toDouble() }),
    STREAK_26("Half a year", "Train every week for 26 weeks", AchievementGroup.CONSISTENCY, 26.0, { it.weekStreak.toDouble() }),
    HABIT_7("Seven days", "Keep a habit going for 7 days", AchievementGroup.CONSISTENCY, 7.0, { it.bestHabitStreak.toDouble() }),
    HABIT_30("Thirty days", "Keep a habit going for 30 days", AchievementGroup.CONSISTENCY, 30.0, { it.bestHabitStreak.toDouble() }),
    FIRST_PR("New best", "Set your first personal record", AchievementGroup.STRENGTH, 1.0, { it.prs.toDouble() }),
    PRS_25("Record breaker", "Set 25 personal records", AchievementGroup.STRENGTH, 25.0, { it.prs.toDouble() }),
    TONNE("One tonne", "Move 1,000 kg in one workout", AchievementGroup.STRENGTH, 1_000.0, { it.bestSessionVolumeKg }),
    FIVE_TONNE("Five tonnes", "Move 5,000 kg in one workout", AchievementGroup.STRENGTH, 5_000.0, { it.bestSessionVolumeKg }),
    GOAL_REACHED("Goal getter", "Reach a goal you set", AchievementGroup.STRENGTH, 1.0, { it.goalsReached.toDouble() }),
    ALL_ROUNDER("All-rounder", "Log 10 sports or cardio sessions", AchievementGroup.LIFESTYLE, 10.0, { it.activities.toDouble() }),
    FUELLED("Fuelled", "Log your food on 14 days", AchievementGroup.LIFESTYLE, 14.0, { it.foodDaysLogged.toDouble() }),
    ;

    fun progress(stats: AchievementStats): Double = (measure(stats) / target).coerceIn(0.0, 1.0)

    fun unlocked(stats: AchievementStats): Boolean = measure(stats) >= target

    companion object {
        fun unlocked(stats: AchievementStats): List<Achievement> = entries.filter { it.unlocked(stats) }

        /** The closest few not yet unlocked, to show what's next. */
        fun upNext(stats: AchievementStats, count: Int = 3): List<Achievement> =
            entries.filterNot { it.unlocked(stats) }.sortedByDescending { it.progress(stats) }.take(count)
    }
}

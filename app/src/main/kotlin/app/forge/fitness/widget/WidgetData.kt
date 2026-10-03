package app.forge.fitness.widget

import app.forge.fitness.data.goals.GoalsRepository
import app.forge.fitness.data.nutrition.FoodRepository
import app.forge.fitness.data.nutrition.total
import app.forge.fitness.data.prefs.UserPreferencesRepository
import app.forge.fitness.data.routine.RoutineRepository
import app.forge.fitness.data.workout.WorkoutRepository
import app.forge.fitness.feature.routines.planFlows
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

data class WidgetHabit(val id: String, val name: String, val done: Boolean, val auto: Boolean, val streak: Int)

/** Everything the home-screen widgets show, as one small value. */
data class TodaySnapshot(
    val activeWorkout: String? = null,
    /** "Full Body B", "Rest day · next Thu", "Done for today", or null with no program. */
    val planLine: String? = null,
    /** Today's routine, when there's one to start. */
    val plannedRoutineId: String? = null,
    val workoutsThisWeek: Int = 0,
    val weekStreak: Int = 0,
    val kcal: Int = 0,
    val kcalTarget: Int? = null,
    val proteinG: Int = 0,
    val proteinTarget: Int? = null,
    val habits: List<WidgetHabit> = emptyList(),
)

@Singleton
class WidgetData @Inject constructor(
    private val workouts: WorkoutRepository,
    private val routines: RoutineRepository,
    private val preferences: UserPreferencesRepository,
    private val foods: FoodRepository,
    private val goals: GoalsRepository,
) {
    fun observeToday(): Flow<TodaySnapshot> {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val food = combine(foods.observeDay(today), foods.observeTargets()) { entries, t -> entries.total() to t.targetsOrNull }
        return combine(
            workouts.observeActiveSession(),
            planFlows(routines, preferences),
            workouts.observeHistory(),
            food,
            goals.observeOverview(),
        ) { active, plan, history, (eaten, targets), overview ->
            val dates = history.map { Instant.ofEpochMilli(it.startedAt).atZone(zone).toLocalDate() }
            val program = plan.active
            val planLine = program?.let { p ->
                val next = p.next
                when {
                    next == null -> null
                    p.plan.doneToday -> "Done for today" + (p.plan.nextTrainingDate?.let { " · next ${dayName(it, today)}" } ?: "")
                    !p.plan.isTrainingDay -> "Rest day" + (p.plan.nextTrainingDate?.let { " · next ${dayName(it, today)}" } ?: "")
                    else -> "${next.name} · ~${next.minutes} min"
                }
            }
            TodaySnapshot(
                activeWorkout = active?.name,
                planLine = planLine,
                plannedRoutineId = program?.takeIf { it.plan.isTrainingDay && !it.plan.doneToday }?.next?.id,
                workoutsThisWeek = dates.count { !it.isBefore(monday) },
                // Same streak as Progress and achievements: workouts plus sports and cardio.
                weekStreak = overview.stats.weekStreak,
                kcal = eaten.kcal.roundToInt(),
                kcalTarget = targets?.kcal,
                proteinG = eaten.proteinG.roundToInt(),
                proteinTarget = targets?.proteinG,
                habits = overview.habits.filter { it.isDueToday }.map {
                    WidgetHabit(it.habit.id, it.habit.name, it.doneToday == true, it.kind.auto, it.streak)
                },
            )
        }.distinctUntilChanged()
    }

    private fun dayName(date: LocalDate, today: LocalDate): String =
        if (date == today.plusDays(1)) "tomorrow" else date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())
}

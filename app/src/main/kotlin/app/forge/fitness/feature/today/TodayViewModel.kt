package app.forge.fitness.feature.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.forge.domain.model.WeightUnit
import app.forge.fitness.data.db.SessionSummaryRow
import app.forge.fitness.data.db.WorkoutSessionEntity
import app.forge.fitness.data.prefs.UserPreferencesRepository
import app.forge.domain.model.Muscle
import app.forge.domain.suggest.DeloadHint
import app.forge.domain.suggest.MuscleStatus
import app.forge.domain.suggest.QuickPlan
import app.forge.domain.suggest.RoutineChoice
import app.forge.domain.suggest.RoutineOption
import app.forge.domain.suggest.TrainToday
import app.forge.domain.activity.Readiness
import app.forge.domain.activity.ReadinessLevel
import app.forge.fitness.data.activity.ActivityRepository
import app.forge.fitness.data.db.DailyHealthEntity
import app.forge.fitness.data.routine.RoutineRepository
import java.time.LocalDate
import app.forge.fitness.data.suggest.SuggestionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import app.forge.fitness.data.workout.WorkoutRepository
import app.forge.fitness.feature.routines.RoutinesState
import app.forge.fitness.feature.routines.StartResult
import app.forge.fitness.feature.routines.planFlows
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class TodayState(
    val active: WorkoutSessionEntity? = null,
    val lastWorkout: SessionSummaryRow? = null,
    val workoutsThisWeek: Int = 0,
    val unit: WeightUnit = WeightUnit.KG,
    val routines: RoutinesState = RoutinesState(),
    val minutes: Int = 30,
    val muscles: List<MuscleStatus> = emptyList(),
    val bestRoutine: RoutineChoice? = null,
    val quickPlan: QuickPlan? = null,
    val deload: DeloadHint? = null,
    val health: HealthToday = HealthToday(),
)

/** Today's watch/strap data (only when Health Connect syncing is on). */
data class HealthToday(
    val enabled: Boolean = false,
    val readiness: Readiness = Readiness(ReadinessLevel.UNKNOWN, emptyList()),
    val day: DailyHealthEntity? = null,
)

@HiltViewModel
class TodayViewModel @Inject constructor(
    private val repository: WorkoutRepository,
    private val routineRepository: RoutineRepository,
    private val suggestions: SuggestionRepository,
    activities: ActivityRepository,
    preferences: UserPreferencesRepository,
) : ViewModel() {

    private val minutes = MutableStateFlow(30)

    private val base = combine(
        repository.observeActiveSession(),
        repository.observeHistory(),
        preferences.preferences,
        planFlows(routineRepository, preferences),
    ) { active, history, prefs, routines ->
        val weekAgo = System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000
        TodayState(
            active = active,
            lastWorkout = history.firstOrNull(),
            workoutsThisWeek = history.count { it.startedAt >= weekAgo },
            unit = prefs.weightUnit,
            routines = routines,
        )
    }

    private val health = combine(
        preferences.preferences,
        activities.observeReadiness(),
        activities.observeDaily(LocalDate.now()),
    ) { prefs, readiness, days ->
        HealthToday(prefs.healthConnectEnabled, readiness, days.lastOrNull { it.epochDay == LocalDate.now().toEpochDay() })
    }

    val state: StateFlow<TodayState> = combine(
        base,
        suggestions.observeMuscleStatus(),
        suggestions.observeDeloadHint(),
        minutes,
        health,
    ) { today, muscles, deload, mins, health ->
        // On a low-readiness day (poor sleep, low HRV), quick workouts are lighter.
        val sets = if (health.enabled && health.readiness.level == ReadinessLevel.LOW) 2 else 3
        val planned = today.routines.active?.let { plan ->
            plan.next?.id?.takeIf { plan.plan.isTrainingDay && !plan.plan.doneToday }
        }
        val options = today.routines.routines.filter { it.data.active.isNotEmpty() }.map { card ->
            val sets = mutableMapOf<Muscle, Double>()
            card.data.active.forEach { (item, exercise) ->
                exercise.primaryMuscles.forEach { sets.merge(it, item.targetSets.toDouble(), Double::plus) }
                exercise.secondaryMuscles.forEach { sets.merge(it, item.targetSets * 0.5, Double::plus) }
            }
            RoutineOption(card.id, card.name, card.minutes, sets, isPlanned = card.id == planned)
        }
        today.copy(
            minutes = mins,
            muscles = muscles,
            bestRoutine = TrainToday.bestRoutine(options, muscles, mins),
            quickPlan = TrainToday.quickPlan(muscles, mins, setsPerExercise = sets),
            deload = deload,
            health = health,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayState())

    fun setMinutes(value: Int) {
        minutes.value = value
    }

    fun dismissDeload() {
        viewModelScope.launch { suggestions.dismissDeload() }
    }

    suspend fun startQuick(plan: QuickPlan, minutes: Int): StartResult? =
        suggestions.startQuickWorkout(plan, minutes)?.let { StartResult.Started(it) }
            ?: if (repository.observeActiveSession().first() != null) StartResult.WorkoutInProgress else null

    /** Starts a new workout, or returns the one in progress. */
    suspend fun startWorkout(): String = repository.startOrResume()

    suspend fun startRoutine(routineId: String): StartResult? {
        val routine = routineRepository.getRoutineWithExercises(routineId) ?: return null
        return repository.startFromRoutine(routine)?.let { StartResult.Started(it) } ?: StartResult.WorkoutInProgress
    }
}

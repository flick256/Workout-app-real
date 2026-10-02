package app.forge.fitness.feature.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.forge.domain.model.WeightUnit
import app.forge.fitness.data.db.SessionSummaryRow
import app.forge.fitness.data.db.WorkoutSessionEntity
import app.forge.fitness.data.prefs.UserPreferencesRepository
import app.forge.fitness.data.routine.RoutineRepository
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
)

@HiltViewModel
class TodayViewModel @Inject constructor(
    private val repository: WorkoutRepository,
    private val routineRepository: RoutineRepository,
    preferences: UserPreferencesRepository,
) : ViewModel() {

    val state: StateFlow<TodayState> = combine(
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
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayState())

    /** Starts a new workout, or returns the one in progress. */
    suspend fun startWorkout(): String = repository.startOrResume()

    suspend fun startRoutine(routineId: String): StartResult? {
        val routine = routineRepository.getRoutineWithExercises(routineId) ?: return null
        return repository.startFromRoutine(routine)?.let { StartResult.Started(it) } ?: StartResult.WorkoutInProgress
    }
}

package app.forge.fitness.feature.history

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import app.forge.domain.model.WeightUnit
import app.forge.domain.workout.LoggedSet
import app.forge.domain.workout.WorkoutStats
import app.forge.domain.workout.WorkoutSummary
import app.forge.fitness.data.db.SessionExerciseWithExercise
import app.forge.fitness.data.db.SetEntryEntity
import app.forge.fitness.data.db.WorkoutSessionEntity
import app.forge.fitness.data.prefs.UserPreferencesRepository
import app.forge.fitness.data.routine.RoutineRepository
import app.forge.fitness.data.workout.WorkoutRepository
import app.forge.fitness.ui.navigation.SessionDetailRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SessionDetailState(
    val loading: Boolean = true,
    val session: WorkoutSessionEntity? = null,
    val exercises: List<Pair<SessionExerciseWithExercise, List<SetEntryEntity>>> = emptyList(),
    val summary: WorkoutSummary? = null,
    val unit: WeightUnit = WeightUnit.KG,
)

@HiltViewModel
class SessionDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: WorkoutRepository,
    private val routines: RoutineRepository,
    preferences: UserPreferencesRepository,
) : ViewModel() {

    val route: SessionDetailRoute = savedStateHandle.toRoute()

    val state: StateFlow<SessionDetailState> = combine(
        repository.observeSession(route.sessionId),
        repository.observeSessionExercises(route.sessionId),
        repository.observeSets(route.sessionId),
        preferences.preferences,
    ) { session, exercises, sets, prefs ->
        val done = sets.filter { it.completedAt != null }
        val bySe = done.groupBy { it.sessionExerciseId }
        SessionDetailState(
            loading = false,
            session = session,
            exercises = exercises.map { it to bySe[it.item.id].orEmpty().sortedBy { s -> s.position } },
            summary = WorkoutStats.summarize(done.map { LoggedSet(it.type, it.loadKg ?: it.weightKg, it.reps, it.durationSeconds) }),
            unit = prefs.weightUnit,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SessionDetailState())

    suspend fun saveAsRoutine(name: String): String = routines.saveSessionAsRoutine(route.sessionId, name)

    fun delete() = viewModelScope.launch { repository.deleteSession(route.sessionId) }

    fun restore() = viewModelScope.launch { repository.restoreSession(route.sessionId) }
}

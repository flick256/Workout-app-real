package app.forge.fitness.feature.routines

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import app.forge.fitness.data.db.RoutineExerciseEntity
import app.forge.fitness.data.db.RoutineWithExercises
import app.forge.fitness.data.prefs.UserPreferencesRepository
import app.forge.fitness.data.routine.RoutineRepository
import app.forge.fitness.data.workout.WorkoutRepository
import app.forge.fitness.ui.navigation.RoutineEditorRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class RoutineEditorState(
    val loading: Boolean = true,
    val routine: RoutineWithExercises? = null,
    val defaultRest: Int = 90,
    val minutes: Int = 0,
)

@HiltViewModel
class RoutineEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val routines: RoutineRepository,
    private val workouts: WorkoutRepository,
    preferences: UserPreferencesRepository,
) : ViewModel() {

    val routineId: String = savedStateHandle.toRoute<RoutineEditorRoute>().routineId

    val state: StateFlow<RoutineEditorState> = combine(
        routines.observeRoutine(routineId),
        preferences.preferences,
    ) { routine, prefs ->
        RoutineEditorState(
            loading = false,
            routine = routine,
            defaultRest = prefs.defaultRestSeconds,
            minutes = routine?.let { estimateMinutes(it, prefs.defaultRestSeconds) } ?: 0,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RoutineEditorState())

    fun rename(name: String) {
        viewModelScope.launch { routines.rename(routineId, name) }
    }

    fun setNotes(notes: String) {
        viewModelScope.launch { routines.setNotes(routineId, notes) }
    }

    fun update(item: RoutineExerciseEntity) {
        viewModelScope.launch { routines.updateItem(item) }
    }

    fun remove(itemId: String) {
        viewModelScope.launch { routines.removeItem(itemId) }
    }

    fun restore(itemId: String) {
        viewModelScope.launch { routines.restoreItem(itemId) }
    }

    fun supersetWithNext(itemId: String) {
        viewModelScope.launch { routines.supersetWithNext(itemId) }
    }

    fun leaveSuperset(itemId: String) {
        viewModelScope.launch { routines.leaveSuperset(itemId) }
    }

    fun reorder(orderedIds: List<String>) {
        viewModelScope.launch { routines.reorderItems(routineId, orderedIds) }
    }

    suspend fun start(id: String): StartResult? {
        val routine = routines.getRoutineWithExercises(id) ?: return null
        return workouts.startFromRoutine(routine)?.let { StartResult.Started(it) } ?: StartResult.WorkoutInProgress
    }
}

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
import app.forge.fitness.data.analytics.AnalyticsRepository
import app.forge.fitness.data.analytics.PrItem
import app.forge.fitness.data.routine.RoutineRepository
import kotlinx.coroutines.flow.MutableStateFlow
import app.forge.fitness.data.workout.WorkoutRepository
import app.forge.fitness.ui.navigation.SessionDetailRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import app.forge.fitness.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
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
    private val analytics: AnalyticsRepository,
    @param:ApplicationScope private val appScope: CoroutineScope,
    preferences: UserPreferencesRepository,
) : ViewModel() {

    val route: SessionDetailRoute = savedStateHandle.toRoute()

    /** Personal records set in this workout (beating every earlier workout). */
    val prs = MutableStateFlow<List<PrItem>>(emptyList())

    init {
        viewModelScope.launch { prs.value = analytics.prsInSession(route.sessionId) }
    }



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

    // App scope: these run after the screen has closed (delete, then Undo on the list).
    fun delete() = appScope.launch { repository.deleteSession(route.sessionId) }

    fun restore() = appScope.launch { repository.restoreSession(route.sessionId) }
}

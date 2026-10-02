package app.forge.fitness.feature.exercises

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import app.forge.domain.bodyweight.LoadEstimate
import app.forge.domain.calc.OneRepMax
import app.forge.domain.dataset.HomePack
import app.forge.domain.model.BodyMetricKind
import app.forge.domain.model.WeightUnit
import app.forge.fitness.data.db.BodyMetricDao
import app.forge.fitness.data.db.ExerciseDao
import app.forge.fitness.data.db.ExerciseEntity
import app.forge.fitness.data.db.SetEntryEntity
import app.forge.fitness.data.db.WorkoutDao
import app.forge.fitness.data.prefs.UserPreferencesRepository
import app.forge.fitness.data.workout.Loads
import app.forge.fitness.ui.navigation.ExerciseDetailRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** One past workout's sets of this exercise. */
data class ExerciseSession(val sessionId: String, val startedAt: Long, val sets: List<SetEntryEntity>) {
    /** The heaviest load moved (bodyweight share included). */
    val bestLoadKg: Double? get() = sets.mapNotNull { it.loadKg ?: it.weightKg }.maxOrNull()
    val bestE1rmKg: Double?
        get() = sets.mapNotNull { s -> (s.loadKg ?: s.weightKg)?.let { w -> s.reps?.let { OneRepMax.estimate(w, it, s.rpe) } } }.maxOrNull()
}

data class ExerciseDetailState(
    val exercise: ExerciseEntity? = null,
    val unit: WeightUnit = WeightUnit.KG,
    val bodyweightLoad: LoadEstimate? = null,
    val hasBodyweight: Boolean = false,
    val chain: HomePack.Chain? = null,
    val chainSteps: List<ExerciseEntity> = emptyList(),
    val history: List<ExerciseSession> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ExerciseDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val exercises: ExerciseDao,
    workouts: WorkoutDao,
    bodyMetrics: BodyMetricDao,
    preferences: UserPreferencesRepository,
) : ViewModel() {

    private val id = savedStateHandle.toRoute<ExerciseDetailRoute>().exerciseId
    private val exercise = exercises.observeById(id)
    private val chainSteps = exercise.flatMapLatest { e ->
        e?.progressionChain?.let { exercises.observeChain(it) } ?: flowOf(emptyList())
    }

    val state: StateFlow<ExerciseDetailState> = combine(
        exercise,
        chainSteps,
        workouts.observeExerciseHistory(id),
        bodyMetrics.observeLatest(BodyMetricKind.WEIGHT),
        preferences.preferences,
    ) { e, chain, history, bodyweight, prefs ->
        ExerciseDetailState(
            exercise = e,
            unit = prefs.weightUnit,
            bodyweightLoad = e?.let { Loads.baseEstimate(it, bodyweight?.value, prefs.heightCm) },
            hasBodyweight = bodyweight != null,
            chain = HomePack.Chain.fromKey(e?.progressionChain),
            chainSteps = chain,
            history = history.groupBy { it.sessionId }.map { (sessionId, rows) ->
                ExerciseSession(sessionId, rows.first().startedAt, rows.map { it.set })
            }.sortedByDescending { it.startedAt },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExerciseDetailState())

    /** Custom exercises can be archived (hidden from lists; past workouts keep them). */
    fun setArchived(archived: Boolean) = viewModelScope.launch {
        val e = exercises.getById(id) ?: return@launch
        exercises.update(e.copy(archived = archived, updatedAt = System.currentTimeMillis()))
    }
}

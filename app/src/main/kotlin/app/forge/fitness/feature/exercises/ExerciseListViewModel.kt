package app.forge.fitness.feature.exercises

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.forge.domain.model.Equipment
import app.forge.domain.model.Muscle
import app.forge.fitness.data.db.ExerciseDao
import app.forge.fitness.data.db.ExerciseEntity
import app.forge.fitness.data.prefs.UserPreferencesRepository
import app.forge.fitness.data.routine.RoutineRepository
import app.forge.fitness.data.workout.WorkoutRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class ExerciseListState(
    val loading: Boolean = true,
    val query: String = "",
    val muscle: Muscle? = null,
    val myEquipmentOnly: Boolean = true,
    /** Only your own exercises (archived ones included, so you can restore them). */
    val customOnly: Boolean = false,
    val recent: List<ExerciseEntity> = emptyList(),
    val all: List<ExerciseEntity> = emptyList(),
)

@HiltViewModel
class ExerciseListViewModel @Inject constructor(
    exerciseDao: ExerciseDao,
    preferences: UserPreferencesRepository,
    private val workouts: WorkoutRepository,
    private val routines: RoutineRepository,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val muscle = MutableStateFlow<Muscle?>(null)
    private val myEquipmentOnly = MutableStateFlow(true)
    private val customOnly = MutableStateFlow(false)

    private data class Filters(val query: String, val muscle: Muscle?, val mine: Boolean, val custom: Boolean)

    private val filters = combine(query, muscle, myEquipmentOnly, customOnly, ::Filters)

    val state: StateFlow<ExerciseListState> = combine(
        exerciseDao.observeAllIncludingArchived(),
        exerciseDao.observeUsage(),
        preferences.preferences,
        filters,
    ) { exercises, usage, prefs, (q, m, mine, custom) ->
        val owned = prefs.equipment + Equipment.BODY_ONLY
        val terms = q.split(' ').map(::normalize).filter { it.isNotEmpty() }
        val filtered = exercises.filter { e ->
            (if (custom) e.isCustom else !e.archived) &&
                (custom || !mine || e.equipment == null || e.equipment in owned) &&
                (m == null || m in e.primaryMuscles || m in e.secondaryMuscles) &&
                terms.all { it in normalize(e.name) }
        }
        val usageById = usage.associateBy { it.exerciseId }
        ExerciseListState(
            loading = exercises.isEmpty(),
            query = q,
            muscle = m,
            myEquipmentOnly = mine,
            customOnly = custom,
            recent = filtered.filter { it.id in usageById }
                .sortedByDescending { usageById.getValue(it.id).lastUsedAt }
                .take(RECENT_COUNT),
            all = filtered,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExerciseListState())

    fun setQuery(text: String) { query.value = text }

    fun setMuscle(value: Muscle?) { muscle.value = value }

    fun setMyEquipmentOnly(value: Boolean) { myEquipmentOnly.value = value }

    fun setCustomOnly(value: Boolean) { customOnly.value = value }

    /** Adds the picked exercises to a workout or a routine, in the order picked. */
    suspend fun addTo(sessionId: String?, routineId: String?, exerciseIds: List<String>) {
        when {
            sessionId != null -> workouts.addExercises(sessionId, exerciseIds)
            routineId != null -> routines.addExercises(routineId, exerciseIds)
        }
    }

    private companion object {
        const val RECENT_COUNT = 8

        /** "Push-Ups" and "pushups" both become "pushups", so either spelling finds it. */
        fun normalize(text: String) = text.lowercase().filter { it.isLetterOrDigit() }
    }
}

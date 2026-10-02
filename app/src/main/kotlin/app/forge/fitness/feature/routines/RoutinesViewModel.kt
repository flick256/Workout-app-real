package app.forge.fitness.feature.routines

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.forge.domain.model.LogType
import app.forge.domain.program.ProgramSchedule
import app.forge.domain.program.TodayPlan
import app.forge.domain.program.WorkoutEstimate
import app.forge.fitness.data.db.ProgramEntity
import app.forge.fitness.data.db.RoutineWithExercises
import app.forge.fitness.data.prefs.UserPreferencesRepository
import app.forge.fitness.data.routine.DayBits
import app.forge.fitness.data.routine.RoutineRepository
import app.forge.fitness.data.workout.WorkoutRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** A routine as shown in lists: its exercises plus a time estimate. */
data class RoutineCardData(val data: RoutineWithExercises, val minutes: Int) {
    val id: String get() = data.routine.id
    val name: String get() = data.routine.name
    val summary: String get() = data.active.joinToString(", ") { it.exercise.name }
}

/** The active program and what it has planned for today. */
data class ProgramPlan(
    val program: ProgramEntity,
    val days: Set<DayOfWeek>,
    val routines: List<RoutineCardData>,
    val plan: TodayPlan,
) {
    val next: RoutineCardData? get() = routines.getOrNull(plan.routineIndex)
}

data class RoutinesState(
    val loading: Boolean = true,
    val routines: List<RoutineCardData> = emptyList(),
    val active: ProgramPlan? = null,
    val defaultRest: Int = 90,
) {
    /** Routines grouped by folder; unfiled ones come first under "My routines". */
    val byFolder: List<Pair<String, List<RoutineCardData>>>
        get() = routines.groupBy { it.data.routine.folder ?: "My routines" }
            .toList()
            .sortedBy { (folder, _) -> if (folder == "My routines") "" else folder }
}

fun estimateMinutes(routine: RoutineWithExercises, defaultRest: Int): Int = WorkoutEstimate.minutes(
    routine.active.map { (item, exercise) ->
        WorkoutEstimate.Slot(
            sets = item.targetSets,
            restSeconds = item.restSeconds ?: defaultRest,
            supersetGroup = item.supersetGroup,
            timedSeconds = item.targetMax.takeIf { exercise.logType == LogType.DURATION },
        )
    },
)

/** Shared by the Routines screen and Today: routines, the active program and its plan. */
@OptIn(ExperimentalCoroutinesApi::class)
fun planFlows(routines: RoutineRepository, preferences: UserPreferencesRepository): Flow<RoutinesState> {
    val activePlan: Flow<Pair<ProgramEntity, Pair<Int?, LocalDate?>>?> = routines.observeActiveProgram().flatMapLatest { program ->
        if (program == null) {
            flowOf(null)
        } else {
            routines.observeLastProgramRun(program.id).map { run ->
                val programRoutines = routines.getProgramRoutines(program.id)
                val lastIndex = run?.let { r -> programRoutines.firstOrNull { it.id == r.routineId }?.programPosition }
                val lastDate = run?.let { Instant.ofEpochMilli(it.startedAt).atZone(ZoneId.systemDefault()).toLocalDate() }
                program to (lastIndex to lastDate)
            }
        }
    }
    return combine(routines.observeRoutines(), activePlan, preferences.preferences) { list, active, prefs ->
        val cards = list.map { RoutineCardData(it, estimateMinutes(it, prefs.defaultRestSeconds)) }
        RoutinesState(
            loading = false,
            routines = cards,
            defaultRest = prefs.defaultRestSeconds,
            active = active?.let { (program, last) ->
                val programCards = cards.filter { it.data.routine.programId == program.id }
                    .sortedBy { it.data.routine.programPosition }
                val days = DayBits.toDays(program.trainingDays)
                ProgramPlan(
                    program = program,
                    days = days,
                    routines = programCards,
                    plan = ProgramSchedule.plan(programCards.size, last.first, last.second, days, LocalDate.now()),
                )
            },
        )
    }
}

sealed interface StartResult {
    data class Started(val sessionId: String) : StartResult
    data object WorkoutInProgress : StartResult
}

@HiltViewModel
class RoutinesViewModel @Inject constructor(
    private val routines: RoutineRepository,
    private val workouts: WorkoutRepository,
    preferences: UserPreferencesRepository,
) : ViewModel() {

    val state: StateFlow<RoutinesState> = planFlows(routines, preferences)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RoutinesState())

    suspend fun create(name: String): String = routines.createRoutine(name)

    fun duplicate(id: String) {
        viewModelScope.launch { routines.duplicate(id) }
    }

    fun delete(id: String) {
        viewModelScope.launch { routines.delete(id) }
    }

    fun restore(id: String) {
        viewModelScope.launch { routines.restore(id) }
    }

    fun setFolder(id: String, folder: String?) {
        viewModelScope.launch { routines.setFolder(id, folder) }
    }

    fun move(id: String, delta: Int) {
        viewModelScope.launch { moveNow(id, delta) }
    }

    private suspend fun moveNow(id: String, delta: Int) {
        val ids = state.value.routines.map { it.id }.toMutableList()
        val from = ids.indexOf(id)
        val to = (from + delta).coerceIn(0, ids.lastIndex)
        if (from < 0 || from == to) return
        ids.add(to, ids.removeAt(from))
        routines.reorderRoutines(ids)
    }

    fun setTrainingDays(programId: String, days: Set<DayOfWeek>) =
        viewModelScope.launch { routines.setTrainingDays(programId, days) }

    fun stopProgram() {
        viewModelScope.launch { routines.setActiveProgram(null) }
    }

    fun activate(programId: String) {
        viewModelScope.launch { routines.setActiveProgram(programId) }
    }

    suspend fun start(routineId: String): StartResult? {
        val routine = routines.getRoutineWithExercises(routineId) ?: return null
        return workouts.startFromRoutine(routine)?.let { StartResult.Started(it) } ?: StartResult.WorkoutInProgress
    }
}

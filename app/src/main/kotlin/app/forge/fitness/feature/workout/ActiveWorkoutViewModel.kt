package app.forge.fitness.feature.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.forge.domain.bodyweight.LoadEstimate
import app.forge.domain.model.Equipment
import app.forge.domain.model.LogType
import app.forge.domain.model.SetType
import app.forge.domain.model.WeightUnit
import app.forge.domain.workout.WarmupCalculator
import app.forge.domain.workout.WorkoutStats
import app.forge.fitness.data.db.ExerciseEntity
import app.forge.fitness.data.db.SessionExerciseEntity
import app.forge.fitness.data.db.SessionExerciseWithExercise
import app.forge.fitness.data.db.SetEntryEntity
import app.forge.fitness.data.db.WorkoutSessionEntity
import app.forge.fitness.data.prefs.UserPreferences
import app.forge.fitness.data.prefs.UserPreferencesRepository
import app.forge.fitness.data.workout.Loads
import app.forge.fitness.data.workout.WorkoutRepository
import app.forge.fitness.timer.RestState
import app.forge.fitness.timer.RestTimer
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One set row, with last time's matching set for the "Previous" column and placeholders. */
data class SetRow(
    val set: SetEntryEntity,
    /** "W", "1", "2", "D", "F": what the left-hand column shows. */
    val label: String,
    val previous: SetEntryEntity?,
)

/** One exercise card in the workout. */
data class ExerciseBlock(
    val item: SessionExerciseEntity,
    val exercise: ExerciseEntity,
    val rows: List<SetRow>,
    /** "A", "B"… when this exercise is part of a superset. */
    val supersetLetter: String?,
    /** Rest only starts after the last exercise of a superset round. */
    val restsAfterSet: Boolean,
    val restSeconds: Int,
    val ownedWeights: List<Double>,
    /** For bodyweight moves: your bodyweight share with nothing added. Null if unknown. */
    val bodyweightLoad: LoadEstimate?,
) {
    val isBodyweight: Boolean get() = exercise.bodyweightProfile != null
}

data class ActiveWorkoutUiState(
    val loading: Boolean = true,
    val session: WorkoutSessionEntity? = null,
    val blocks: List<ExerciseBlock> = emptyList(),
    val unit: WeightUnit = WeightUnit.KG,
    val rest: RestState? = null,
    val heightCm: Double? = null,
) {
    val bodyweightKg: Double? get() = session?.bodyweightKg

    /** Ask for bodyweight when it would change the numbers shown. */
    val needsBodyweight: Boolean get() = bodyweightKg == null && blocks.any { it.isBodyweight }

    val incompleteSets: Int get() = blocks.sumOf { b -> b.rows.count { it.set.completedAt == null } }
    val completedSets: Int get() = blocks.sumOf { b -> b.rows.count { it.set.completedAt != null } }
}

/** One-off messages for the screen (snackbars with undo). */
sealed interface WorkoutEvent {
    data class SetDeleted(val setId: String) : WorkoutEvent
    data class ExerciseRemoved(val sessionExerciseId: String, val name: String) : WorkoutEvent
    data class Message(val text: String) : WorkoutEvent
    data class Finished(val sessionId: String) : WorkoutEvent
    data object Discarded : WorkoutEvent
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ActiveWorkoutViewModel @Inject constructor(
    private val repository: WorkoutRepository,
    preferences: UserPreferencesRepository,
    private val restTimer: RestTimer,
) : ViewModel() {

    private val previousByExercise = MutableStateFlow<Map<String, List<SetEntryEntity>>>(emptyMap())
    private val _events = Channel<WorkoutEvent>(Channel.BUFFERED)
    val events: Flow<WorkoutEvent> = _events.receiveAsFlow()

    /** Wraps the session so "not loaded yet" (null) differs from "no workout" (session = null). */
    private data class Loaded(val session: WorkoutSessionEntity?)

    private val loaded: StateFlow<Loaded?> = repository.observeActiveSession()
        .map { Loaded(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Only the id: renaming the workout shouldn't re-query all its sets. */
    private val sessionId: Flow<String?> = loaded.map { it?.session?.id }.distinctUntilChanged()

    private val currentSession: WorkoutSessionEntity? get() = loaded.value?.session

    private val content: Flow<Pair<List<SessionExerciseWithExercise>, List<SetEntryEntity>>> =
        sessionId.flatMapLatest { id ->
            if (id == null) {
                flowOf(emptyList<SessionExerciseWithExercise>() to emptyList())
            } else {
                combine(repository.observeSessionExercises(id), repository.observeSets(id)) { a, b -> a to b }
            }
        }.shareIn(viewModelScope, SharingStarted.Eagerly, replay = 1)

    val state: StateFlow<ActiveWorkoutUiState> = combine(
        loaded,
        content,
        preferences.preferences,
        previousByExercise,
        restTimer.state,
    ) { l, (exercises, sets), prefs, previous, rest ->
        ActiveWorkoutUiState(
            loading = l == null,
            session = l?.session,
            blocks = buildBlocks(exercises, sets, prefs, previous, l?.session?.bodyweightKg),
            unit = prefs.weightUnit,
            rest = rest,
            heightCm = prefs.heightCm,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ActiveWorkoutUiState())

    init {
        // Load "last time" for any exercise we haven't looked up yet.
        content.onEach { (exercises, _) ->
            val sessionId = currentSession?.id ?: return@onEach
            val missing = exercises.map { it.exercise.id }.filter { it !in previousByExercise.value }.distinct()
            if (missing.isEmpty()) return@onEach
            val loaded = missing.associateWith { repository.previousSets(it, sessionId) }
            previousByExercise.update { it + loaded }
        }.launchIn(viewModelScope)
    }

    private fun buildBlocks(
        exercises: List<SessionExerciseWithExercise>,
        sets: List<SetEntryEntity>,
        prefs: UserPreferences,
        previous: Map<String, List<SetEntryEntity>>,
        bodyweightKg: Double?,
    ): List<ExerciseBlock> {
        val setsByExercise = sets.groupBy { it.sessionExerciseId }
        val letters = exercises.mapNotNull { it.item.supersetGroup }.distinct()
            .withIndex().associate { (i, group) -> group to ('A' + i).toString() }
        return exercises.mapIndexed { index, (item, exercise) ->
            val mySets = setsByExercise[item.id].orEmpty().sortedBy { it.position }
            val matched = WorkoutStats.matchPrevious(mySets.map { it.type }, previous[exercise.id].orEmpty()) { it.type }
            var workNumber = 0
            val rows = mySets.mapIndexed { i, set ->
                val label = if (set.type == SetType.WORKING) (++workNumber).toString() else set.type.short
                SetRow(set, label, matched[i])
            }
            val next = exercises.getOrNull(index + 1)?.item
            val inSuperset = item.supersetGroup != null
            ExerciseBlock(
                item = item,
                exercise = exercise,
                rows = rows,
                supersetLetter = item.supersetGroup?.let { letters[it] },
                restsAfterSet = !inSuperset || next?.supersetGroup != item.supersetGroup,
                restSeconds = item.restSeconds ?: prefs.defaultRestSeconds,
                // Bodyweight moves take added weight from a vest or bag.
                ownedWeights = if (exercise.bodyweightProfile != null) {
                    (prefs.weightsFor(Equipment.WEIGHTED_VEST) + prefs.weightsFor(Equipment.WEIGHTED_BAG)).distinct().sorted()
                } else {
                    prefs.weightsFor(exercise.equipment)
                },
                bodyweightLoad = Loads.baseEstimate(exercise, bodyweightKg, prefs.heightCm),
            )
        }
    }

    // ---- Set editing (each change is saved immediately) --------------------------------

    /** Changing the weight of a ticked-off set updates its load too. */
    fun setWeight(block: ExerciseBlock, setId: String, kg: Double?) = launch {
        repository.patchSet(setId) { s ->
            val changed = s.copy(weightKg = kg)
            if (changed.completedAt != null) changed.copy(loadKg = loadFor(changed, block)) else changed
        }
    }

    private fun loadFor(set: SetEntryEntity, block: ExerciseBlock): Double? {
        val st = state.value
        return Loads.loadFor(set, block.exercise, st.bodyweightKg, st.heightCm)
    }

    /** Sets bodyweight for this workout; bodyweight sets already done are recalculated. */
    fun setBodyweight(kg: Double) = launch {
        val id = currentSession?.id ?: return@launch
        repository.setSessionBodyweight(id, kg, state.value.heightCm)
    }

    fun setReps(setId: String, reps: Int?) = launch { repository.patchSet(setId) { it.copy(reps = reps) } }

    fun setDuration(setId: String, seconds: Int?) =
        launch { repository.patchSet(setId) { it.copy(durationSeconds = seconds) } }

    fun setDistance(setId: String, meters: Double?) =
        launch { repository.patchSet(setId) { it.copy(distanceMeters = meters) } }

    fun setRpe(setId: String, rpe: Double?) = launch { repository.patchSet(setId) { it.copy(rpe = rpe) } }

    fun setType(setId: String, type: SetType) = launch { repository.patchSet(setId) { it.copy(type = type) } }

    /** Quick-pick chip: puts a weight into the first set that isn't ticked off yet. */
    fun fillNextWeight(block: ExerciseBlock, kg: Double) {
        val target = block.rows.firstOrNull { it.set.completedAt == null } ?: return
        setWeight(block, target.set.id, kg)
    }

    /**
     * Ticks a set off (or un-ticks it). Empty fields are filled from last time, so a
     * repeat of last session is a single tap per set. Starts the rest timer.
     */
    fun toggleComplete(block: ExerciseBlock, row: SetRow) = launch {
        if (row.set.completedAt != null) {
            repository.patchSet(row.set.id) { it.copy(completedAt = null, loadKg = null) }
            return@launch
        }
        val prev = row.previous
        val filled = repository.patchSet(row.set.id) { s ->
            s.copy(
                weightKg = s.weightKg ?: prev?.weightKg,
                reps = s.reps ?: prev?.reps,
                durationSeconds = s.durationSeconds ?: prev?.durationSeconds,
                distanceMeters = s.distanceMeters ?: prev?.distanceMeters,
            )
        } ?: return@launch
        val missing = when (block.exercise.logType) {
            LogType.WEIGHT_REPS, LogType.REPS -> filled.reps == null
            LogType.DURATION -> filled.durationSeconds == null
            LogType.DISTANCE_DURATION -> filled.durationSeconds == null && filled.distanceMeters == null
        }
        if (missing) {
            emit(WorkoutEvent.Message(if (block.exercise.logType.usesReps()) "Enter reps first" else "Enter a time first"))
            return@launch
        }
        repository.completeSet(filled.copy(loadKg = loadFor(filled, block)))
        if (block.restsAfterSet) startRest(block, row)
    }

    private fun startRest(block: ExerciseBlock, row: SetRow) {
        val seconds = if (row.set.type == SetType.WARMUP) minOf(block.restSeconds, WARMUP_REST) else block.restSeconds
        val upNext = nextUp(block, row)
        restTimer.start(seconds, upNext)
    }

    /** "Next: Push-ups, set 3" for the timer and its notification. */
    private fun nextUp(block: ExerciseBlock, row: SetRow): String {
        val blocks = state.value.blocks
        val after = block.rows.dropWhile { it.set.id != row.set.id }.drop(1)
            .firstOrNull { it.set.completedAt == null }
        if (after != null) {
            val first = blocks.firstOrNull { it.item.supersetGroup != null && it.item.supersetGroup == block.item.supersetGroup }
            val name = first?.exercise?.name ?: block.exercise.name
            return "Next: $name, set ${after.label}"
        }
        val nextBlock = blocks.dropWhile { it.item.id != block.item.id }.drop(1).firstOrNull()
        return nextBlock?.let { "Next: ${it.exercise.name}" } ?: "Last set done. Nice work."
    }

    fun addSet(block: ExerciseBlock) = launch { repository.addSet(block.item.id) }

    fun deleteSet(setId: String) = launch {
        repository.deleteSet(setId)
        emit(WorkoutEvent.SetDeleted(setId))
    }

    fun restoreSet(setId: String) = launch { repository.restoreSet(setId) }

    /** Adds a warm-up ramp based on the first working set's weight (or last time's). */
    fun addWarmups(block: ExerciseBlock) = launch {
        val firstWork = block.rows.firstOrNull { it.set.type != SetType.WARMUP }
        val working = firstWork?.set?.weightKg ?: firstWork?.previous?.weightKg
        if (working == null || working <= 0) {
            emit(WorkoutEvent.Message("Enter your working weight first"))
            return@launch
        }
        val plan = WarmupCalculator.plan(working, ownedWeights = block.ownedWeights)
        if (plan.isEmpty()) {
            emit(WorkoutEvent.Message("Too light to need a warm-up"))
        } else {
            repository.addWarmups(block.item.id, plan)
        }
    }

    // ---- Exercises --------------------------------------------------------------------

    fun removeExercise(block: ExerciseBlock) = launch {
        repository.removeExercise(block.item.id)
        emit(WorkoutEvent.ExerciseRemoved(block.item.id, block.exercise.name))
    }

    fun restoreExercise(sessionExerciseId: String) = launch { repository.restoreExercise(sessionExerciseId) }

    fun move(block: ExerciseBlock, delta: Int) = launch { repository.moveExercise(block.item.id, delta) }

    fun supersetWithNext(block: ExerciseBlock) = launch { repository.supersetWithNext(block.item.id) }

    fun leaveSuperset(block: ExerciseBlock) = launch { repository.leaveSuperset(block.item.id) }

    fun setExerciseNotes(block: ExerciseBlock, notes: String) =
        launch { repository.setExerciseNotes(block.item.id, notes) }

    fun setExerciseRest(block: ExerciseBlock, seconds: Int) =
        launch { repository.setExerciseRest(block.item.id, seconds) }

    // ---- Session ------------------------------------------------------------------------

    fun rename(name: String) = launch { currentSession?.let { repository.renameSession(it.id, name) } }

    fun setNotes(notes: String) = launch { currentSession?.let { repository.setSessionNotes(it.id, notes) } }

    fun finish() = launch {
        val id = currentSession?.id ?: return@launch
        restTimer.stop()
        repository.finish(id)
        emit(WorkoutEvent.Finished(id))
    }

    fun discard() = launch {
        val id = currentSession?.id ?: return@launch
        restTimer.stop()
        repository.discard(id)
        emit(WorkoutEvent.Discarded)
    }

    // ---- Rest timer -------------------------------------------------------------------

    fun adjustRest(seconds: Int) = restTimer.adjust(seconds)

    fun skipRest() = restTimer.stop()

    // ---- Events ---------------------------------------------------------------------------

    private fun emit(event: WorkoutEvent) {
        _events.trySend(event)
    }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    private fun LogType.usesReps() = this == LogType.WEIGHT_REPS || this == LogType.REPS

    private companion object {
        const val WARMUP_REST = 60
    }
}

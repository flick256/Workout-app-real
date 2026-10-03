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
import app.forge.domain.model.BodyMetricKind
import app.forge.fitness.data.db.BodyMetricDao
import app.forge.fitness.data.db.ExerciseDao
import app.forge.fitness.data.db.ExerciseEntity
import app.forge.fitness.data.db.SessionExerciseEntity
import app.forge.fitness.data.db.SessionExerciseWithExercise
import app.forge.fitness.data.db.SetEntryEntity
import app.forge.fitness.data.db.WorkoutSessionEntity
import app.forge.fitness.data.prefs.UserPreferences
import app.forge.fitness.data.prefs.UserPreferencesRepository
import app.forge.domain.suggest.Suggestion
import app.forge.domain.suggest.SuggestionKind
import app.forge.fitness.data.suggest.SuggestionRepository
import app.forge.fitness.data.workout.Loads
import app.forge.fitness.data.workout.WorkoutRepository
import app.forge.fitness.timer.RestState
import app.forge.fitness.timer.RestTimer
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import app.forge.domain.parse.ExerciseMatcher
import app.forge.domain.parse.SetCommand
import app.forge.domain.parse.SetCommandParser
import app.forge.fitness.data.ai.AiAssistant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
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
    /** What to aim for today, from your history (null while loading or for cardio). */
    val suggestion: Suggestion? = null,
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
    /** This workout's bodyweight, or your latest logged one if it has none yet. */
    val effectiveBodyweightKg: Double? = null,
) {
    val bodyweightKg: Double? get() = effectiveBodyweightKg

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
    bodyMetrics: BodyMetricDao,
    private val exercises: ExerciseDao,
    private val suggestions: SuggestionRepository,
    private val preferences: UserPreferencesRepository,
    private val restTimer: RestTimer,
    private val assistant: AiAssistant,
    private val health: app.forge.fitness.data.health.HealthConnectManager,
    heartRate: app.forge.fitness.heart.HeartRateMonitor,
) : ViewModel() {

    /** Live heart rate from your strap, if one is connected. */
    val strap: StateFlow<app.forge.fitness.heart.StrapState> = heartRate.state

    /** Estimated max heart rate for zones (from your birth year, if set). */
    var maxHr: Int = app.forge.domain.heart.HeartRateMath.maxHr(null)
        private set

    init {
        viewModelScope.launch {
            preferences.preferences.collect { p ->
                maxHr = app.forge.domain.heart.HeartRateMath.maxHr(p.birthYear?.let { java.time.LocalDate.now().year - it })
            }
        }
    }

    /** Suggestion per session exercise, keyed by "sessionExerciseId:exerciseId" (swaps get a fresh one). */
    private val suggestionByItem = MutableStateFlow<Map<String, Suggestion?>>(emptyMap())

    /** Suggestions you've applied (or dismissed) in this workout, so they stop showing. */
    private val appliedSuggestions = MutableStateFlow<Set<String>>(emptySet())

    private fun suggestionKey(item: SessionExerciseEntity) = "${item.id}:${item.exerciseId}"

    /** Your latest logged bodyweight (Settings → Body or "Bodyweight today"). */
    private val latestBodyweight: Flow<Double?> = bodyMetrics.observeLatest(BodyMetricKind.WEIGHT).map { it?.value }

    private val prefsAndBodyweight = combine(preferences.preferences, latestBodyweight) { p, bw -> p to bw }

    private val previousByExercise = MutableStateFlow<Map<String, List<SetEntryEntity>>>(emptyMap())

    // Declared after everything it reads: Kotlin initialises properties top to bottom.
    private val historyHints = combine(previousByExercise, suggestionByItem, appliedSuggestions) { p, s, a -> Triple(p, s, a) }

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
        prefsAndBodyweight,
        historyHints,
        restTimer.state,
    ) { l, (exercises, sets), (prefs, latestBw), (previous, suggested, applied), rest ->
        // The workout's own snapshot wins; otherwise use your latest logged weight.
        val bodyweight = l?.session?.bodyweightKg ?: latestBw
        ActiveWorkoutUiState(
            loading = l == null,
            session = l?.session,
            blocks = buildBlocks(exercises, sets, prefs, previous, bodyweight).map { block ->
                val key = suggestionKey(block.item)
                if (key in applied) block else block.copy(suggestion = suggested[key])
            },
            unit = prefs.weightUnit,
            rest = rest,
            heightCm = prefs.heightCm,
            effectiveBodyweightKg = bodyweight,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ActiveWorkoutUiState())

    init {
        // A workout started before you entered your bodyweight picks it up automatically.
        combine(loaded, prefsAndBodyweight) { l, (prefs, latestBw) -> Triple(l?.session, latestBw, prefs.heightCm) }
            .onEach { (session, latestBw, height) ->
                if (session != null && session.bodyweightKg == null && latestBw != null) {
                    repository.adoptBodyweightIfMissing(session.id, latestBw, height)
                }
            }
            .launchIn(viewModelScope)

        // Load "last time" for any exercise we haven't looked up yet.
        content.onEach { (exercises, _) ->
            val sessionId = currentSession?.id ?: return@onEach
            val missing = exercises.map { it.exercise.id }.filter { it !in previousByExercise.value }.distinct()
            if (missing.isEmpty()) return@onEach
            val loaded = missing.associateWith { repository.previousSets(it, sessionId) }
            previousByExercise.update { it + loaded }
        }.launchIn(viewModelScope)

        // Work out a suggestion for each exercise we haven't looked at yet.
        content.onEach { (items, _) ->
            val prefs = preferences.preferences.first()
            items.filter { suggestionKey(it.item) !in suggestionByItem.value }.forEach { (item, exercise) ->
                val suggestion = suggestions.suggestionFor(exercise, item.targetMin, item.targetMax, item.targetRpe, prefs)
                suggestionByItem.update { it + (suggestionKey(item) to suggestion) }
            }
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
        repository.completeSetById(filled.id) { loadFor(it, block) }
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

    /**
     * Uses the suggestion: fills the weight and reps (or time) of the sets you haven't
     * ticked yet, or switches to the harder variation.
     */
    fun applySuggestion(block: ExerciseBlock) = launch {
        val s = block.suggestion ?: return@launch
        appliedSuggestions.update { it + suggestionKey(block.item) }
        if (s.kind == SuggestionKind.HARDER_VARIATION) {
            swapVariation(block, +1)
            return@launch
        }
        block.rows.filter { it.set.completedAt == null && it.set.type != SetType.WARMUP }.forEach { row ->
            repository.patchSet(row.set.id) { set ->
                set.copy(
                    weightKg = s.weightKg ?: set.weightKg,
                    reps = s.reps ?: set.reps,
                    durationSeconds = s.seconds ?: set.durationSeconds,
                )
            }
        }
    }

    fun dismissSuggestion(block: ExerciseBlock) = appliedSuggestions.update { it + suggestionKey(block.item) }

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

    /**
     * Swaps to the previous (-1) or next (+1) step of the exercise's progression ladder,
     * e.g. push-up → diamond push-up. Only before any set of it is ticked off.
     */
    fun swapVariation(block: ExerciseBlock, direction: Int) = launch {
        val chain = block.exercise.progressionChain ?: return@launch
        val step = block.exercise.progressionStep ?: return@launch
        if (block.rows.any { it.set.completedAt != null }) {
            emit(WorkoutEvent.Message("You've started this one. Add the other variation as a new exercise instead."))
            return@launch
        }
        val target = exercises.getChainStep(chain, step + direction)
        if (target == null) {
            emit(WorkoutEvent.Message(if (direction > 0) "That's the hardest variation" else "That's the easiest variation"))
            return@launch
        }
        repository.swapExercise(block.item.id, target.id)
        emit(WorkoutEvent.Message("Switched to ${target.name}"))
    }

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
        // Share it with Health Connect (if you've allowed Forge to write there).
        health.exportWorkoutInBackground(id)
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

    /** What a quick-log line was understood as, to confirm before saving. */
    data class QuickLogPreview(val command: SetCommand, val exercise: ExerciseEntity, val byAi: Boolean)

    /**
     * Understands "3x8 bench at 60" with Forge's own parser; if that can't read it and the
     * on-device AI is installed, asks the AI. Either way you confirm before anything saves.
     */
    suspend fun interpretQuickLog(text: String): QuickLogPreview? {
        val inWorkout = state.value.blocks.map { it.exercise.id }.toSet()
        val all = exercises.observeAll().first()
        fun match(cmd: SetCommand) = ExerciseMatcher.best(cmd.exerciseQuery, all, { it.name }, { it.id in inWorkout })
        SetCommandParser.parse(text)?.let { cmd -> match(cmd)?.let { return QuickLogPreview(cmd, it, byAi = false) } }
        val ai = assistant.parseSet(text) ?: return null
        return match(ai)?.let { QuickLogPreview(ai, it, byAi = true) }
    }

    fun confirmQuickLog(preview: QuickLogPreview) = launch {
        val id = currentSession?.id ?: return@launch
        val c = preview.command
        val n = repository.logSets(id, preview.exercise, c.sets, c.weightKg, c.reps, c.seconds, c.rpe, state.value.heightCm)
        emit(WorkoutEvent.Message("Logged $n set${if (n == 1) "" else "s"} of ${preview.exercise.name}"))
    }

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

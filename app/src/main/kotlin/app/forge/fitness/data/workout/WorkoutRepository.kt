package app.forge.fitness.data.workout

import androidx.room.withTransaction
import app.forge.domain.model.BodyMetricKind
import app.forge.domain.model.SessionStatus
import app.forge.domain.model.SetType
import app.forge.domain.heart.HeartRateMath
import app.forge.domain.heart.HrSample
import app.forge.domain.workout.LoggedSet
import app.forge.domain.workout.WarmupSet
import app.forge.domain.workout.WorkoutStats
import app.forge.domain.workout.WorkoutSummary
import app.forge.fitness.data.db.BodyMetricDao
import app.forge.fitness.data.db.BodyMetricEntity
import app.forge.fitness.data.db.ForgeDatabase
import app.forge.fitness.data.db.RoutineWithExercises
import app.forge.fitness.data.db.SessionExerciseEntity
import app.forge.fitness.data.db.ExerciseEntity
import app.forge.fitness.data.db.SetEntryEntity
import app.forge.fitness.data.db.WorkoutDao
import app.forge.fitness.data.db.WorkoutSessionEntity
import app.forge.fitness.di.TimeSource
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

/**
 * Every change to a workout goes through here and is written to the database
 * immediately. There is no "save" step, so if the app is killed mid-workout nothing
 * is lost: the session is still ACTIVE and reopens on next launch.
 */
@Singleton
class WorkoutRepository @Inject constructor(
    private val db: ForgeDatabase,
    private val dao: WorkoutDao,
    private val bodyMetrics: BodyMetricDao,
    private val time: TimeSource,
) {
    fun observeActiveSession(): Flow<WorkoutSessionEntity?> = dao.observeActiveSession()

    fun observeSession(id: String) = dao.observeSession(id)

    fun observeSessionExercises(sessionId: String) = dao.observeSessionExercises(sessionId)

    fun observeSets(sessionId: String) = dao.observeSets(sessionId)

    fun observeHistory() = dao.observeHistory()

    fun observeHistoryLines() = dao.observeHistoryLines()

    suspend fun previousSets(exerciseId: String, excludeSessionId: String) =
        dao.previousSets(exerciseId, excludeSessionId)

    // ---- Session lifecycle ------------------------------------------------------------

    /** Starts an empty workout, or returns the one already in progress (never two at once). */
    /** True once anything has been logged (used to skip first-run setup on upgrade). */
    suspend fun hasAnyWorkouts(): Boolean = dao.countSessions() > 0

    suspend fun startOrResume(name: String? = null): String = db.withTransaction {
        val now = time.now()
        dao.getActiveSessionNow()?.id ?: newId().also { id ->
            dao.insertSession(
                WorkoutSessionEntity(
                    id = id,
                    name = name ?: defaultName(now),
                    routineId = null,
                    startedAt = now,
                    endedAt = null,
                    status = SessionStatus.ACTIVE,
                    notes = null,
                    // Snapshot today's bodyweight so this workout's loads never change later.
                    bodyweightKg = bodyMetrics.latest(BodyMetricKind.WEIGHT)?.value,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
        }
    }

    suspend fun renameSession(sessionId: String, name: String) = editSession(sessionId) {
        it.copy(name = name.trim().ifEmpty { it.name })
    }

    suspend fun setSessionNotes(sessionId: String, notes: String) = editSession(sessionId) {
        it.copy(notes = notes.ifBlank { null })
    }

    /**
     * Finishes the workout. Sets you never ticked off, and exercises left with no
     * completed sets, are removed (soft-deleted, so they're still in backups).
     */
    suspend fun finish(sessionId: String): WorkoutSummary = db.withTransaction {
        val now = time.now()
        val exercises = dao.getSessionExercises(sessionId)
        val allSets = dao.getSetsForSession(sessionId)
        val (done, notDone) = allSets.partition { it.completedAt != null }
        dao.updateSets(notDone.map { it.copy(deletedAt = now, updatedAt = now) })
        val withWork = done.map { it.sessionExerciseId }.toSet()
        dao.updateSessionExercises(
            exercises.filter { it.id !in withWork }.map { it.copy(deletedAt = now, updatedAt = now) },
        )
        editSession(sessionId) { it.copy(status = SessionStatus.FINISHED, endedAt = now) }
        // Live heart rate from your strap becomes the workout's average and peak.
        val heart = db.heartRateDao().forSession(sessionId)
        HeartRateMath.summarize(heart.map { HrSample(it.atMillis, it.bpm) }, HeartRateMath.maxHr(null))?.let {
            dao.setHeartRate(sessionId, it.avg, it.max, now)
        }
        WorkoutStats.summarize(done.map { LoggedSet(it.type, it.loadKg ?: it.weightKg, it.reps, it.durationSeconds) })
    }

    /**
     * Sets the bodyweight for this workout (and logs it as today's weight), then
     * recalculates the load of every completed bodyweight set in it.
     */
    suspend fun setSessionBodyweight(sessionId: String, kg: Double, heightCm: Double?) = db.withTransaction {
        logBodyweight(kg)
        applyBodyweight(sessionId, kg, heightCm)
    }

    /**
     * A workout started before you'd entered your bodyweight has none recorded. This
     * gives it your latest logged weight (without logging a new entry) and works out
     * the loads of any bodyweight sets you've already ticked off. Does nothing if the
     * workout already has a bodyweight.
     */
    suspend fun adoptBodyweightIfMissing(sessionId: String, kg: Double, heightCm: Double?) = db.withTransaction {
        if (dao.getSession(sessionId)?.bodyweightKg == null) applyBodyweight(sessionId, kg, heightCm)
    }

    private suspend fun applyBodyweight(sessionId: String, kg: Double, heightCm: Double?) {
        editSession(sessionId) { it.copy(bodyweightKg = kg) }
        val now = time.now()
        val exercises = dao.getSessionExercisesWithExercise(sessionId).associateBy { it.item.id }
        val updated = dao.getSetsForSession(sessionId)
            .filter { it.completedAt != null }
            .mapNotNull { set ->
                val exercise = exercises[set.sessionExerciseId]?.exercise ?: return@mapNotNull null
                if (Loads.profileOf(exercise) == null) return@mapNotNull null
                set.copy(loadKg = Loads.loadFor(set, exercise, kg, heightCm), updatedAt = now)
            }
        dao.updateSets(updated)
    }

    /** Records a bodyweight measurement for today. */
    suspend fun logBodyweight(kg: Double) {
        val now = time.now()
        bodyMetrics.insert(
            BodyMetricEntity(
                id = newId(),
                kind = BodyMetricKind.WEIGHT,
                value = kg,
                measuredAt = now,
                createdAt = now,
                updatedAt = now,
            ),
        )
    }

    suspend fun discard(sessionId: String) = editSession(sessionId) {
        it.copy(status = SessionStatus.DISCARDED, endedAt = time.now())
    }

    /** Soft delete from History. [restoreSession] undoes it. */
    suspend fun deleteSession(sessionId: String) = editSession(sessionId) { it.copy(deletedAt = time.now()) }

    suspend fun restoreSession(sessionId: String) = editSession(sessionId) { it.copy(deletedAt = null) }

    // ---- Exercises in a session ---------------------------------------------------------

    /**
     * Adds exercises at the end. Each gets as many empty set rows as you did work sets
     * last time (3 if it's new), so "last time" numbers line up row by row.
     */
    suspend fun addExercises(sessionId: String, exerciseIds: List<String>) = db.withTransaction {
        val now = time.now()
        var position = (dao.getSessionExercises(sessionId).maxOfOrNull { it.position } ?: -1) + 1
        exerciseIds.forEach { exerciseId -> insertExercise(sessionId, exerciseId, position++, now) }
    }

    /**
     * Starts a workout from a routine: its exercises, order, supersets, rest times and
     * targets, with one row per target set (plus last time's warm-ups). Returns null if
     * another workout is already in progress.
     */
    suspend fun startFromRoutine(routine: RoutineWithExercises): String? = startPlanned(
        name = routine.routine.name,
        routineId = routine.routine.id,
        items = routine.active.map { (item, exercise) ->
            PlannedExercise(
                exerciseId = exercise.id,
                sets = item.targetSets,
                targetMin = item.targetMin,
                targetMax = item.targetMax,
                targetRpe = item.targetRpe,
                restSeconds = item.restSeconds,
                supersetGroup = item.supersetGroup,
                notes = item.notes,
            )
        },
    )

    /**
     * Starts a workout with these exercises and targets (from a routine or a generated
     * quick workout). Returns null if another workout is already in progress.
     */
    suspend fun startPlanned(name: String, items: List<PlannedExercise>, routineId: String? = null): String? =
        db.withTransaction {
            if (dao.getActiveSessionNow() != null) return@withTransaction null
            val now = time.now()
            val id = newId()
            dao.insertSession(
                WorkoutSessionEntity(
                    id = id,
                    name = name,
                    routineId = routineId,
                    startedAt = now,
                    endedAt = null,
                    status = SessionStatus.ACTIVE,
                    notes = null,
                    bodyweightKg = bodyMetrics.latest(BodyMetricKind.WEIGHT)?.value,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
            items.forEachIndexed { i, p ->
                insertExercise(
                    sessionId = id,
                    exerciseId = p.exerciseId,
                    position = i,
                    now = now,
                    template = SessionExerciseEntity(
                        id = "", sessionId = id, exerciseId = p.exerciseId, position = i,
                        supersetGroup = p.supersetGroup, notes = p.notes, restSeconds = p.restSeconds,
                        createdAt = now, updatedAt = now,
                        targetSets = p.sets, targetMin = p.targetMin, targetMax = p.targetMax, targetRpe = p.targetRpe,
                    ),
                )
            }
            id
        }

    /**
     * Adds one exercise with its set rows. Without a routine target it gets as many rows
     * as you did work sets last time (3 if it's new), so "last time" lines up row by row.
     */
    private suspend fun insertExercise(
        sessionId: String,
        exerciseId: String,
        position: Int,
        now: Long,
        template: SessionExerciseEntity? = null,
    ) {
        val item = (template ?: SessionExerciseEntity(
            id = "", sessionId = sessionId, exerciseId = exerciseId, position = position,
            supersetGroup = null, notes = null, restSeconds = null, createdAt = now, updatedAt = now,
        )).copy(id = newId())
        dao.insertSessionExercises(listOf(item))
        val previous = dao.previousSets(exerciseId, sessionId)
        val warmups = previous.count { it.type == SetType.WARMUP }
        val work = item.targetSets
            ?: previous.count { it.type != SetType.WARMUP }.takeIf { it > 0 }
            ?: DEFAULT_SETS
        val types = List(warmups) { SetType.WARMUP } + List(work) { SetType.WORKING }
        dao.insertSets(types.mapIndexed { index, type -> emptySet(item.id, index, type, now) })
    }

    suspend fun removeExercise(sessionExerciseId: String) =
        editSessionExercise(sessionExerciseId) { it.copy(deletedAt = time.now(), supersetGroup = null) }
            .also { it?.let { removed -> tidySupersets(removed.sessionId) } }

    suspend fun restoreExercise(sessionExerciseId: String) =
        editSessionExercise(sessionExerciseId) { it.copy(deletedAt = null) }

    /** Replaces the exercise (e.g. an easier or harder variation), keeping sets and targets. */
    suspend fun swapExercise(sessionExerciseId: String, newExerciseId: String) =
        editSessionExercise(sessionExerciseId) { it.copy(exerciseId = newExerciseId) }

    suspend fun setExerciseNotes(sessionExerciseId: String, notes: String) =
        editSessionExercise(sessionExerciseId) { it.copy(notes = notes.ifBlank { null }) }

    suspend fun setExerciseRest(sessionExerciseId: String, seconds: Int?) =
        editSessionExercise(sessionExerciseId) { it.copy(restSeconds = seconds) }

    /** Moves an exercise up (-1) or down (+1) in the list. */
    suspend fun moveExercise(sessionExerciseId: String, delta: Int) = db.withTransaction {
        val item = dao.getSessionExercise(sessionExerciseId) ?: return@withTransaction
        val list = dao.getSessionExercises(item.sessionId).toMutableList()
        val from = list.indexOfFirst { it.id == sessionExerciseId }
        val to = (from + delta).coerceIn(0, list.lastIndex)
        if (from == to) return@withTransaction
        list.add(to, list.removeAt(from))
        renumber(list)
        tidySupersets(item.sessionId)
    }

    /**
     * Links an exercise with the one below it into a superset (or adds the one below to
     * this exercise's existing superset).
     */
    suspend fun supersetWithNext(sessionExerciseId: String) = db.withTransaction {
        val item = dao.getSessionExercise(sessionExerciseId) ?: return@withTransaction
        val list = dao.getSessionExercises(item.sessionId)
        val index = list.indexOfFirst { it.id == sessionExerciseId }
        val next = list.getOrNull(index + 1) ?: return@withTransaction
        val group = item.supersetGroup ?: next.supersetGroup
            ?: ((list.mapNotNull { it.supersetGroup }.maxOrNull() ?: 0) + 1)
        val now = time.now()
        dao.updateSessionExercises(
            listOf(item, next).map { it.copy(supersetGroup = group, updatedAt = now) },
        )
    }

    suspend fun leaveSuperset(sessionExerciseId: String) = db.withTransaction {
        val item = editSessionExercise(sessionExerciseId) { it.copy(supersetGroup = null) }
        item?.let { tidySupersets(it.sessionId) }
    }

    // ---- Sets -------------------------------------------------------------------------

    /** Adds a set, pre-filled with the values of the last set in that exercise. */
    suspend fun addSet(sessionExerciseId: String, type: SetType = SetType.WORKING) = db.withTransaction {
        val now = time.now()
        val sets = dao.getSets(sessionExerciseId)
        val template = sets.lastOrNull { it.type == type } ?: sets.lastOrNull()
        dao.insertSets(
            listOf(
                emptySet(sessionExerciseId, (sets.maxOfOrNull { it.position } ?: -1) + 1, type, now).copy(
                    weightKg = template?.weightKg,
                    reps = template?.reps,
                    durationSeconds = template?.durationSeconds,
                    distanceMeters = template?.distanceMeters,
                ),
            ),
        )
    }

    /**
     * Quick log ("3x8 bench at 60"): fills the exercise's next empty work sets (adding the
     * exercise and extra rows if needed) and ticks them off. Returns how many were logged.
     */
    suspend fun logSets(
        sessionId: String,
        exercise: ExerciseEntity,
        count: Int,
        weightKg: Double?,
        reps: Int?,
        seconds: Int?,
        rpe: Double?,
        heightCm: Double?,
    ): Int = db.withTransaction {
        val now = time.now()
        val item = dao.getSessionExercises(sessionId).firstOrNull { it.exerciseId == exercise.id } ?: run {
            val position = (dao.getSessionExercises(sessionId).maxOfOrNull { it.position } ?: -1) + 1
            insertExercise(sessionId, exercise.id, position, now)
            dao.getSessionExercises(sessionId).first { it.exerciseId == exercise.id }
        }
        val bodyweight = dao.getSession(sessionId)?.bodyweightKg
        val all = dao.getSets(item.id)
        val open = all.filter { it.completedAt == null && it.type != SetType.WARMUP }
        var position = (all.maxOfOrNull { it.position } ?: -1) + 1
        val updated = mutableListOf<SetEntryEntity>()
        val added = mutableListOf<SetEntryEntity>()
        repeat(count) { i ->
            val existing = open.getOrNull(i)
            val base = existing ?: emptySet(item.id, position++, SetType.WORKING, now)
            val filled = base.copy(
                weightKg = weightKg ?: base.weightKg,
                reps = reps ?: base.reps,
                durationSeconds = seconds ?: base.durationSeconds,
                rpe = rpe ?: base.rpe,
                completedAt = now + i,
                updatedAt = now,
            )
            val withLoad = filled.copy(loadKg = Loads.loadFor(filled, exercise, bodyweight, heightCm))
            if (existing != null) updated += withLoad else added += withLoad
        }
        if (updated.isNotEmpty()) dao.updateSets(updated)
        if (added.isNotEmpty()) dao.insertSets(added)
        count
    }

    /** Inserts warm-up sets before the first working set. */
    suspend fun addWarmups(sessionExerciseId: String, plan: List<WarmupSet>) = db.withTransaction {
        if (plan.isEmpty()) return@withTransaction
        val now = time.now()
        val existing = dao.getSets(sessionExerciseId)
        // Replace empty warm-up rows rather than stacking more on top of them.
        val (emptyWarmups, keep) = existing.partition { it.type == SetType.WARMUP && it.completedAt == null }
        dao.updateSets(emptyWarmups.map { it.copy(deletedAt = now, updatedAt = now) })
        val warmups = plan.map { w ->
            emptySet(sessionExerciseId, 0, SetType.WARMUP, now).copy(weightKg = w.weightKg, reps = w.reps)
        }
        val doneWarmups = keep.filter { it.type == SetType.WARMUP }
        val rest = keep.filter { it.type != SetType.WARMUP }
        dao.insertSets(warmups)
        val ordered = doneWarmups + warmups + rest
        dao.updateSets(ordered.mapIndexed { index, set -> set.copy(position = index, updatedAt = now) })
    }

    suspend fun updateSet(set: SetEntryEntity) = dao.updateSets(listOf(set.copy(updatedAt = time.now())))

    /**
     * Changes one set based on its *current* saved values. The UI uses this for every
     * keystroke, so typing weight then reps quickly can never overwrite one with a stale
     * copy of the other.
     */
    suspend fun patchSet(setId: String, change: (SetEntryEntity) -> SetEntryEntity): SetEntryEntity? =
        db.withTransaction {
            val current = dao.getSet(setId) ?: return@withTransaction null
            change(current).copy(updatedAt = time.now()).also { dao.updateSets(listOf(it)) }
        }

    /** Ticks a set off with the given values. Returns the saved set. */
    suspend fun completeSet(set: SetEntryEntity): SetEntryEntity {
        val now = time.now()
        val done = set.copy(completedAt = now, updatedAt = now)
        dao.updateSets(listOf(done))
        return done
    }

    /**
     * Ticks a set off using its latest saved values (not a copy held by the screen, which
     * could miss a number typed a moment ago). [load] works out the set's total load.
     */
    suspend fun completeSetById(setId: String, load: (SetEntryEntity) -> Double?): SetEntryEntity? =
        patchSet(setId) { it.copy(completedAt = time.now(), loadKg = load(it)) }

    suspend fun uncompleteSet(set: SetEntryEntity) =
        dao.updateSets(listOf(set.copy(completedAt = null, updatedAt = time.now())))

    suspend fun deleteSet(setId: String) = editSet(setId) { it.copy(deletedAt = time.now()) }

    suspend fun restoreSet(setId: String) = editSet(setId) { it.copy(deletedAt = null) }

    // ---- Helpers ----------------------------------------------------------------------

    private suspend fun editSession(id: String, change: (WorkoutSessionEntity) -> WorkoutSessionEntity) {
        val current = dao.getSession(id) ?: return
        dao.updateSession(change(current).copy(updatedAt = time.now()))
    }

    private suspend fun editSessionExercise(
        id: String,
        change: (SessionExerciseEntity) -> SessionExerciseEntity,
    ): SessionExerciseEntity? {
        val current = dao.getSessionExercise(id) ?: return null
        return change(current).copy(updatedAt = time.now()).also { dao.updateSessionExercises(listOf(it)) }
    }

    private suspend fun editSet(id: String, change: (SetEntryEntity) -> SetEntryEntity) {
        val current = dao.getSet(id) ?: return
        dao.updateSets(listOf(change(current).copy(updatedAt = time.now())))
    }

    private suspend fun renumber(list: List<SessionExerciseEntity>) {
        val now = time.now()
        dao.updateSessionExercises(
            list.mapIndexedNotNull { index, item ->
                if (item.position == index) null else item.copy(position = index, updatedAt = now)
            },
        )
    }

    /**
     * Keeps supersets valid after moves and removals: a superset is only real if its
     * exercises are next to each other, so lone or separated members are unlinked.
     */
    private suspend fun tidySupersets(sessionId: String) {
        val list = dao.getSessionExercises(sessionId)
        val keep = mutableSetOf<String>()
        list.forEachIndexed { i, item ->
            val group = item.supersetGroup ?: return@forEachIndexed
            val neighbourInGroup = list.getOrNull(i - 1)?.supersetGroup == group ||
                list.getOrNull(i + 1)?.supersetGroup == group
            if (neighbourInGroup) keep += item.id
        }
        val now = time.now()
        dao.updateSessionExercises(
            list.filter { it.supersetGroup != null && it.id !in keep }
                .map { it.copy(supersetGroup = null, updatedAt = now) },
        )
    }

    private fun emptySet(sessionExerciseId: String, position: Int, type: SetType, now: Long) = SetEntryEntity(
        id = newId(),
        sessionExerciseId = sessionExerciseId,
        position = position,
        type = type,
        weightKg = null,
        reps = null,
        rpe = null,
        durationSeconds = null,
        distanceMeters = null,
        completedAt = null,
        createdAt = now,
        updatedAt = now,
    )

    private fun newId() = UUID.randomUUID().toString()

    private fun defaultName(now: Long): String {
        val hour = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).hour
        return when (hour) {
            in 5..11 -> "Morning workout"
            in 12..16 -> "Afternoon workout"
            in 17..21 -> "Evening workout"
            else -> "Late workout"
        }
    }

    companion object {
        const val DEFAULT_SETS = 3
    }
}

/** One exercise of a workout about to start, with its targets. */
data class PlannedExercise(
    val exerciseId: String,
    val sets: Int,
    val targetMin: Int?,
    val targetMax: Int?,
    val targetRpe: Double? = null,
    val restSeconds: Int? = null,
    val supersetGroup: Int? = null,
    val notes: String? = null,
)

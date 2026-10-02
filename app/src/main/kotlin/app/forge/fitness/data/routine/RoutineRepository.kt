package app.forge.fitness.data.routine

import androidx.room.withTransaction
import app.forge.domain.model.SetType
import app.forge.domain.program.ProgramTemplate
import app.forge.fitness.data.db.ExerciseDao
import app.forge.fitness.data.db.ForgeDatabase
import app.forge.fitness.data.db.ProgramEntity
import app.forge.fitness.data.db.RoutineDao
import app.forge.fitness.data.db.RoutineEntity
import app.forge.fitness.data.db.RoutineExerciseEntity
import app.forge.fitness.data.db.WorkoutDao
import app.forge.fitness.di.TimeSource
import java.time.DayOfWeek
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Default targets for an exercise added to a routine by hand. */
private const val DEFAULT_SETS = 3
private const val DEFAULT_MIN = 8
private const val DEFAULT_MAX = 12

@Singleton
class RoutineRepository @Inject constructor(
    private val db: ForgeDatabase,
    private val dao: RoutineDao,
    private val exercises: ExerciseDao,
    private val workouts: WorkoutDao,
    private val time: TimeSource,
) {
    fun observeRoutines() = dao.observeRoutines()

    fun observeRoutine(id: String) = dao.observeRoutine(id)

    fun observePrograms() = dao.observePrograms()

    fun observeActiveProgram() = dao.observeActiveProgram()

    fun observeLastProgramRun(programId: String) = dao.observeLastProgramRun(programId)

    suspend fun getRoutineWithExercises(id: String) = dao.getRoutineWithExercises(id)

    suspend fun getProgramRoutines(programId: String) = dao.getProgramRoutines(programId)

    // ---- Routines ---------------------------------------------------------------------

    suspend fun createRoutine(name: String, folder: String? = null): String = db.withTransaction {
        val now = time.now()
        val id = newId()
        dao.insertRoutine(
            RoutineEntity(
                id = id,
                name = name.trim().ifEmpty { "New routine" },
                notes = null,
                folder = folder?.trim()?.ifEmpty { null },
                position = nextPosition(),
                programId = null,
                programPosition = null,
                createdAt = now,
                updatedAt = now,
            ),
        )
        id
    }

    /** Copies a routine and its exercises. The copy isn't part of any program. */
    suspend fun duplicate(routineId: String): String? = db.withTransaction {
        val source = dao.getRoutine(routineId) ?: return@withTransaction null
        val now = time.now()
        val id = newId()
        dao.insertRoutine(
            source.copy(
                id = id,
                name = "${source.name} (copy)",
                position = nextPosition(),
                programId = null,
                programPosition = null,
                createdAt = now,
                updatedAt = now,
                deletedAt = null,
            ),
        )
        dao.insertItems(dao.getItems(routineId).map { it.copy(id = newId(), routineId = id, createdAt = now, updatedAt = now) })
        id
    }

    suspend fun rename(routineId: String, name: String) = editRoutine(routineId) {
        it.copy(name = name.trim().ifEmpty { it.name })
    }

    suspend fun setNotes(routineId: String, notes: String) = editRoutine(routineId) { it.copy(notes = notes.ifBlank { null }) }

    suspend fun setFolder(routineId: String, folder: String?) = editRoutine(routineId) {
        it.copy(folder = folder?.trim()?.ifEmpty { null })
    }

    suspend fun delete(routineId: String) = editRoutine(routineId) { it.copy(deletedAt = time.now()) }

    suspend fun restore(routineId: String) = editRoutine(routineId) { it.copy(deletedAt = null) }

    /** Saves a new order for the routines list (ids in the order shown). */
    suspend fun reorderRoutines(orderedIds: List<String>) = db.withTransaction {
        val byId = dao.getRoutines().associateBy { it.id }
        val now = time.now()
        dao.updateRoutines(
            orderedIds.mapIndexedNotNull { i, id ->
                byId[id]?.takeIf { it.position != i }?.copy(position = i, updatedAt = now)
            },
        )
    }

    // ---- Exercises in a routine -------------------------------------------------------

    suspend fun addExercises(routineId: String, exerciseIds: List<String>) = db.withTransaction {
        val now = time.now()
        var position = (dao.getItems(routineId).maxOfOrNull { it.position } ?: -1) + 1
        dao.insertItems(
            exerciseIds.map { exerciseId ->
                RoutineExerciseEntity(
                    id = newId(),
                    routineId = routineId,
                    exerciseId = exerciseId,
                    position = position++,
                    supersetGroup = null,
                    targetSets = DEFAULT_SETS,
                    targetMin = DEFAULT_MIN,
                    targetMax = DEFAULT_MAX,
                    targetRpe = null,
                    restSeconds = null,
                    notes = null,
                    createdAt = now,
                    updatedAt = now,
                )
            },
        )
        touch(routineId)
    }

    suspend fun updateItem(item: RoutineExerciseEntity) = db.withTransaction {
        dao.updateItems(listOf(item.copy(updatedAt = time.now())))
        touch(item.routineId)
    }

    suspend fun removeItem(itemId: String) = db.withTransaction {
        val item = dao.getItem(itemId) ?: return@withTransaction
        dao.updateItems(listOf(item.copy(deletedAt = time.now(), supersetGroup = null, updatedAt = time.now())))
        tidySupersets(item.routineId)
    }

    suspend fun restoreItem(itemId: String) = db.withTransaction {
        val item = dao.getItem(itemId) ?: return@withTransaction
        dao.updateItems(listOf(item.copy(deletedAt = null, updatedAt = time.now())))
    }

    /** Saves a new exercise order (ids in the order shown) after a drag. */
    suspend fun reorderItems(routineId: String, orderedIds: List<String>) = db.withTransaction {
        val byId = dao.getItems(routineId).associateBy { it.id }
        val now = time.now()
        dao.updateItems(
            orderedIds.mapIndexedNotNull { i, id ->
                byId[id]?.takeIf { it.position != i }?.copy(position = i, updatedAt = now)
            },
        )
        tidySupersets(routineId)
    }

    suspend fun supersetWithNext(itemId: String) = db.withTransaction {
        val item = dao.getItem(itemId) ?: return@withTransaction
        val list = dao.getItems(item.routineId)
        val next = list.getOrNull(list.indexOfFirst { it.id == itemId } + 1) ?: return@withTransaction
        val group = item.supersetGroup ?: next.supersetGroup ?: ((list.mapNotNull { it.supersetGroup }.maxOrNull() ?: 0) + 1)
        val now = time.now()
        dao.updateItems(listOf(item, next).map { it.copy(supersetGroup = group, updatedAt = now) })
    }

    suspend fun leaveSuperset(itemId: String) = db.withTransaction {
        val item = dao.getItem(itemId) ?: return@withTransaction
        dao.updateItems(listOf(item.copy(supersetGroup = null, updatedAt = time.now())))
        tidySupersets(item.routineId)
    }

    // ---- Programs ---------------------------------------------------------------------

    /**
     * Copies a prebuilt program into your routines (in a folder named after it) and makes
     * it your active program. Exercises missing from the library are skipped.
     */
    suspend fun installTemplate(template: ProgramTemplate): String = db.withTransaction {
        val now = time.now()
        val programId = newId()
        val sourceIds = template.routines.flatMap { r -> r.slots.map { it.sourceId } }.distinct()
        val bySource = exercises.getBySourceIds(sourceIds).associateBy { it.sourceId }
        deactivatePrograms()
        dao.insertProgram(
            ProgramEntity(
                id = programId,
                name = template.name,
                description = template.summary,
                templateKey = template.key,
                trainingDays = DayBits.fromDays(template.trainingDays),
                isActive = true,
                createdAt = now,
                updatedAt = now,
            ),
        )
        var position = nextPosition()
        template.routines.forEachIndexed { index, routine ->
            val routineId = newId()
            dao.insertRoutine(
                RoutineEntity(
                    id = routineId,
                    name = routine.name,
                    notes = routine.notes,
                    folder = template.name,
                    position = position++,
                    programId = programId,
                    programPosition = index,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
            dao.insertItems(
                routine.slots.mapNotNull { slot ->
                    val exercise = bySource[slot.sourceId] ?: return@mapNotNull null
                    slot to exercise
                }.mapIndexed { i, (slot, exercise) ->
                    RoutineExerciseEntity(
                        id = newId(),
                        routineId = routineId,
                        exerciseId = exercise.id,
                        position = i,
                        supersetGroup = slot.supersetGroup,
                        targetSets = slot.sets,
                        targetMin = slot.targetMin,
                        targetMax = slot.targetMax,
                        targetRpe = null,
                        restSeconds = slot.restSeconds,
                        notes = null,
                        createdAt = now,
                        updatedAt = now,
                    )
                },
            )
        }
        programId
    }

    /** Makes [programId] the active program, or turns programs off with null. */
    suspend fun setActiveProgram(programId: String?) = db.withTransaction {
        deactivatePrograms()
        if (programId != null) editProgram(programId) { it.copy(isActive = true) }
    }

    suspend fun setTrainingDays(programId: String, days: Set<DayOfWeek>) =
        editProgram(programId) { it.copy(trainingDays = DayBits.fromDays(days)) }

    /** Deletes the program; its routines stay as normal routines. */
    suspend fun deleteProgram(programId: String) = db.withTransaction {
        val now = time.now()
        editProgram(programId) { it.copy(deletedAt = now, isActive = false) }
        dao.updateRoutines(
            dao.getProgramRoutines(programId).map { it.copy(programId = null, programPosition = null, updatedAt = now) },
        )
    }

    /**
     * Turns a finished workout into a routine: same exercises and order, with as many sets
     * as you completed and a target range spanning the reps you did.
     */
    suspend fun saveSessionAsRoutine(sessionId: String, name: String): String = db.withTransaction {
        val now = time.now()
        val routineId = newId()
        dao.insertRoutine(
            RoutineEntity(
                id = routineId,
                name = name.trim().ifEmpty { "My routine" },
                notes = null,
                folder = null,
                position = nextPosition(),
                programId = null,
                programPosition = null,
                createdAt = now,
                updatedAt = now,
            ),
        )
        val sets = workouts.getSetsForSession(sessionId)
            .filter { it.completedAt != null && it.type != SetType.WARMUP }
            .groupBy { it.sessionExerciseId }
        dao.insertItems(
            workouts.getSessionExercises(sessionId).mapIndexed { i, se ->
                val done = sets[se.id].orEmpty()
                val reps = done.mapNotNull { it.reps ?: it.durationSeconds }
                RoutineExerciseEntity(
                    id = newId(),
                    routineId = routineId,
                    exerciseId = se.exerciseId,
                    position = i,
                    supersetGroup = se.supersetGroup,
                    targetSets = done.size.coerceAtLeast(1),
                    targetMin = reps.minOrNull(),
                    targetMax = reps.maxOrNull(),
                    targetRpe = null,
                    restSeconds = se.restSeconds,
                    notes = se.notes,
                    createdAt = now,
                    updatedAt = now,
                )
            },
        )
        routineId
    }

    // ---- Helpers ----------------------------------------------------------------------

    private suspend fun nextPosition() = (dao.getRoutines().maxOfOrNull { it.position } ?: -1) + 1

    private suspend fun touch(routineId: String) = editRoutine(routineId) { it }

    private suspend fun editRoutine(id: String, change: (RoutineEntity) -> RoutineEntity) {
        val current = dao.getRoutine(id) ?: return
        dao.updateRoutines(listOf(change(current).copy(updatedAt = time.now())))
    }

    private suspend fun editProgram(id: String, change: (ProgramEntity) -> ProgramEntity) {
        val current = dao.getProgram(id) ?: return
        dao.updatePrograms(listOf(change(current).copy(updatedAt = time.now())))
    }

    private suspend fun deactivatePrograms() {
        val now = time.now()
        dao.updatePrograms(dao.getPrograms().filter { it.isActive }.map { it.copy(isActive = false, updatedAt = now) })
    }

    /** A superset only makes sense between neighbours; unlink anything left alone. */
    private suspend fun tidySupersets(routineId: String) {
        val list = dao.getItems(routineId)
        val keep = list.indices.filter { i ->
            val g = list[i].supersetGroup ?: return@filter false
            list.getOrNull(i - 1)?.supersetGroup == g || list.getOrNull(i + 1)?.supersetGroup == g
        }.map { list[it].id }.toSet()
        val now = time.now()
        dao.updateItems(list.filter { it.supersetGroup != null && it.id !in keep }.map { it.copy(supersetGroup = null, updatedAt = now) })
    }

    private fun newId() = UUID.randomUUID().toString()
}

/** Training days as a bit set: Monday = 1, Tuesday = 2, … Sunday = 64. 0 = any day. */
object DayBits {
    fun fromDays(days: Set<DayOfWeek>): Int = days.fold(0) { acc, d -> acc or (1 shl (d.value - 1)) }

    fun toDays(bits: Int): Set<DayOfWeek> = DayOfWeek.entries.filter { bits and (1 shl (it.value - 1)) != 0 }.toSet()
}

package app.forge.fitness.data.db

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

data class RoutineExerciseWithExercise(
    @Embedded val item: RoutineExerciseEntity,
    @Relation(parentColumn = "exerciseId", entityColumn = "id")
    val exercise: ExerciseEntity,
)

/** A routine with its exercises. Removed (soft-deleted) items are filtered by [active]. */
data class RoutineWithExercises(
    @Embedded val routine: RoutineEntity,
    @Relation(entity = RoutineExerciseEntity::class, parentColumn = "id", entityColumn = "routineId")
    val items: List<RoutineExerciseWithExercise>,
) {
    val active: List<RoutineExerciseWithExercise>
        get() = items.filter { it.item.deletedAt == null }.sortedBy { it.item.position }
}

/** The most recent finished workout started from one of a program's routines. */
data class ProgramRun(val routineId: String, val startedAt: Long)

@Dao
interface RoutineDao {

    // ---- Routines ---------------------------------------------------------------------

    @Transaction
    @Query("SELECT * FROM routine WHERE deletedAt IS NULL ORDER BY position")
    fun observeRoutines(): Flow<List<RoutineWithExercises>>

    @Transaction
    @Query("SELECT * FROM routine WHERE id = :id")
    fun observeRoutine(id: String): Flow<RoutineWithExercises?>

    @Transaction
    @Query("SELECT * FROM routine WHERE id = :id")
    suspend fun getRoutineWithExercises(id: String): RoutineWithExercises?

    @Query("SELECT * FROM routine WHERE id = :id")
    suspend fun getRoutine(id: String): RoutineEntity?

    @Query("SELECT * FROM routine WHERE deletedAt IS NULL ORDER BY position")
    suspend fun getRoutines(): List<RoutineEntity>

    @Query("SELECT * FROM routine WHERE programId = :programId AND deletedAt IS NULL ORDER BY programPosition")
    suspend fun getProgramRoutines(programId: String): List<RoutineEntity>

    @Insert
    suspend fun insertRoutine(routine: RoutineEntity)

    @Update
    suspend fun updateRoutines(routines: List<RoutineEntity>)

    // ---- Routine exercises ------------------------------------------------------------

    @Query("SELECT * FROM routine_exercise WHERE routineId = :routineId AND deletedAt IS NULL ORDER BY position")
    suspend fun getItems(routineId: String): List<RoutineExerciseEntity>

    @Query("SELECT * FROM routine_exercise WHERE id = :id")
    suspend fun getItem(id: String): RoutineExerciseEntity?

    @Insert
    suspend fun insertItems(items: List<RoutineExerciseEntity>)

    @Update
    suspend fun updateItems(items: List<RoutineExerciseEntity>)

    // ---- Programs ---------------------------------------------------------------------

    @Query("SELECT * FROM program WHERE deletedAt IS NULL ORDER BY createdAt")
    fun observePrograms(): Flow<List<ProgramEntity>>

    @Query("SELECT * FROM program WHERE isActive = 1 AND deletedAt IS NULL LIMIT 1")
    fun observeActiveProgram(): Flow<ProgramEntity?>

    @Query("SELECT * FROM program WHERE id = :id")
    suspend fun getProgram(id: String): ProgramEntity?

    @Query("SELECT * FROM program WHERE deletedAt IS NULL")
    suspend fun getPrograms(): List<ProgramEntity>

    @Insert
    suspend fun insertProgram(program: ProgramEntity)

    @Update
    suspend fun updatePrograms(programs: List<ProgramEntity>)

    @Query(
        """
        SELECT s.routineId AS routineId, s.startedAt AS startedAt
        FROM workout_session s JOIN routine r ON r.id = s.routineId
        WHERE r.programId = :programId AND s.status = 'FINISHED' AND s.deletedAt IS NULL
        ORDER BY s.startedAt DESC LIMIT 1
        """,
    )
    fun observeLastProgramRun(programId: String): Flow<ProgramRun?>

    // ---- Export -----------------------------------------------------------------------

    @Query("SELECT * FROM program") suspend fun exportPrograms(): List<ProgramEntity>
    @Query("SELECT * FROM routine") suspend fun exportRoutines(): List<RoutineEntity>
    @Query("SELECT * FROM routine_exercise") suspend fun exportRoutineExercises(): List<RoutineExerciseEntity>
}

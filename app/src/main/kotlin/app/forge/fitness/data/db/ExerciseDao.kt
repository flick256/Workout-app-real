package app.forge.fitness.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** How often and how recently you've done an exercise (for "Recent" in the picker). */
data class ExerciseUsage(
    val exerciseId: String,
    val timesUsed: Int,
    val lastUsedAt: Long,
)

@Dao
interface ExerciseDao {

    @Query("SELECT * FROM exercise WHERE deletedAt IS NULL AND archived = 0 ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<ExerciseEntity>>

    /** Everything including archived (the list hides archived unless you ask for them). */
    @Query("SELECT * FROM exercise WHERE deletedAt IS NULL ORDER BY name COLLATE NOCASE")
    fun observeAllIncludingArchived(): Flow<List<ExerciseEntity>>

    @Query("SELECT * FROM exercise WHERE id = :id")
    suspend fun getById(id: String): ExerciseEntity?

    @Query("SELECT * FROM exercise WHERE id = :id")
    fun observeById(id: String): Flow<ExerciseEntity?>

    @Query("SELECT * FROM exercise WHERE progressionChain = :chain AND deletedAt IS NULL ORDER BY progressionStep")
    fun observeChain(chain: String): Flow<List<ExerciseEntity>>

    @Insert
    suspend fun insert(exercise: ExerciseEntity)

    @Update
    suspend fun update(exercise: ExerciseEntity)

    @Query("SELECT COUNT(*) FROM exercise WHERE isCustom = 0")
    suspend fun bundledCount(): Int

    @Query("SELECT * FROM exercise WHERE sourceId IN (:sourceIds)")
    suspend fun getBySourceIds(sourceIds: List<String>): List<ExerciseEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(exercises: List<ExerciseEntity>)

    @Update
    suspend fun updateAll(exercises: List<ExerciseEntity>)

    @Query(
        """
        SELECT se.exerciseId AS exerciseId, COUNT(*) AS timesUsed, MAX(s.startedAt) AS lastUsedAt
        FROM session_exercise se
        JOIN workout_session s ON s.id = se.sessionId
        WHERE se.deletedAt IS NULL AND s.deletedAt IS NULL AND s.status = 'FINISHED'
        GROUP BY se.exerciseId
        """,
    )
    fun observeUsage(): Flow<List<ExerciseUsage>>

    /**
     * Inserts new bundled exercises and refreshes the text of existing ones, keeping
     * your own changes (archived flag, creation time). Runs as one transaction, so an
     * interrupted import leaves nothing half-done.
     */
    @Transaction
    suspend fun upsertBundled(incoming: List<ExerciseEntity>) {
        val existing = getBySourceIds(incoming.mapNotNull { it.sourceId }).associateBy { it.sourceId }
        val (updates, inserts) = incoming.partition { it.sourceId in existing }
        insertAll(inserts)
        updateAll(
            updates.map { new ->
                val old = existing.getValue(new.sourceId)
                new.copy(id = old.id, archived = old.archived, createdAt = old.createdAt, deletedAt = old.deletedAt)
            },
        )
    }

    @Query("SELECT * FROM exercise")
    suspend fun exportAll(): List<ExerciseEntity>
}

package app.forge.fitness.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ActivityDao {
    @Query("SELECT * FROM activity_session WHERE deletedAt IS NULL ORDER BY startedAt DESC")
    fun observeAll(): Flow<List<ActivitySessionEntity>>

    @Query("SELECT * FROM activity_session WHERE deletedAt IS NULL AND startedAt >= :since ORDER BY startedAt DESC")
    fun observeSince(since: Long): Flow<List<ActivitySessionEntity>>

    @Query("SELECT * FROM activity_session WHERE id = :id")
    fun observe(id: String): Flow<ActivitySessionEntity?>

    @Query("SELECT * FROM activity_session WHERE id = :id")
    suspend fun get(id: String): ActivitySessionEntity?

    /** Includes deleted rows, so a session you removed isn't imported again. */
    @Query("SELECT * FROM activity_session WHERE externalId = :externalId")
    suspend fun getByExternalId(externalId: String): ActivitySessionEntity?

    /** Hand-logged sessions that overlap [from, to], to merge with what the strap recorded. */
    @Query(
        "SELECT * FROM activity_session WHERE deletedAt IS NULL AND externalId IS NULL " +
            "AND startedAt < :to AND startedAt + durationMinutes * 60000 > :from",
    )
    suspend fun manualOverlapping(from: Long, to: Long): List<ActivitySessionEntity>

    /** Imported sessions overlapping [from, to] (another app may have recorded the same thing). */
    @Query(
        "SELECT * FROM activity_session WHERE deletedAt IS NULL AND externalId IS NOT NULL " +
            "AND startedAt < :to AND startedAt + durationMinutes * 60000 > :from",
    )
    suspend fun importedOverlapping(from: Long, to: Long): List<ActivitySessionEntity>

    @Insert
    suspend fun insert(activity: ActivitySessionEntity)

    @Update
    suspend fun update(activity: ActivitySessionEntity)

    @Query("SELECT * FROM activity_session")
    suspend fun exportAll(): List<ActivitySessionEntity>

    // ---- Daily health ------------------------------------------------------------------

    @Query("SELECT * FROM daily_health WHERE epochDay >= :fromDay ORDER BY epochDay")
    fun observeDaily(fromDay: Long): Flow<List<DailyHealthEntity>>

    @Query("SELECT * FROM daily_health ORDER BY epochDay")
    suspend fun exportDaily(): List<DailyHealthEntity>

    @Upsert
    suspend fun upsertDaily(days: List<DailyHealthEntity>)
}

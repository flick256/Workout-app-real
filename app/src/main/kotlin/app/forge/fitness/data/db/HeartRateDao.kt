package app.forge.fitness.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface HeartRateDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(samples: List<HeartRateSampleEntity>)

    @Query("SELECT * FROM heart_rate_sample WHERE sessionId = :sessionId ORDER BY atMillis")
    suspend fun forSession(sessionId: String): List<HeartRateSampleEntity>

    @Query("SELECT * FROM heart_rate_sample WHERE sessionId = :sessionId ORDER BY atMillis")
    fun observeSession(sessionId: String): Flow<List<HeartRateSampleEntity>>

    @Query("SELECT COUNT(*) FROM heart_rate_sample WHERE sessionId = :sessionId")
    suspend fun count(sessionId: String): Int

    @Query("SELECT * FROM heart_rate_sample")
    suspend fun exportAll(): List<HeartRateSampleEntity>
}

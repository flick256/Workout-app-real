package app.forge.fitness.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import app.forge.domain.model.BodyMetricKind
import kotlinx.coroutines.flow.Flow

@Dao
interface BodyMetricDao {
    @Query(
        "SELECT * FROM body_metric WHERE kind = :kind AND deletedAt IS NULL " +
            "ORDER BY measuredAt DESC LIMIT 1",
    )
    fun observeLatest(kind: BodyMetricKind): Flow<BodyMetricEntity?>

    @Query(
        "SELECT * FROM body_metric WHERE kind = :kind AND deletedAt IS NULL " +
            "ORDER BY measuredAt DESC LIMIT 1",
    )
    suspend fun latest(kind: BodyMetricKind): BodyMetricEntity?

    @Query("SELECT * FROM body_metric WHERE kind = :kind AND deletedAt IS NULL ORDER BY measuredAt DESC")
    fun observeAll(kind: BodyMetricKind): Flow<List<BodyMetricEntity>>

    @Query("SELECT * FROM body_metric WHERE deletedAt IS NULL ORDER BY measuredAt DESC")
    fun observeEverything(): Flow<List<BodyMetricEntity>>

    @Query("SELECT * FROM body_metric WHERE id = :id")
    suspend fun get(id: String): BodyMetricEntity?

    @Insert
    suspend fun insert(metric: BodyMetricEntity)

    @Update
    suspend fun update(metric: BodyMetricEntity)

    @Query("SELECT * FROM body_metric")
    suspend fun exportAll(): List<BodyMetricEntity>
}

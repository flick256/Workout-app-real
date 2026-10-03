package app.forge.fitness.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PhotoDao {
    @Query("SELECT * FROM progress_photo WHERE deletedAt IS NULL ORDER BY takenAt DESC")
    fun observeAll(): Flow<List<ProgressPhotoEntity>>

    @Query("SELECT * FROM progress_photo WHERE id = :id")
    suspend fun get(id: String): ProgressPhotoEntity?

    @Insert
    suspend fun insert(photo: ProgressPhotoEntity)

    @Update
    suspend fun update(photo: ProgressPhotoEntity)

    @Query("SELECT * FROM progress_photo")
    suspend fun exportAll(): List<ProgressPhotoEntity>
}

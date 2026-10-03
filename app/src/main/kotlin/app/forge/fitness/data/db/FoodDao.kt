package app.forge.fitness.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface FoodDao {
    // ---- Foods -------------------------------------------------------------------------

    @Query("SELECT * FROM food WHERE id = :id")
    suspend fun getFood(id: String): FoodEntity?

    @Query("SELECT * FROM food WHERE id = :id")
    fun observeFood(id: String): Flow<FoodEntity?>

    /** Includes deleted foods, so a re-scan can bring one back instead of clashing. */
    @Query("SELECT * FROM food WHERE barcode = :barcode")
    suspend fun getByBarcode(barcode: String): FoodEntity?

    @Query(
        "SELECT * FROM food WHERE deletedAt IS NULL AND (name LIKE '%' || :query || '%' " +
            "OR brand LIKE '%' || :query || '%') ORDER BY favorite DESC, name COLLATE NOCASE LIMIT 60",
    )
    fun search(query: String): Flow<List<FoodEntity>>

    @Query("SELECT * FROM food WHERE deletedAt IS NULL AND favorite = 1 ORDER BY name COLLATE NOCASE")
    fun observeFavorites(): Flow<List<FoodEntity>>

    /** Foods you've logged, most recent first. */
    @Query(
        """
        SELECT f.* FROM food f
        JOIN (SELECT foodId, MAX(loggedAt) AS lastLogged FROM food_log
              WHERE deletedAt IS NULL AND foodId IS NOT NULL GROUP BY foodId) r ON r.foodId = f.id
        WHERE f.deletedAt IS NULL
        ORDER BY r.lastLogged DESC
        LIMIT :limit
        """,
    )
    fun observeRecent(limit: Int): Flow<List<FoodEntity>>

    @Insert
    suspend fun insertFood(food: FoodEntity)

    @Update
    suspend fun updateFood(food: FoodEntity)

    // ---- Log ---------------------------------------------------------------------------

    @Query("SELECT * FROM food_log WHERE epochDay = :day AND deletedAt IS NULL ORDER BY loggedAt")
    fun observeDay(day: Long): Flow<List<FoodLogEntity>>

    @Query("SELECT * FROM food_log WHERE epochDay BETWEEN :from AND :to AND deletedAt IS NULL ORDER BY epochDay, loggedAt")
    fun observeRange(from: Long, to: Long): Flow<List<FoodLogEntity>>

    @Query("SELECT * FROM food_log WHERE epochDay = :day AND meal = :meal AND deletedAt IS NULL ORDER BY loggedAt")
    suspend fun getMeal(day: Long, meal: String): List<FoodLogEntity>

    @Query("SELECT * FROM food_log WHERE id = :id")
    suspend fun getEntry(id: String): FoodLogEntity?

    @Insert
    suspend fun insertEntries(entries: List<FoodLogEntity>)

    @Update
    suspend fun updateEntry(entry: FoodLogEntity)

    @Query("SELECT * FROM food")
    suspend fun exportFoods(): List<FoodEntity>

    @Query("SELECT * FROM food_log")
    suspend fun exportLog(): List<FoodLogEntity>
}

package app.forge.fitness.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface GoalDao {
    // ---- Goals -------------------------------------------------------------------------

    @Query("SELECT * FROM goal WHERE deletedAt IS NULL ORDER BY createdAt")
    fun observeGoals(): Flow<List<GoalEntity>>

    @Query("SELECT * FROM goal WHERE id = :id")
    suspend fun getGoal(id: String): GoalEntity?

    @Insert
    suspend fun insertGoal(goal: GoalEntity)

    @Update
    suspend fun updateGoal(goal: GoalEntity)

    // ---- Habits ------------------------------------------------------------------------

    @Query("SELECT * FROM habit WHERE deletedAt IS NULL ORDER BY position, createdAt")
    fun observeHabits(): Flow<List<HabitEntity>>

    @Query("SELECT * FROM habit WHERE deletedAt IS NULL ORDER BY position, createdAt")
    suspend fun getHabits(): List<HabitEntity>

    @Query("SELECT * FROM habit WHERE id = :id")
    suspend fun getHabit(id: String): HabitEntity?

    @Insert
    suspend fun insertHabit(habit: HabitEntity)

    @Update
    suspend fun updateHabit(habit: HabitEntity)

    @Query("SELECT * FROM habit_check WHERE epochDay >= :fromDay")
    fun observeChecks(fromDay: Long): Flow<List<HabitCheckEntity>>

    @Query("SELECT COUNT(*) FROM habit_check WHERE habitId = :habitId AND epochDay = :day")
    suspend fun isChecked(habitId: String, day: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun check(check: HabitCheckEntity)

    @Query("DELETE FROM habit_check WHERE habitId = :habitId AND epochDay = :day")
    suspend fun uncheck(habitId: String, day: Long)

    @Query("SELECT * FROM goal")
    suspend fun exportGoals(): List<GoalEntity>

    @Query("SELECT * FROM habit")
    suspend fun exportHabits(): List<HabitEntity>

    @Query("SELECT * FROM habit_check")
    suspend fun exportChecks(): List<HabitCheckEntity>
}

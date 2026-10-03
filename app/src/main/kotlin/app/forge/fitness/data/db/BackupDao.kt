package app.forge.fitness.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

/**
 * Writes for restoring a backup. Upsert (insert or update in place) never deletes a row,
 * so restoring can't cascade-delete anything on the phone.
 */
@Dao
interface BackupDao {
    @Upsert suspend fun exercises(rows: List<ExerciseEntity>)
    @Upsert suspend fun sessions(rows: List<WorkoutSessionEntity>)
    @Upsert suspend fun sessionExercises(rows: List<SessionExerciseEntity>)
    @Upsert suspend fun sets(rows: List<SetEntryEntity>)
    @Upsert suspend fun bodyMetrics(rows: List<BodyMetricEntity>)
    @Upsert suspend fun programs(rows: List<ProgramEntity>)
    @Upsert suspend fun routines(rows: List<RoutineEntity>)
    @Upsert suspend fun routineExercises(rows: List<RoutineExerciseEntity>)
    @Upsert suspend fun photos(rows: List<ProgressPhotoEntity>)
    @Upsert suspend fun activities(rows: List<ActivitySessionEntity>)
    @Upsert suspend fun dailyHealth(rows: List<DailyHealthEntity>)
    @Upsert suspend fun foods(rows: List<FoodEntity>)
    @Upsert suspend fun foodLog(rows: List<FoodLogEntity>)
    @Upsert suspend fun goals(rows: List<GoalEntity>)
    @Upsert suspend fun habits(rows: List<HabitEntity>)
    @Upsert suspend fun habitChecks(rows: List<HabitCheckEntity>)
    @Upsert suspend fun heartRateSamples(rows: List<HeartRateSampleEntity>)

    // ---- Restoring a snapshot exactly: clear your data first (children before parents).
    // Library exercises and progress photos (whose image files live outside backups) stay.

    @Query("DELETE FROM set_entry") suspend fun clearSets()
    @Query("DELETE FROM session_exercise") suspend fun clearSessionExercises()
    @Query("DELETE FROM heart_rate_sample") suspend fun clearHeartRate()
    @Query("DELETE FROM workout_session") suspend fun clearSessions()
    @Query("DELETE FROM routine_exercise") suspend fun clearRoutineExercises()
    @Query("DELETE FROM routine") suspend fun clearRoutines()
    @Query("DELETE FROM program") suspend fun clearPrograms()
    @Query("DELETE FROM habit_check") suspend fun clearHabitChecks()
    @Query("DELETE FROM habit") suspend fun clearHabits()
    @Query("DELETE FROM goal") suspend fun clearGoals()
    @Query("DELETE FROM food_log") suspend fun clearFoodLog()
    @Query("DELETE FROM food") suspend fun clearFoods()
    @Query("DELETE FROM activity_session") suspend fun clearActivities()
    @Query("DELETE FROM daily_health") suspend fun clearDailyHealth()
    @Query("DELETE FROM body_metric") suspend fun clearBodyMetrics()
    @Query("DELETE FROM exercise WHERE isCustom = 1") suspend fun clearCustomExercises()

    suspend fun clearUserData() {
        clearSets(); clearSessionExercises(); clearHeartRate(); clearSessions()
        clearRoutineExercises(); clearRoutines(); clearPrograms()
        clearHabitChecks(); clearHabits(); clearGoals()
        clearFoodLog(); clearFoods(); clearActivities(); clearDailyHealth(); clearBodyMetrics()
        clearCustomExercises()
    }

    @Query("SELECT id FROM exercise")
    suspend fun exerciseIds(): List<String>

    @Query("SELECT * FROM program WHERE isActive = 1 AND deletedAt IS NULL ORDER BY updatedAt DESC")
    suspend fun activePrograms(): List<ProgramEntity>

    @Query("UPDATE program SET isActive = 0 WHERE id != :keepId")
    suspend fun deactivateOtherPrograms(keepId: String)
}

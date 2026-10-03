package app.forge.fitness.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction

/** Removes everything "Load demo data" created, and nothing else. */
@Dao
interface DemoDao {
    @Query("SELECT COUNT(*) FROM workout_session WHERE isDemo = 1")
    suspend fun demoSessionCount(): Int

    @Query(
        "DELETE FROM set_entry WHERE sessionExerciseId IN (SELECT se.id FROM session_exercise se " +
            "JOIN workout_session s ON s.id = se.sessionId WHERE s.isDemo = 1)",
    )
    suspend fun deleteDemoSets()

    @Query("DELETE FROM session_exercise WHERE sessionId IN (SELECT id FROM workout_session WHERE isDemo = 1)")
    suspend fun deleteDemoSessionExercises()

    @Query("DELETE FROM workout_session WHERE isDemo = 1")
    suspend fun deleteDemoSessions()

    @Query("DELETE FROM body_metric WHERE isDemo = 1")
    suspend fun deleteDemoBodyMetrics()

    @Transaction
    suspend fun deleteAllDemo() {
        deleteDemoSets()
        deleteDemoSessionExercises()
        deleteDemoSessions()
        deleteDemoBodyMetrics()
    }
}

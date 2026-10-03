package app.forge.fitness.data.db

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Update
import app.forge.domain.model.Muscle
import kotlinx.coroutines.flow.Flow

/** An exercise in a session, together with its library entry. */
data class SessionExerciseWithExercise(
    @Embedded val item: SessionExerciseEntity,
    @Relation(parentColumn = "exerciseId", entityColumn = "id")
    val exercise: ExerciseEntity,
)

/** One row of the History list. Totals are computed in SQL so the list stays fast. */
data class SessionSummaryRow(
    val id: String,
    val name: String,
    val startedAt: Long,
    val endedAt: Long?,
    val notes: String?,
    val setCount: Int,
    val volumeKg: Double,
)

/** A completed work set of one exercise, with when it happened (for exercise history). */
data class ExerciseHistorySet(
    val sessionId: String,
    val startedAt: Long,
    @Embedded val set: SetEntryEntity,
)

/** A completed work set with the muscles it trained (for the recovery model). */
data class RecentWorkSet(
    val completedAt: Long,
    val primaryMuscles: List<Muscle>,
    val secondaryMuscles: List<Muscle>,
)

/** One finished workout's date and average RPE (for deload hints). */
data class SessionPointRow(val startedAt: Long, val avgRpe: Double?)

/** A completed work set with its exercise and workout (for strength trends). */
data class TrendRow(
    val exerciseId: String,
    val exerciseName: String,
    val sessionId: String,
    val startedAt: Long,
    val weightKg: Double?,
    val loadKg: Double?,
    val reps: Int?,
    val rpe: Double?,
)

data class SessionExerciseLine(
    val sessionId: String,
    val position: Int,
    val name: String,
    val sets: Int,
)

@Dao
interface WorkoutDao {

    // ---- Sessions -------------------------------------------------------------------

    @Query(
        "SELECT * FROM workout_session WHERE status = 'ACTIVE' AND deletedAt IS NULL " +
            "ORDER BY startedAt DESC LIMIT 1",
    )
    fun observeActiveSession(): Flow<WorkoutSessionEntity?>

    @Query(
        "SELECT * FROM workout_session WHERE status = 'ACTIVE' AND deletedAt IS NULL " +
            "ORDER BY startedAt DESC LIMIT 1",
    )
    suspend fun getActiveSessionNow(): WorkoutSessionEntity?

    @Query("SELECT * FROM workout_session WHERE id = :id")
    fun observeSession(id: String): Flow<WorkoutSessionEntity?>

    @Query("SELECT * FROM workout_session WHERE id = :id")
    suspend fun getSession(id: String): WorkoutSessionEntity?

    @Insert
    suspend fun insertSession(session: WorkoutSessionEntity)

    @Update
    suspend fun updateSession(session: WorkoutSessionEntity)

    @Query(
        """
        SELECT s.id, s.name, s.startedAt, s.endedAt, s.notes,
          (SELECT COUNT(*) FROM set_entry st JOIN session_exercise se ON st.sessionExerciseId = se.id
            WHERE se.sessionId = s.id AND se.deletedAt IS NULL AND st.deletedAt IS NULL
              AND st.completedAt IS NOT NULL AND st.type != 'WARMUP') AS setCount,
          (SELECT COALESCE(SUM(COALESCE(st.loadKg, st.weightKg, 0) * COALESCE(st.reps, 0)), 0)
            FROM set_entry st JOIN session_exercise se ON st.sessionExerciseId = se.id
            WHERE se.sessionId = s.id AND se.deletedAt IS NULL AND st.deletedAt IS NULL
              AND st.completedAt IS NOT NULL AND st.type != 'WARMUP') AS volumeKg
        FROM workout_session s
        WHERE s.status = 'FINISHED' AND s.deletedAt IS NULL
        ORDER BY s.startedAt DESC
        """,
    )
    fun observeHistory(): Flow<List<SessionSummaryRow>>

    @Query(
        """
        SELECT se.sessionId AS sessionId, se.position AS position, e.name AS name,
          (SELECT COUNT(*) FROM set_entry st WHERE st.sessionExerciseId = se.id
             AND st.deletedAt IS NULL AND st.completedAt IS NOT NULL AND st.type != 'WARMUP') AS sets
        FROM session_exercise se
        JOIN exercise e ON e.id = se.exerciseId
        JOIN workout_session s ON s.id = se.sessionId
        WHERE s.status = 'FINISHED' AND s.deletedAt IS NULL AND se.deletedAt IS NULL
        ORDER BY se.sessionId, se.position
        """,
    )
    fun observeHistoryLines(): Flow<List<SessionExerciseLine>>

    // ---- Exercises within a session ---------------------------------------------------

    @Transaction
    @Query(
        "SELECT * FROM session_exercise WHERE sessionId = :sessionId AND deletedAt IS NULL " +
            "ORDER BY position",
    )
    fun observeSessionExercises(sessionId: String): Flow<List<SessionExerciseWithExercise>>

    @Transaction
    @Query(
        "SELECT * FROM session_exercise WHERE sessionId = :sessionId AND deletedAt IS NULL " +
            "ORDER BY position",
    )
    suspend fun getSessionExercisesWithExercise(sessionId: String): List<SessionExerciseWithExercise>

    @Query(
        "SELECT * FROM session_exercise WHERE sessionId = :sessionId AND deletedAt IS NULL " +
            "ORDER BY position",
    )
    suspend fun getSessionExercises(sessionId: String): List<SessionExerciseEntity>

    @Query("SELECT * FROM session_exercise WHERE id = :id")
    suspend fun getSessionExercise(id: String): SessionExerciseEntity?

    @Insert
    suspend fun insertSessionExercises(items: List<SessionExerciseEntity>)

    @Update
    suspend fun updateSessionExercises(items: List<SessionExerciseEntity>)

    // ---- Sets ---------------------------------------------------------------------------

    @Query(
        """
        SELECT st.* FROM set_entry st
        JOIN session_exercise se ON st.sessionExerciseId = se.id
        WHERE se.sessionId = :sessionId AND st.deletedAt IS NULL AND se.deletedAt IS NULL
        ORDER BY st.position
        """,
    )
    fun observeSets(sessionId: String): Flow<List<SetEntryEntity>>

    @Query("SELECT * FROM set_entry WHERE sessionExerciseId = :sessionExerciseId AND deletedAt IS NULL ORDER BY position")
    suspend fun getSets(sessionExerciseId: String): List<SetEntryEntity>

    @Query(
        """
        SELECT st.* FROM set_entry st
        JOIN session_exercise se ON st.sessionExerciseId = se.id
        WHERE se.sessionId = :sessionId AND st.deletedAt IS NULL AND se.deletedAt IS NULL
        """,
    )
    suspend fun getSetsForSession(sessionId: String): List<SetEntryEntity>

    @Query("SELECT * FROM set_entry WHERE id = :id")
    suspend fun getSet(id: String): SetEntryEntity?

    @Insert
    suspend fun insertSets(sets: List<SetEntryEntity>)

    @Update
    suspend fun updateSets(sets: List<SetEntryEntity>)

    /**
     * Completed sets from the most recent finished session that included [exerciseId]:
     * the "last time" numbers shown next to each set while logging.
     */
    @Query(
        """
        SELECT st.* FROM set_entry st
        WHERE st.deletedAt IS NULL AND st.completedAt IS NOT NULL AND st.sessionExerciseId = (
          SELECT se.id FROM session_exercise se
          JOIN workout_session s ON s.id = se.sessionId
          WHERE se.exerciseId = :exerciseId AND se.deletedAt IS NULL
            AND s.status = 'FINISHED' AND s.deletedAt IS NULL AND s.id != :excludeSessionId
            AND EXISTS (SELECT 1 FROM set_entry x WHERE x.sessionExerciseId = se.id
                        AND x.deletedAt IS NULL AND x.completedAt IS NOT NULL)
          ORDER BY s.startedAt DESC, se.position LIMIT 1
        )
        ORDER BY st.position
        """,
    )
    suspend fun previousSets(exerciseId: String, excludeSessionId: String): List<SetEntryEntity>

    /** Your completed work sets of one exercise, newest workout first. */
    @Query(
        """
        SELECT s.id AS sessionId, s.startedAt AS startedAt, st.*
        FROM set_entry st
        JOIN session_exercise se ON st.sessionExerciseId = se.id
        JOIN workout_session s ON s.id = se.sessionId
        WHERE se.exerciseId = :exerciseId AND s.status = 'FINISHED' AND s.deletedAt IS NULL
          AND se.deletedAt IS NULL AND st.deletedAt IS NULL AND st.completedAt IS NOT NULL
          AND st.type != 'WARMUP'
        ORDER BY s.startedAt DESC, st.position
        """,
    )
    fun observeExerciseHistory(exerciseId: String): Flow<List<ExerciseHistorySet>>

    @Query(
        """
        SELECT s.id AS sessionId, s.startedAt AS startedAt, st.*
        FROM set_entry st
        JOIN session_exercise se ON st.sessionExerciseId = se.id
        JOIN workout_session s ON s.id = se.sessionId
        WHERE se.exerciseId = :exerciseId AND s.status = 'FINISHED' AND s.deletedAt IS NULL
          AND se.deletedAt IS NULL AND st.deletedAt IS NULL AND st.completedAt IS NOT NULL
          AND st.type != 'WARMUP'
        ORDER BY s.startedAt DESC, st.position
        """,
    )
    suspend fun exerciseHistory(exerciseId: String): List<ExerciseHistorySet>

    /** Work sets since [since] in finished and in-progress workouts, with their muscles. */
    @Query(
        """
        SELECT st.completedAt AS completedAt, e.primaryMuscles AS primaryMuscles, e.secondaryMuscles AS secondaryMuscles
        FROM set_entry st
        JOIN session_exercise se ON st.sessionExerciseId = se.id
        JOIN workout_session s ON s.id = se.sessionId
        JOIN exercise e ON e.id = se.exerciseId
        WHERE st.completedAt >= :since AND st.type != 'WARMUP' AND st.deletedAt IS NULL
          AND se.deletedAt IS NULL AND s.deletedAt IS NULL AND s.status IN ('FINISHED', 'ACTIVE')
        """,
    )
    fun observeRecentWorkSets(since: Long): Flow<List<RecentWorkSet>>

    @Query(
        """
        SELECT s.startedAt AS startedAt,
          (SELECT AVG(st.rpe) FROM set_entry st JOIN session_exercise se ON st.sessionExerciseId = se.id
            WHERE se.sessionId = s.id AND st.rpe IS NOT NULL AND st.completedAt IS NOT NULL
              AND st.deletedAt IS NULL AND se.deletedAt IS NULL AND st.type != 'WARMUP') AS avgRpe
        FROM workout_session s
        WHERE s.status = 'FINISHED' AND s.deletedAt IS NULL AND s.startedAt >= :since
        """,
    )
    fun observeSessionPoints(since: Long): Flow<List<SessionPointRow>>

    @Query(
        """
        SELECT se.exerciseId AS exerciseId, e.name AS exerciseName, s.id AS sessionId, s.startedAt AS startedAt,
          st.weightKg AS weightKg, st.loadKg AS loadKg, st.reps AS reps, st.rpe AS rpe
        FROM set_entry st
        JOIN session_exercise se ON st.sessionExerciseId = se.id
        JOIN workout_session s ON s.id = se.sessionId
        JOIN exercise e ON e.id = se.exerciseId
        WHERE s.status = 'FINISHED' AND s.deletedAt IS NULL AND s.startedAt >= :since
          AND se.deletedAt IS NULL AND st.deletedAt IS NULL AND st.completedAt IS NOT NULL
          AND st.type != 'WARMUP' AND st.reps IS NOT NULL
        """,
    )
    fun observeTrendRows(since: Long): Flow<List<TrendRow>>

    // ---- Export (includes soft-deleted rows, so a backup is complete) -------------------

    /** Finished or in-progress workouts that overlap [from, to] (for matching strap recordings). */
    @Query(
        "SELECT * FROM workout_session WHERE deletedAt IS NULL AND status != 'DISCARDED' " +
            "AND startedAt < :to AND (endedAt IS NULL OR endedAt > :from)",
    )
    suspend fun sessionsOverlapping(from: Long, to: Long): List<WorkoutSessionEntity>

    @Query("SELECT * FROM workout_session")
    suspend fun exportSessions(): List<WorkoutSessionEntity>

    @Query("SELECT * FROM session_exercise")
    suspend fun exportSessionExercises(): List<SessionExerciseEntity>

    @Query("SELECT * FROM set_entry")
    suspend fun exportSets(): List<SetEntryEntity>
}

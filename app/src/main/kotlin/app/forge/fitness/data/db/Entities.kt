package app.forge.fitness.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import app.forge.domain.model.Equipment
import app.forge.domain.model.ExerciseCategory
import app.forge.domain.model.LogType
import app.forge.domain.model.Muscle
import app.forge.domain.model.SessionStatus
import app.forge.domain.model.SetType

/*
 * Conventions for every table:
 *  - `id` is a random UUID string, so backups from different devices merge without clashes.
 *  - `createdAt` / `updatedAt` are epoch milliseconds; `updatedAt` decides which copy wins
 *    when merging backups.
 *  - `deletedAt` marks a soft delete. Rows are hidden, not removed, so deletes can be
 *    undone and are carried through backups.
 *  - Weights are always stored in kilograms.
 */

@Entity(
    tableName = "exercise",
    indices = [Index("name"), Index("sourceId", unique = true)],
)
data class ExerciseEntity(
    @PrimaryKey val id: String,
    val name: String,
    val primaryMuscles: List<Muscle>,
    val secondaryMuscles: List<Muscle>,
    val equipment: Equipment?,
    val category: ExerciseCategory,
    /** Which fields a set row shows (weight × reps, reps, time, distance + time). */
    val logType: LogType,
    val mechanic: String?,
    val force: String?,
    val level: String?,
    val instructions: List<String>,
    val images: List<String>,
    val isCustom: Boolean,
    /** ID in the bundled dataset (null for exercises you create yourself). */
    val sourceId: String?,
    @ColumnInfo(defaultValue = "0") val archived: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

@Entity(
    tableName = "workout_session",
    indices = [Index("startedAt"), Index("status")],
)
data class WorkoutSessionEntity(
    @PrimaryKey val id: String,
    val name: String,
    val routineId: String?,
    val startedAt: Long,
    val endedAt: Long?,
    val status: SessionStatus,
    val notes: String?,
    val bodyweightKg: Double?,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

@Entity(
    tableName = "session_exercise",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["exerciseId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("sessionId"), Index("exerciseId")],
)
data class SessionExerciseEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val exerciseId: String,
    val position: Int,
    /** Exercises in the same session that share a group number form a superset. */
    val supersetGroup: Int?,
    val notes: String?,
    val restSeconds: Int?,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

@Entity(
    tableName = "set_entry",
    foreignKeys = [
        ForeignKey(
            entity = SessionExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionExerciseId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("sessionExerciseId"), Index("completedAt")],
)
data class SetEntryEntity(
    @PrimaryKey val id: String,
    val sessionExerciseId: String,
    val position: Int,
    val type: SetType,
    val weightKg: Double?,
    val reps: Int?,
    val rpe: Double?,
    val durationSeconds: Int?,
    val distanceMeters: Double?,
    /** Null until the set is ticked off. */
    val completedAt: Long?,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

/** Small key/value table for app bookkeeping, e.g. which exercise dataset is loaded. */
@Entity(tableName = "app_meta")
data class AppMetaEntity(
    @PrimaryKey val key: String,
    val value: String,
)

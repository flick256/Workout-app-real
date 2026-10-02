package app.forge.fitness.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import app.forge.domain.model.BodyMetricKind
import app.forge.domain.model.Equipment
import app.forge.domain.model.ExerciseCategory
import app.forge.domain.model.LogType
import app.forge.domain.model.Muscle
import app.forge.domain.model.SessionStatus
import app.forge.domain.model.SetType
import kotlinx.serialization.Serializable

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
@Serializable
data class ExerciseEntity(
    @PrimaryKey val id: String,
    val name: String,
    val primaryMuscles: List<Muscle>,
    val secondaryMuscles: List<Muscle>,
    val equipment: Equipment?,
    val category: ExerciseCategory,
    /** Which fields a set row shows (weight × reps, reps, time, distance + time). */
    val logType: LogType,
    /** [app.forge.domain.bodyweight.BodyweightProfile] name for bodyweight moves (v2). */
    val bodyweightProfile: String? = null,
    /** Bench/box height for incline and decline moves, in cm (v2). */
    val bodyweightElevationCm: Double? = null,
    /** [app.forge.domain.dataset.HomePack.Chain] name and position in it (v2). */
    val progressionChain: String? = null,
    val progressionStep: Int? = null,
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
@Serializable
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
@Serializable
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
    /** Targets copied from the routine this workout was started from (v3). */
    val targetSets: Int? = null,
    /** Target reps (or seconds for timed exercises), e.g. 8 and 12 for "8–12". */
    val targetMin: Int? = null,
    val targetMax: Int? = null,
    val targetRpe: Double? = null,
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
@Serializable
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
    /**
     * What you actually moved per rep, in kg (v2): the weight for normal lifts, or your
     * bodyweight share + added weight for calisthenics. Worked out when the set is
     * ticked off, using that day's bodyweight, so old workouts never change.
     */
    val loadKg: Double? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

/**
 * A training program: routines that rotate in order on your training days (v3).
 * [trainingDays] is a bit set, Monday = bit 0 … Sunday = bit 6; 0 = any day.
 */
@Entity(tableName = "program")
@Serializable
data class ProgramEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String?,
    /** Which prebuilt template it came from, if any. */
    val templateKey: String?,
    val trainingDays: Int,
    val isActive: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

/** A saved workout plan you can start with one tap (v3). */
@Entity(tableName = "routine", indices = [Index("programId")])
@Serializable
data class RoutineEntity(
    @PrimaryKey val id: String,
    val name: String,
    val notes: String?,
    /** Optional grouping in the routines list. */
    val folder: String?,
    /** Order in your routines list. */
    val position: Int,
    /** Set when the routine belongs to a program; [programPosition] is its turn in the rotation. */
    val programId: String?,
    val programPosition: Int?,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

/** One exercise in a routine, with its targets (v3). */
@Entity(
    tableName = "routine_exercise",
    foreignKeys = [
        ForeignKey(
            entity = RoutineEntity::class,
            parentColumns = ["id"],
            childColumns = ["routineId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["exerciseId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("routineId"), Index("exerciseId")],
)
@Serializable
data class RoutineExerciseEntity(
    @PrimaryKey val id: String,
    val routineId: String,
    val exerciseId: String,
    val position: Int,
    val supersetGroup: Int?,
    val targetSets: Int,
    /** Reps, or seconds for timed exercises. */
    val targetMin: Int?,
    val targetMax: Int?,
    val targetRpe: Double?,
    /** Null = your default rest time. */
    val restSeconds: Int?,
    val notes: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

/** One measurement on one day: bodyweight, waist, etc. (v2). */
@Serializable
@Entity(tableName = "body_metric", indices = [Index("kind", "measuredAt")])
data class BodyMetricEntity(
    @PrimaryKey val id: String,
    val kind: BodyMetricKind,
    /** Kilograms for weight, centimetres for lengths, percent for body fat. */
    val value: Double,
    val measuredAt: Long,
    val note: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

/** Small key/value table for app bookkeeping, e.g. which exercise dataset is loaded. */
@Entity(tableName = "app_meta")
@Serializable
data class AppMetaEntity(
    @PrimaryKey val key: String,
    val value: String,
)

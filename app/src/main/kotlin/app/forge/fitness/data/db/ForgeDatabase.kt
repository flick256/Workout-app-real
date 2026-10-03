package app.forge.fitness.data.db

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/**
 * The app's single source of truth.
 *
 * Rules for changing it (data integrity first):
 *  1. Bump [version], let the build export the new schema to app/schemas/, and commit it.
 *  2. Add a Migration (or an AutoMigration) plus a migration test.
 *  3. Never use fallbackToDestructiveMigration: it silently deletes your data.
 */
@Database(
    entities = [
        ExerciseEntity::class,
        WorkoutSessionEntity::class,
        SessionExerciseEntity::class,
        SetEntryEntity::class,
        AppMetaEntity::class,
        BodyMetricEntity::class,
        ProgramEntity::class,
        RoutineEntity::class,
        RoutineExerciseEntity::class,
        ProgressPhotoEntity::class,
        ActivitySessionEntity::class,
        DailyHealthEntity::class,
        FoodEntity::class,
        FoodLogEntity::class,
        GoalEntity::class,
        HabitEntity::class,
        HabitCheckEntity::class,
        HeartRateSampleEntity::class,
    ],
    version = 8,
    exportSchema = true,
    autoMigrations = [
        // v2 (M2): bodyweight profiles, progressions, per-set load, body measurements.
        // Only adds columns and a table, so Room can generate it; MigrationTest checks it.
        AutoMigration(from = 1, to = 2),
        // v3 (M3): routines, programs, and targets on session exercises.
        AutoMigration(from = 2, to = 3),
        // v4 (M5): progress photos, demo-data flags.
        AutoMigration(from = 3, to = 4),
        // v5 (M6): sports/cardio activities, daily health data, workout heart rate.
        AutoMigration(from = 4, to = 5),
        // v6 (M7): foods and the food log.
        AutoMigration(from = 5, to = 6),
        // v7 (M8): goals and habits.
        AutoMigration(from = 6, to = 7),
        // v8 (M10): live heart rate during workouts.
        AutoMigration(from = 7, to = 8),
    ],
)
@TypeConverters(Converters::class)
abstract class ForgeDatabase : RoomDatabase() {
    abstract fun exerciseDao(): ExerciseDao
    abstract fun workoutDao(): WorkoutDao
    abstract fun metaDao(): MetaDao
    abstract fun bodyMetricDao(): BodyMetricDao
    abstract fun routineDao(): RoutineDao
    abstract fun photoDao(): PhotoDao
    abstract fun demoDao(): DemoDao
    abstract fun activityDao(): ActivityDao
    abstract fun foodDao(): FoodDao
    abstract fun goalDao(): GoalDao
    abstract fun backupDao(): BackupDao
    abstract fun heartRateDao(): HeartRateDao

    companion object {
        const val NAME = "forge.db"
    }
}

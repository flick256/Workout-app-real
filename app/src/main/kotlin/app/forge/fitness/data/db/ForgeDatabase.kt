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
    ],
    version = 2,
    exportSchema = true,
    autoMigrations = [
        // v2 (M2): bodyweight profiles, progressions, per-set load, body measurements.
        // Only adds columns and a table, so Room can generate it; MigrationTest checks it.
        AutoMigration(from = 1, to = 2),
    ],
)
@TypeConverters(Converters::class)
abstract class ForgeDatabase : RoomDatabase() {
    abstract fun exerciseDao(): ExerciseDao
    abstract fun workoutDao(): WorkoutDao
    abstract fun metaDao(): MetaDao
    abstract fun bodyMetricDao(): BodyMetricDao

    companion object {
        const val NAME = "forge.db"
    }
}

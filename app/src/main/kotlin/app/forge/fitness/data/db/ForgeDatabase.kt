package app.forge.fitness.data.db

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
 *
 * Version 1 is a draft until Milestone 1 ships, because nothing opens the database in M0.
 */
@Database(
    entities = [
        ExerciseEntity::class,
        WorkoutSessionEntity::class,
        SessionExerciseEntity::class,
        SetEntryEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class ForgeDatabase : RoomDatabase() {
    companion object {
        const val NAME = "forge.db"
    }
}

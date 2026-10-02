package app.forge.fitness.data.exercise

import android.content.Context
import android.util.Log
import androidx.room.withTransaction
import app.forge.domain.dataset.ExerciseDataset
import app.forge.domain.dataset.ExerciseSeed
import app.forge.fitness.data.db.AppMetaEntity
import app.forge.fitness.data.db.ExerciseEntity
import app.forge.fitness.data.db.ForgeDatabase
import app.forge.fitness.di.TimeSource
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Loads the bundled exercise library (assets/exercises.json) into the database on
 * first launch, and again whenever [ExerciseDataset.VERSION] goes up.
 */
@Singleton
class ExerciseSeeder @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: ForgeDatabase,
    private val time: TimeSource,
) {
    private val mutex = Mutex()

    suspend fun seedIfNeeded() = mutex.withLock {
        withContext(Dispatchers.IO) {
            val meta = db.metaDao()
            val loaded = meta.get(KEY_VERSION)?.toIntOrNull() ?: 0
            if (loaded >= ExerciseDataset.VERSION && db.exerciseDao().bundledCount() > 0) return@withContext

            val started = System.nanoTime()
            val json = context.assets.open(ASSET).bufferedReader().use { it.readText() }
            val now = time.now()
            val entities = ExerciseDataset.parse(json).map { it.toEntity(now) }
            db.withTransaction {
                db.exerciseDao().upsertBundled(entities)
                meta.put(AppMetaEntity(KEY_VERSION, ExerciseDataset.VERSION.toString()))
            }
            Log.i(TAG, "Seeded ${entities.size} exercises in ${(System.nanoTime() - started) / 1_000_000} ms")
        }
    }

    private fun ExerciseSeed.toEntity(now: Long) = ExerciseEntity(
        id = id,
        name = name,
        primaryMuscles = primaryMuscles,
        secondaryMuscles = secondaryMuscles,
        equipment = equipment,
        category = category,
        logType = logType,
        mechanic = mechanic,
        force = force,
        level = level,
        instructions = instructions,
        images = images,
        isCustom = false,
        sourceId = sourceId,
        createdAt = now,
        updatedAt = now,
    )

    private companion object {
        const val TAG = "ExerciseSeeder"
        const val ASSET = "exercises.json"
        const val KEY_VERSION = "exercise_dataset_version"
    }
}

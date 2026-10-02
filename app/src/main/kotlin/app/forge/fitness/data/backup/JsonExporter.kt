package app.forge.fitness.data.backup

import android.content.Context
import android.net.Uri
import app.forge.fitness.data.db.ExerciseDao
import app.forge.fitness.data.db.ExerciseEntity
import app.forge.fitness.data.db.SessionExerciseEntity
import app.forge.fitness.data.db.SetEntryEntity
import app.forge.fitness.data.db.WorkoutDao
import app.forge.fitness.data.db.WorkoutSessionEntity
import app.forge.fitness.data.prefs.UserPreferencesRepository
import app.forge.fitness.di.TimeSource
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * A complete, human-readable copy of your data. Bundled library exercises are left out
 * (every install has them, with the same IDs); your custom exercises are included.
 * Soft-deleted rows are included and marked with `deletedAt`, so nothing is lost.
 */
@Serializable
data class ForgeExport(
    val app: String = "Forge",
    val formatVersion: Int = 1,
    val databaseVersion: Int,
    val exportedAt: Long,
    val settings: ExportedSettings,
    val customExercises: List<ExerciseEntity>,
    val sessions: List<WorkoutSessionEntity>,
    val sessionExercises: List<SessionExerciseEntity>,
    val sets: List<SetEntryEntity>,
)

@Serializable
data class ExportedSettings(
    val weightUnit: String,
    val defaultRestSeconds: Int,
    val equipment: List<String>,
    val ownedWeightsKg: Map<String, List<Double>>,
)

data class ExportResult(val workouts: Int, val sets: Int, val bytes: Int)

@Singleton
class JsonExporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val exercises: ExerciseDao,
    private val workouts: WorkoutDao,
    private val preferences: UserPreferencesRepository,
    private val time: TimeSource,
) {
    private val json = Json { prettyPrint = true; encodeDefaults = true }

    suspend fun exportTo(uri: Uri): ExportResult = withContext(Dispatchers.IO) {
        val prefs = preferences.preferences.first()
        val export = ForgeExport(
            databaseVersion = DATABASE_VERSION,
            exportedAt = time.now(),
            settings = ExportedSettings(
                weightUnit = prefs.weightUnit.name,
                defaultRestSeconds = prefs.defaultRestSeconds,
                equipment = prefs.equipment.map { it.name }.sorted(),
                ownedWeightsKg = prefs.ownedWeights.mapKeys { it.key.name },
            ),
            customExercises = exercises.exportAll().filter { it.isCustom },
            sessions = workouts.exportSessions(),
            sessionExercises = workouts.exportSessionExercises(),
            sets = workouts.exportSets(),
        )
        val bytes = json.encodeToString(ForgeExport.serializer(), export).toByteArray()
        context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(bytes) }
            ?: error("Couldn't open the file for writing")
        ExportResult(
            workouts = export.sessions.count { it.deletedAt == null && it.status.name == "FINISHED" },
            sets = export.sets.count { it.deletedAt == null && it.completedAt != null },
            bytes = bytes.size,
        )
    }

    private companion object {
        const val DATABASE_VERSION = 1
    }
}

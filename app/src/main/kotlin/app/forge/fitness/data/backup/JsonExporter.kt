package app.forge.fitness.data.backup

import android.content.Context
import android.net.Uri
import app.forge.fitness.data.db.ActivityDao
import app.forge.fitness.data.db.ActivitySessionEntity
import app.forge.fitness.data.db.BodyMetricDao
import app.forge.fitness.data.db.DailyHealthEntity
import app.forge.fitness.data.db.BodyMetricEntity
import app.forge.fitness.data.db.ExerciseDao
import app.forge.fitness.data.db.FoodDao
import app.forge.fitness.data.db.FoodEntity
import app.forge.fitness.data.db.FoodLogEntity
import app.forge.fitness.data.db.ExerciseEntity
import app.forge.fitness.data.db.PhotoDao
import app.forge.fitness.data.db.ProgramEntity
import app.forge.fitness.data.db.ProgressPhotoEntity
import app.forge.fitness.data.db.RoutineDao
import app.forge.fitness.data.db.RoutineEntity
import app.forge.fitness.data.db.RoutineExerciseEntity
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
    val formatVersion: Int = 6,
    val databaseVersion: Int,
    val exportedAt: Long,
    val settings: ExportedSettings,
    val customExercises: List<ExerciseEntity>,
    val sessions: List<WorkoutSessionEntity>,
    val sessionExercises: List<SessionExerciseEntity>,
    val sets: List<SetEntryEntity>,
    val bodyMetrics: List<BodyMetricEntity> = emptyList(),
    val programs: List<ProgramEntity> = emptyList(),
    val routines: List<RoutineEntity> = emptyList(),
    val routineExercises: List<RoutineExerciseEntity> = emptyList(),
    /** Photo details only; the images themselves stay on the phone. */
    val progressPhotos: List<ProgressPhotoEntity> = emptyList(),
    /** Sports, cardio and mobility sessions (v5). */
    val activities: List<ActivitySessionEntity> = emptyList(),
    /** Daily steps, sleep, HRV and resting heart rate from Health Connect (v5). */
    val dailyHealth: List<DailyHealthEntity> = emptyList(),
    /** Foods (yours and cached Open Food Facts ones) and everything you logged (v6). */
    val foods: List<FoodEntity> = emptyList(),
    val foodLog: List<FoodLogEntity> = emptyList(),
)

@Serializable
data class ExportedSettings(
    val weightUnit: String,
    val defaultRestSeconds: Int,
    val equipment: List<String>,
    val ownedWeightsKg: Map<String, List<Double>>,
    val heightCm: Double? = null,
    val sex: String? = null,
    val birthYear: Int? = null,
    val activityLevel: String? = null,
    val nutritionGoal: String? = null,
)

data class ExportResult(val workouts: Int, val sets: Int, val bytes: Int)

@Singleton
class JsonExporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val exercises: ExerciseDao,
    private val workouts: WorkoutDao,
    private val bodyMetrics: BodyMetricDao,
    private val routines: RoutineDao,
    private val photos: PhotoDao,
    private val activities: ActivityDao,
    private val foods: FoodDao,
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
                heightCm = prefs.heightCm,
                sex = prefs.sex?.name,
                birthYear = prefs.birthYear,
                activityLevel = prefs.activityLevel.name,
                nutritionGoal = prefs.nutritionGoal.name,
            ),
            customExercises = exercises.exportAll().filter { it.isCustom },
            sessions = workouts.exportSessions(),
            sessionExercises = workouts.exportSessionExercises(),
            sets = workouts.exportSets(),
            bodyMetrics = bodyMetrics.exportAll(),
            programs = routines.exportPrograms(),
            routines = routines.exportRoutines(),
            routineExercises = routines.exportRoutineExercises(),
            progressPhotos = photos.exportAll(),
            activities = activities.exportAll(),
            dailyHealth = activities.exportDaily(),
            foods = foods.exportFoods(),
            foodLog = foods.exportLog(),
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
        const val DATABASE_VERSION = 6
    }
}

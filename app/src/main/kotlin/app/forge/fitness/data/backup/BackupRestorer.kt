package app.forge.fitness.data.backup

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import app.forge.domain.backup.BackupMerge
import app.forge.domain.backup.MergePlan
import app.forge.domain.model.Equipment
import app.forge.domain.model.WeightUnit
import app.forge.domain.nutrition.ActivityLevel
import app.forge.domain.nutrition.NutritionGoal
import app.forge.domain.nutrition.Sex
import app.forge.fitness.data.db.ForgeDatabase
import app.forge.fitness.data.photos.PhotoRepository
import app.forge.fitness.data.prefs.UserPreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/** What a restore did, table by table, in words. */
data class RestoreReport(
    val added: Int,
    val updated: Int,
    val kept: Int,
    val skipped: Int,
    val lines: List<String>,
    val snapshotName: String?,
) {
    val summary: String get() = "Added $added, updated $updated, kept $kept of yours" + (if (skipped > 0) ", skipped $skipped" else "") + "."
}

/** A quick look at a backup before restoring it. */
data class BackupPreview(val exportedAt: Long, val formatVersion: Int, val workouts: Int, val foodEntries: Int, val total: Int)

/**
 * Restores a Forge backup by merging (see [BackupMerge]): nothing on the phone is deleted,
 * and a snapshot of the phone's data is saved first, so a restore can always be undone
 * by restoring that snapshot.
 */
@Singleton
class BackupRestorer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: ForgeDatabase,
    private val exporter: JsonExporter,
    private val snapshots: SnapshotStore,
    private val photos: PhotoRepository,
    private val preferences: UserPreferencesRepository,
) {
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    suspend fun read(uri: Uri): ForgeExport = withContext(Dispatchers.IO) {
        val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
            ?: error("Couldn't open that file")
        decode(text)
    }

    fun decode(text: String): ForgeExport {
        val export = runCatching { json.decodeFromString(ForgeExport.serializer(), text) }
            .getOrElse { error("That isn't a Forge backup (${it.message?.take(80)})") }
        require(export.app == "Forge") { "That isn't a Forge backup" }
        require(export.databaseVersion <= JsonExporter.DATABASE_VERSION) {
            "That backup is from a newer version of Forge. Update Forge first."
        }
        return export
    }

    fun preview(export: ForgeExport) = BackupPreview(
        exportedAt = export.exportedAt,
        formatVersion = export.formatVersion,
        workouts = export.sessions.count { it.deletedAt == null && it.status.name == "FINISHED" },
        foodEntries = export.foodLog.count { it.deletedAt == null },
        total = with(export) {
            customExercises.size + sessions.size + sessionExercises.size + sets.size + bodyMetrics.size + programs.size +
                routines.size + routineExercises.size + progressPhotos.size + activities.size + dailyHealth.size +
                foods.size + foodLog.size + goals.size + habits.size + habitChecks.size + heartRateSamples.size
        },
    )

    /**
     * [exact]: make the phone match the backup (used to go back to a snapshot). Everything not
     * in it is removed, except progress photos. Otherwise the backup is merged in.
     */
    suspend fun restore(export: ForgeExport, includeSettings: Boolean, exact: Boolean = false): RestoreReport = withContext(Dispatchers.IO) {
        // A copy of everything as it is now, before touching anything. No copy, no restore.
        val snapshot = try {
            snapshots.save("before-restore")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error("Couldn't save a safety snapshot first, so nothing was restored (${e.message})")
        }
        val dao = db.backupDao()
        val lines = mutableListOf<String>()
        var added = 0
        var updated = 0
        var kept = 0
        var skipped = 0

        fun <T> count(label: String, plan: MergePlan<T>) {
            added += plan.toInsert.size
            updated += plan.toUpdate.size
            kept += plan.kept
            if (plan.changes > 0) lines += "$label: ${plan.toInsert.size} added, ${plan.toUpdate.size} updated"
        }

        db.withTransaction {
            if (exact) dao.clearUserData()
            // Read inside the transaction, so nothing written meanwhile is overwritten by an older copy.
            val current = exporter.build()
            val exercises = BackupMerge.plan(current.customExercises, export.customExercises, { it.id }, { it.updatedAt })
            dao.exercises(exercises.toInsert + exercises.toUpdate)
            count("Custom exercises", exercises)
            val knownExercises = dao.exerciseIds().toSet()

            val sessions = BackupMerge.plan(current.sessions, export.sessions, { it.id }, { it.updatedAt })
            dao.sessions(sessions.toInsert + sessions.toUpdate)
            count("Workouts", sessions)
            val knownSessions = current.sessions.map { it.id }.toSet() + export.sessions.map { it.id }

            // Workouts can reference library exercises; skip any this install doesn't have.
            val sessionExercisesIn = export.sessionExercises.filter { it.exerciseId in knownExercises && it.sessionId in knownSessions }
            skipped += export.sessionExercises.size - sessionExercisesIn.size
            val sessionExercises = BackupMerge.plan(current.sessionExercises, sessionExercisesIn, { it.id }, { it.updatedAt })
            dao.sessionExercises(sessionExercises.toInsert + sessionExercises.toUpdate)
            count("Exercises in workouts", sessionExercises)
            val knownItems = current.sessionExercises.map { it.id }.toSet() + sessionExercisesIn.map { it.id }

            val setsIn = export.sets.filter { it.sessionExerciseId in knownItems }
            skipped += export.sets.size - setsIn.size
            val sets = BackupMerge.plan(current.sets, setsIn, { it.id }, { it.updatedAt })
            dao.sets(sets.toInsert + sets.toUpdate)
            count("Sets", sets)

            val body = BackupMerge.plan(current.bodyMetrics, export.bodyMetrics, { it.id }, { it.updatedAt })
            dao.bodyMetrics(body.toInsert + body.toUpdate)
            count("Body measurements", body)

            val programs = BackupMerge.plan(current.programs, export.programs, { it.id }, { it.updatedAt })
            dao.programs(programs.toInsert + programs.toUpdate)
            count("Programs", programs)
            val routines = BackupMerge.plan(current.routines, export.routines, { it.id }, { it.updatedAt })
            dao.routines(routines.toInsert + routines.toUpdate)
            count("Routines", routines)
            val knownRoutines = current.routines.map { it.id }.toSet() + export.routines.map { it.id }
            val routineItemsIn = export.routineExercises.filter { it.exerciseId in knownExercises && it.routineId in knownRoutines }
            skipped += export.routineExercises.size - routineItemsIn.size
            val routineItems = BackupMerge.plan(current.routineExercises, routineItemsIn, { it.id }, { it.updatedAt })
            dao.routineExercises(routineItems.toInsert + routineItems.toUpdate)
            count("Routine exercises", routineItems)
            // Only one program can be the active one: keep the most recently changed.
            dao.activePrograms().firstOrNull()?.let { dao.deactivateOtherPrograms(it.id) }

            // Photo files aren't in backups; only bring back details for photos still on this phone.
            val photosIn = export.progressPhotos.filter { photos.fileFor(it).exists() }
            skipped += export.progressPhotos.size - photosIn.size
            val photoPlan = BackupMerge.plan(current.progressPhotos, photosIn, { it.id }, { it.updatedAt })
            dao.photos(photoPlan.toInsert + photoPlan.toUpdate)
            count("Progress photos", photoPlan)

            // A Health Connect record can only be on one activity (unique). If the phone already has it
            // under another id (e.g. synced again after a reinstall), the backup's copy takes that id.
            val localByExternal = current.activities.filter { it.externalId != null }.associateBy { it.externalId!! }
            val activitiesIn = export.activities.map { a ->
                val local = a.externalId?.let(localByExternal::get)
                if (local != null && local.id != a.id) a.copy(id = local.id) else a
            }
            val activities = BackupMerge.plan(current.activities, activitiesIn, { it.id }, { it.updatedAt })
            dao.activities(activities.toInsert + activities.toUpdate)
            count("Activities", activities)

            val daily = BackupMerge.plan(current.dailyHealth, export.dailyHealth, { it.epochDay.toString() }, { it.updatedAt })
            dao.dailyHealth(daily.toInsert + daily.toUpdate)
            count("Daily health", daily)

            // Barcodes are unique too: a food the phone already has under another id keeps the
            // phone's id, and the food log is pointed at it.
            val localByBarcode = current.foods.filter { it.barcode != null }.associateBy { it.barcode!! }
            val foodIds = mutableMapOf<String, String>()
            val foodsIn = export.foods.map { f ->
                val local = f.barcode?.let(localByBarcode::get)
                if (local != null && local.id != f.id) { foodIds[f.id] = local.id; f.copy(id = local.id) } else f
            }
            val foods = BackupMerge.plan(current.foods, foodsIn, { it.id }, { it.updatedAt })
            dao.foods(foods.toInsert + foods.toUpdate)
            count("Foods", foods)
            val logIn = export.foodLog.map { e -> e.foodId?.let(foodIds::get)?.let { e.copy(foodId = it) } ?: e }
            val foodLog = BackupMerge.plan(current.foodLog, logIn, { it.id }, { it.updatedAt })
            dao.foodLog(foodLog.toInsert + foodLog.toUpdate)
            count("Food log", foodLog)

            val goals = BackupMerge.plan(current.goals, export.goals, { it.id }, { it.updatedAt })
            dao.goals(goals.toInsert + goals.toUpdate)
            count("Goals", goals)
            val habits = BackupMerge.plan(current.habits, export.habits, { it.id }, { it.updatedAt })
            dao.habits(habits.toInsert + habits.toUpdate)
            count("Habits", habits)
            val checks = BackupMerge.plan(current.habitChecks, export.habitChecks, { "${it.habitId}:${it.epochDay}" }, { it.checkedAt })
            dao.habitChecks(checks.toInsert + checks.toUpdate)
            count("Habit ticks", checks)

            // Heart-rate readings never change once recorded, so new ones are simply added.
            val heartIn = export.heartRateSamples.filter { it.sessionId in knownSessions }
            skipped += export.heartRateSamples.size - heartIn.size
            val heart = BackupMerge.plan(current.heartRateSamples, heartIn, { "${it.sessionId}:${it.atMillis}" }, { it.atMillis })
            dao.heartRateSamples(heart.toInsert)
            count("Heart-rate readings", heart)
        }

        if (includeSettings) applySettings(export.settings)
        RestoreReport(added, updated, kept, skipped, lines, snapshot.name)
    }

    private suspend fun applySettings(s: ExportedSettings) {
        WeightUnit.entries.firstOrNull { it.name == s.weightUnit }?.let { preferences.setWeightUnit(it) }
        preferences.setDefaultRestSeconds(s.defaultRestSeconds)
        preferences.setEquipment(s.equipment.mapNotNull { n -> Equipment.entries.firstOrNull { it.name == n } }.toSet())
        s.ownedWeightsKg.forEach { (name, kg) -> Equipment.entries.firstOrNull { it.name == name }?.let { preferences.setOwnedWeights(it, kg) } }
        s.heightCm?.let { preferences.setHeightCm(it) }
        s.sex?.let { n -> Sex.entries.firstOrNull { it.name == n } }?.let { preferences.setSex(it) }
        s.birthYear?.let { preferences.setBirthYear(it) }
        s.activityLevel?.let { n -> ActivityLevel.entries.firstOrNull { it.name == n } }?.let { preferences.setActivityLevel(it) }
        s.nutritionGoal?.let { n -> NutritionGoal.entries.firstOrNull { it.name == n } }?.let { preferences.setNutritionGoal(it) }
    }
}

package app.forge.fitness.data.backup

import android.content.Context
import android.net.Uri
import app.forge.fitness.data.db.ExerciseDao
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.OutputStream
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Spreadsheet-friendly export: one .zip with a CSV per kind of data (sets, food, body,
 * activities, habits). For looking at your data; restoring uses the JSON backup.
 */
@Singleton
class CsvExporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val exporter: JsonExporter,
    private val exercises: ExerciseDao,
) {
    private val zone = ZoneId.systemDefault()
    private val dateTime = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(zone)
    private fun t(ms: Long?) = ms?.let { dateTime.format(Instant.ofEpochMilli(it)) }.orEmpty()
    private fun d(epochDay: Long) = LocalDate.ofEpochDay(epochDay).toString()

    suspend fun exportTo(uri: Uri): Int = withContext(Dispatchers.IO) {
        val e = exporter.build()
        val names = exercises.observeAll().first().associate { it.id to it.name } + e.customExercises.associate { it.id to it.name }
        val sessions = e.sessions.filter { it.deletedAt == null && it.status.name == "FINISHED" }.associateBy { it.id }
        val items = e.sessionExercises.filter { it.deletedAt == null }.associateBy { it.id }
        val files = linkedMapOf<String, List<List<Any?>>>()

        files["sets.csv"] = listOf(listOf("date", "workout", "exercise", "set", "type", "weight_kg", "reps", "rpe", "seconds", "distance_m", "load_kg")) +
            e.sets.filter { it.deletedAt == null && it.completedAt != null }.mapNotNull { s ->
                val item = items[s.sessionExerciseId] ?: return@mapNotNull null
                val session = sessions[item.sessionId] ?: return@mapNotNull null
                listOf(t(session.startedAt), session.name, names[item.exerciseId] ?: item.exerciseId, s.position + 1, s.type.name,
                    s.weightKg, s.reps, s.rpe, s.durationSeconds, s.distanceMeters, s.loadKg)
            }.sortedBy { it[0] as String }
        files["workouts.csv"] = listOf(listOf("start", "end", "name", "notes", "bodyweight_kg", "avg_hr", "max_hr")) +
            sessions.values.sortedBy { it.startedAt }.map { listOf(t(it.startedAt), t(it.endedAt), it.name, it.notes, it.bodyweightKg, it.avgHeartRate, it.maxHeartRate) }
        files["food.csv"] = listOf(listOf("date", "meal", "food", "grams", "kcal", "protein_g", "carbs_g", "fat_g", "fibre_g", "sugars_g", "salt_g")) +
            e.foodLog.filter { it.deletedAt == null }.sortedBy { it.loggedAt }.map {
                listOf(d(it.epochDay), it.meal, it.name, it.grams, it.kcal.r(), it.proteinG.r(), it.carbsG.r(), it.fatG.r(), it.fiberG?.r(), it.sugarG?.r(), it.saltG?.r())
            }
        files["body.csv"] = listOf(listOf("date", "measurement", "value", "note")) +
            e.bodyMetrics.filter { it.deletedAt == null }.sortedBy { it.measuredAt }.map { listOf(t(it.measuredAt), it.kind.name, it.value, it.note) }
        files["activities.csv"] = listOf(listOf("start", "sport", "name", "minutes", "effort", "distance_m", "avg_hr", "max_hr", "kcal", "source")) +
            e.activities.filter { it.deletedAt == null }.sortedBy { it.startedAt }.map {
                listOf(t(it.startedAt), it.sport, it.title, it.durationMinutes, it.intensity, it.distanceMeters, it.avgHeartRate, it.maxHeartRate, it.calories?.r(), it.source)
            }
        val habitNames = e.habits.associate { it.id to it.name }
        files["habits.csv"] = listOf(listOf("date", "habit")) +
            e.habitChecks.sortedBy { it.epochDay }.map { listOf(d(it.epochDay), habitNames[it.habitId] ?: it.habitId) }
        files["daily_health.csv"] = listOf(listOf("date", "steps", "sleep_min", "resting_hr", "hrv_ms")) +
            e.dailyHealth.sortedBy { it.epochDay }.map { listOf(d(it.epochDay), it.steps, it.sleepMinutes, it.restingHr, it.hrvMs) }

        val out: OutputStream = runCatching { context.contentResolver.openOutputStream(uri, "wt") }.getOrNull()
            ?: context.contentResolver.openOutputStream(uri, "w") ?: error("Couldn't open the file for writing")
        ZipOutputStream(out).use { zip ->
            files.forEach { (name, rows) ->
                zip.putNextEntry(ZipEntry(name))
                // A byte-order mark so Excel opens UTF-8 (e.g. "×", accents) correctly.
                zip.write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))
                zip.write(rows.joinToString("\r\n") { row -> row.joinToString(",") { cell(it) } }.toByteArray())
                zip.closeEntry()
            }
        }
        files.values.sumOf { it.size - 1 }
    }

    private fun Double.r(): Double = Math.round(this * 10) / 10.0

    /** RFC 4180 quoting: wrap in quotes when needed, double any quotes inside. */
    private fun cell(value: Any?): String {
        val s = value?.toString() ?: return ""
        return if (s.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + s.replace("\"", "\"\"") + "\"" else s
    }
}

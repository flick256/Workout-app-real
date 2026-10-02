package app.forge.fitness.data.backup

import app.forge.domain.model.SessionStatus
import app.forge.domain.model.SetType
import app.forge.domain.model.BodyMetricKind
import app.forge.fitness.data.TestDb
import app.forge.fitness.data.db.BodyMetricEntity
import app.forge.fitness.data.db.SessionExerciseEntity
import app.forge.fitness.data.db.SetEntryEntity
import app.forge.fitness.data.db.WorkoutSessionEntity
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The export must round-trip exactly, or it isn't a backup. */
class ForgeExportTest {

    @Test
    fun exportRoundTripsWithoutLoss() {
        val export = ForgeExport(
            databaseVersion = 2,
            exportedAt = 1_700_000_000_000,
            settings = ExportedSettings(
                weightUnit = "KG",
                defaultRestSeconds = 90,
                equipment = listOf("BODY_ONLY", "WEIGHTED_BAG"),
                ownedWeightsKg = mapOf("WEIGHTED_BAG" to listOf(10.0, 15.0)),
                heightCm = 178.0,
            ),
            customExercises = listOf(TestDb.exercise("bag-squat", "Bag Squat")),
            sessions = listOf(
                WorkoutSessionEntity(
                    id = "s1", name = "Evening workout", routineId = null, startedAt = 1L, endedAt = 2L,
                    status = SessionStatus.FINISHED, notes = "felt good", bodyweightKg = 68.5, createdAt = 1L, updatedAt = 2L,
                ),
            ),
            sessionExercises = listOf(
                SessionExerciseEntity(
                    id = "se1", sessionId = "s1", exerciseId = "bag-squat", position = 0, supersetGroup = null,
                    notes = null, restSeconds = 120, createdAt = 1L, updatedAt = 1L,
                ),
            ),
            sets = listOf(
                SetEntryEntity(
                    id = "x1", sessionExerciseId = "se1", position = 0, type = SetType.WORKING, weightKg = 15.0,
                    reps = 12, rpe = 8.5, durationSeconds = null, distanceMeters = null, completedAt = 2L,
                    loadKg = 15.0, createdAt = 1L, updatedAt = 2L,
                ),
            ),
            bodyMetrics = listOf(
                BodyMetricEntity(
                    id = "b1", kind = BodyMetricKind.WEIGHT, value = 68.5, measuredAt = 1L, createdAt = 1L, updatedAt = 1L,
                ),
            ),
        )
        val json = Json { prettyPrint = true; encodeDefaults = true }
        val text = json.encodeToString(ForgeExport.serializer(), export)
        assertEquals(export, json.decodeFromString(ForgeExport.serializer(), text))
        assertTrue("human-readable enum names", "\"WORKING\"" in text && "\"FINISHED\"" in text)
    }
}

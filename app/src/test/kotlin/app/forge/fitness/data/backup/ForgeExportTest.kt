package app.forge.fitness.data.backup

import app.forge.domain.model.SessionStatus
import app.forge.domain.model.SetType
import app.forge.fitness.data.TestDb
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
            databaseVersion = 1,
            exportedAt = 1_700_000_000_000,
            settings = ExportedSettings("KG", 90, listOf("BODY_ONLY", "WEIGHTED_BAG"), mapOf("WEIGHTED_BAG" to listOf(10.0, 15.0))),
            customExercises = listOf(TestDb.exercise("bag-squat", "Bag Squat")),
            sessions = listOf(
                WorkoutSessionEntity("s1", "Evening workout", null, 1L, 2L, SessionStatus.FINISHED, "felt good", null, 1L, 2L),
            ),
            sessionExercises = listOf(SessionExerciseEntity("se1", "s1", "bag-squat", 0, null, null, 120, 1L, 1L)),
            sets = listOf(SetEntryEntity("x1", "se1", 0, SetType.WORKING, 15.0, 12, 8.5, null, null, 2L, 1L, 2L)),
        )
        val json = Json { prettyPrint = true; encodeDefaults = true }
        val text = json.encodeToString(ForgeExport.serializer(), export)
        assertEquals(export, json.decodeFromString(ForgeExport.serializer(), text))
        assertTrue("human-readable enum names", "\"WORKING\"" in text && "\"FINISHED\"" in text)
    }
}

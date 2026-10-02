package app.forge.fitness.data.workout

import app.forge.domain.model.LogType
import app.forge.domain.model.SetType
import app.forge.fitness.data.TestDb
import app.forge.fitness.data.db.SetEntryEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LoadsTest {
    private fun set(weight: Double?, reps: Int = 10) = SetEntryEntity(
        id = "s", sessionExerciseId = "se", position = 0, type = SetType.WORKING, weightKg = weight, reps = reps,
        rpe = null, durationSeconds = null, distanceMeters = null, completedAt = 1L, createdAt = 0, updatedAt = 0,
    )

    private val pullUp = TestDb.exercise("pu", logType = LogType.REPS).copy(bodyweightProfile = "PULL_UP")
    private val press = TestDb.exercise("press")
    private val plank = TestDb.exercise("plank", logType = LogType.DURATION)

    @Test
    fun bodyweightMoveUsesBodyweightShareAndAddedWeight() {
        assertEquals(70 * 0.95 + 10, Loads.loadFor(set(10.0), pullUp, 70.0, 180.0)!!, 0.001)
    }

    @Test
    fun withoutBodyweightOnlyAddedWeightCounts() {
        assertEquals(10.0, Loads.loadFor(set(10.0), pullUp, null, null)!!, 0.0)
        assertNull(Loads.loadFor(set(null), pullUp, null, null))
    }

    @Test
    fun weightedLiftUsesTheWeight() {
        assertEquals(22.5, Loads.loadFor(set(22.5), press, 70.0, null)!!, 0.0)
    }

    @Test
    fun timedSetsHaveNoLoad() {
        assertNull(Loads.loadFor(set(null), plank, 70.0, null))
    }
}

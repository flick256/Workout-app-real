package app.forge.domain.activity

import app.forge.domain.model.Muscle
import app.forge.domain.suggest.Recovery
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ActivityFatigueTest {
    @Test
    fun `a football game counts like a few sets of leg work`() {
        assertEquals(4.2, ActivityFatigue.equivalentSets(60, 7), 1e-9)
        val work = ActivityFatigue.muscleWork(Sport.FOOTBALL, 0, 60, 7)!!
        assertTrue(Muscle.QUADRICEPS in work.primary)
        assertEquals(60 * 60_000L, work.atMillis)
    }

    @Test
    fun `long activities are capped and gentle ones ignored`() {
        assertEquals(ActivityFatigue.MAX_SETS, ActivityFatigue.equivalentSets(300, 6))
        assertNull(ActivityFatigue.muscleWork(Sport.STRETCHING, 0, 30, 2))
        assertNull(ActivityFatigue.muscleWork(Sport.YOGA, 0, 45, 3))
        assertNull(ActivityFatigue.muscleWork(Sport.FOOTBALL, 0, 0, 7))
    }

    @Test
    fun `football yesterday shows up as tired legs`() {
        val hour = 3_600_000L
        val now = 100 * hour
        val work = ActivityFatigue.muscleWork(Sport.FOOTBALL, now - 20 * hour, 90, 8)!!
        val quads = Recovery.status(listOf(work), now).first { it.muscle == Muscle.QUADRICEPS }
        assertTrue(quads.readiness < 0.3, "quads ${quads.readiness}")
    }

    @Test
    fun `unknown sport keys fall back to other`() {
        assertEquals(Sport.OTHER, Sport.fromKey("UNDERWATER_BASKET_WEAVING"))
        assertEquals(Sport.RUNNING, Sport.fromKey("RUNNING"))
    }
}

class ReadinessCheckTest {
    @Test
    fun `no data is unknown`() {
        assertEquals(ReadinessLevel.UNKNOWN, ReadinessCheck.assess(ReadinessInput()).level)
    }

    @Test
    fun `good sleep and normal signals are good`() {
        val r = ReadinessCheck.assess(ReadinessInput(480, 65.0, 62.0, 55.0, 55.0))
        assertEquals(ReadinessLevel.GOOD, r.level)
        assertEquals(3, r.reasons.size)
    }

    @Test
    fun `short sleep plus low HRV says take it easier`() {
        val r = ReadinessCheck.assess(ReadinessInput(sleepMinutes = 330, hrvMs = 48.0, hrvBaselineMs = 62.0))
        assertEquals(ReadinessLevel.LOW, r.level)
        assertTrue(r.reasons.any { "below your normal" in it })
        assertTrue(r.advice.contains("lighter"))
    }

    @Test
    fun `one mild signal is just normal`() {
        val r = ReadinessCheck.assess(ReadinessInput(sleepMinutes = 400, restingHr = 58.0, restingHrBaseline = 57.0))
        assertEquals(ReadinessLevel.OK, r.level)
    }

    @Test
    fun `elevated resting heart rate counts`() {
        val r = ReadinessCheck.assess(ReadinessInput(sleepMinutes = 350, restingHr = 63.0, restingHrBaseline = 57.0))
        assertEquals(ReadinessLevel.LOW, r.level)
    }

    @Test
    fun `heart rate maps to effort`() {
        assertEquals(2, ActivityFatigue.intensityFromHeartRate(80))
        assertEquals(6, ActivityFatigue.intensityFromHeartRate(150))
        assertEquals(10, ActivityFatigue.intensityFromHeartRate(195))
    }
}

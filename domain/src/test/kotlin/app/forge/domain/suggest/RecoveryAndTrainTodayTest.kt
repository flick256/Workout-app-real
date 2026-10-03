package app.forge.domain.suggest

import app.forge.domain.model.Muscle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RecoveryAndTrainTodayTest {
    private val hour = 3_600_000L
    private val now = 1_000_000_000_000L

    private fun work(hoursAgo: Long, sets: Double, vararg primary: Muscle, secondary: List<Muscle> = emptyList()) =
        MuscleWork(now - hoursAgo * hour, primary.toList(), secondary, sets)

    private fun statusOf(work: List<MuscleWork>) = Recovery.status(work, now).associateBy { it.muscle }

    @Test
    fun `fresh muscles are fully ready and under target`() {
        val s = statusOf(emptyList()).getValue(Muscle.CHEST)
        assertEquals(1.0, s.readiness)
        assertEquals(1.0, s.deficit)
    }

    @Test
    fun `fatigue halves after one half-life`() {
        // 6 chest sets just now: not recovered. 72 h later (chest's half-life): half the fatigue.
        assertEquals(0.0, statusOf(listOf(work(0, 6.0, Muscle.CHEST))).getValue(Muscle.CHEST).readiness, 1e-9)
        assertEquals(0.5, statusOf(listOf(work(72, 6.0, Muscle.CHEST))).getValue(Muscle.CHEST).readiness, 1e-9)
        // Biceps recover faster (48 h half-life).
        assertEquals(0.5, statusOf(listOf(work(48, 6.0, Muscle.BICEPS))).getValue(Muscle.BICEPS).readiness, 1e-9)
    }

    @Test
    fun `secondary muscles count half and old work leaves the weekly count`() {
        val s = statusOf(listOf(work(24, 4.0, Muscle.CHEST, secondary = listOf(Muscle.TRICEPS)), work(24 * 8, 10.0, Muscle.CHEST)))
        assertEquals(4.0, s.getValue(Muscle.CHEST).weeklySets)
        assertEquals(2.0, s.getValue(Muscle.TRICEPS).weeklySets)
    }

    @Test
    fun `best routine prefers recovered muscles but respects the plan`() {
        val statuses = Recovery.status(listOf(work(12, 9.0, Muscle.CHEST, Muscle.TRICEPS)), now)
        val push = RoutineOption("push", "Push", 40, mapOf(Muscle.CHEST to 6.0, Muscle.TRICEPS to 4.0), isPlanned = true)
        val legs = RoutineOption("legs", "Legs", 40, mapOf(Muscle.QUADRICEPS to 6.0, Muscle.HAMSTRINGS to 4.0))
        val choice = TrainToday.bestRoutine(listOf(push, legs), statuses, 45)!!
        assertEquals("legs", choice.option.id) // chest is wrecked, so the plan loses
        assertTrue("Push" in choice.reason)

        val fresh = Recovery.status(emptyList(), now)
        assertEquals("push", TrainToday.bestRoutine(listOf(push, legs), fresh, 45)!!.option.id) // plan wins when it's fine
    }

    @Test
    fun `routines that don't fit the time are skipped`() {
        val long = RoutineOption("long", "Long", 60, mapOf(Muscle.QUADRICEPS to 10.0))
        assertNull(TrainToday.bestRoutine(listOf(long), Recovery.status(emptyList(), now), 30))
    }

    @Test
    fun `quick plan fits the time and avoids sore muscles`() {
        val statuses = Recovery.status(listOf(work(6, 8.0, Muscle.QUADRICEPS)), now)
        val plan15 = assertNotNull(TrainToday.quickPlan(statuses, 15))
        assertTrue(plan15.minutes <= 18, "15-min plan took ${plan15.minutes}")
        assertTrue(plan15.slots.none { it.muscle == Muscle.QUADRICEPS })
        assertTrue(plan15.slots.any { it.supersetGroup != null }, "short sessions use supersets")

        val plan45 = assertNotNull(TrainToday.quickPlan(Recovery.status(emptyList(), now), 45))
        assertTrue(plan45.slots.size > plan15.slots.size)
        assertTrue(plan45.minutes <= 48)
    }
}

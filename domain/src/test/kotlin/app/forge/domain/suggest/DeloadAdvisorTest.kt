package app.forge.domain.suggest

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DeloadAdvisorTest {
    private val today = LocalDate.of(2026, 10, 7) // a Wednesday

    /** Two workouts in each of the last [weeks] weeks (Mon + Thu). */
    private fun steady(weeks: Int, rpe: Double? = null) = (0 until weeks).flatMap { w ->
        val monday = today.minusWeeks(w.toLong()).with(java.time.DayOfWeek.MONDAY)
        listOf(SessionPoint(monday, rpe), SessionPoint(monday.plusDays(3), rpe))
    }

    @Test
    fun `counts consecutive weeks`() {
        assertEquals(6, DeloadAdvisor.weeksInARow(steady(6).map { it.date }, today.plusDays(4)))
        assertEquals(0, DeloadAdvisor.weeksInARow(emptyList(), today))
    }

    @Test
    fun `a long streak alone suggests a deload`() {
        val hint = assertNotNull(DeloadAdvisor.check(DeloadInput(steady(7), emptyList(), today.plusDays(4))))
        assertTrue(hint.reasons.first().contains("weeks"))
    }

    @Test
    fun `a short streak with one stall does not`() {
        val stalled = ExerciseTrend("Squat", listOf(100.0, 100.0, 99.0, 101.0, 100.0, 98.0))
        assertNull(DeloadAdvisor.check(DeloadInput(steady(3), listOf(stalled), today)))
    }

    @Test
    fun `stalls plus rising effort suggest a deload`() {
        val sessions = listOf(
            SessionPoint(today.minusDays(2), 9.2), SessionPoint(today.minusDays(6), 9.0),
            SessionPoint(today.minusDays(16), 8.2), SessionPoint(today.minusDays(20), 8.0),
        )
        val stalled = ExerciseTrend("Push-ups", listOf(60.0, 60.0, 59.0, 61.0, 60.0, 58.0))
        val hint = assertNotNull(DeloadAdvisor.check(DeloadInput(sessions, listOf(stalled), today)))
        assertEquals(2, hint.reasons.size)
    }

    @Test
    fun `improving lifts are not stalls`() {
        assertTrue(!DeloadAdvisor.isStalled(ExerciseTrend("Row", listOf(70.0, 68.0, 67.0, 65.0, 64.0, 62.0))))
        assertTrue(!DeloadAdvisor.isStalled(ExerciseTrend("New", listOf(50.0, 50.0))))
    }

    @Test
    fun `dismissed hints stay hidden until the date`() {
        val later = today.plusDays(4)
        assertNull(DeloadAdvisor.check(DeloadInput(steady(8), emptyList(), later, dismissedUntil = later.plusDays(3))))
        assertNotNull(DeloadAdvisor.check(DeloadInput(steady(8), emptyList(), later, dismissedUntil = later.minusDays(1))))
    }
}

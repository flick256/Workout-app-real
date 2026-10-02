package app.forge.domain.program

import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.WEDNESDAY
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProgramScheduleTest {
    private val mwf = setOf(MONDAY, WEDNESDAY, FRIDAY)
    private val monday = LocalDate.of(2026, 10, 5)

    @Test
    fun `routines rotate in order and wrap around`() {
        assertEquals(0, ProgramSchedule.nextRoutineIndex(3, null))
        assertEquals(1, ProgramSchedule.nextRoutineIndex(3, 0))
        assertEquals(0, ProgramSchedule.nextRoutineIndex(3, 2))
        // A routine that no longer exists restarts the rotation instead of crashing.
        assertEquals(0, ProgramSchedule.nextRoutineIndex(2, 5))
    }

    @Test
    fun `training day with nothing done yet`() {
        val plan = ProgramSchedule.plan(2, lastCompletedIndex = 0, lastCompletedDate = monday.minusDays(3), days = mwf, today = monday)
        assertEquals(1, plan.routineIndex)
        assertTrue(plan.isTrainingDay)
        assertFalse(plan.doneToday)
        assertNull(plan.nextTrainingDate)
    }

    @Test
    fun `rest day points at the next training day`() {
        val tuesday = monday.plusDays(1)
        val plan = ProgramSchedule.plan(2, 0, monday, mwf, tuesday)
        assertFalse(plan.isTrainingDay)
        assertEquals(monday.plusDays(2), plan.nextTrainingDate)
    }

    @Test
    fun `done today shows what's next and when`() {
        val plan = ProgramSchedule.plan(2, 1, monday, mwf, monday)
        assertTrue(plan.doneToday)
        assertEquals(0, plan.routineIndex)
        assertEquals(monday.plusDays(2), plan.nextTrainingDate)
    }

    @Test
    fun `flexible programs are always a training day`() {
        val plan = ProgramSchedule.plan(3, null, null, emptySet(), monday.plusDays(1))
        assertTrue(plan.isTrainingDay)
        assertNull(plan.nextTrainingDate)
    }

    @Test
    fun `estimate counts supersets as rounds`() {
        val straight = WorkoutEstimate.minutes(listOf(WorkoutEstimate.Slot(3, 60, null), WorkoutEstimate.Slot(3, 60, null)))
        val superset = WorkoutEstimate.minutes(listOf(WorkoutEstimate.Slot(3, 60, 1), WorkoutEstimate.Slot(3, 60, 1)))
        assertEquals(10, straight) // 6 × 40 s work + 6 × 60 s rest
        assertEquals(7, superset) // 6 × 40 s work + 3 × 60 s rest
    }
}

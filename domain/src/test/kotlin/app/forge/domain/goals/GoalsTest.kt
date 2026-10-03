package app.forge.domain.goals

import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GoalsTest {
    private val today = LocalDate.of(2026, 10, 3) // a Saturday
    private fun days(vararg back: Long) = back.map { today.minusDays(it) }.toSet()

    @Test
    fun `weekly goals`() {
        assertEquals(GoalProgress(2.0, 4.0, 0.5, false), GoalMath.weekly(2, 4))
        assertTrue(GoalMath.weekly(5, 4).done)
        assertEquals(1.0, GoalMath.weekly(5, 4).fraction)
    }

    @Test
    fun `bodyweight goals work in both directions`() {
        val cut = GoalMath.bodyweight(80.0, 77.5, 75.0)
        assertEquals(0.5, cut.fraction, 1e-9)
        assertFalse(cut.done)
        assertTrue(GoalMath.bodyweight(80.0, 74.8, 75.0).done)
        val bulk = GoalMath.bodyweight(60.0, 61.0, 65.0)
        assertEquals(0.2, bulk.fraction, 1e-9)
        assertEquals(0.0, GoalMath.bodyweight(60.0, 59.0, 65.0).fraction, "wrong way counts as zero")
        assertEquals(0.0, GoalMath.bodyweight(60.0, null, 65.0).fraction)
    }

    @Test
    fun `strength goals`() {
        assertEquals(0.8, GoalMath.best(80.0, 100.0).fraction, 1e-9)
        assertTrue(GoalMath.best(101.0, 100.0).done)
        assertFalse(GoalMath.best(null, 100.0).done)
    }

    @Test
    fun `streak survives until today is over`() {
        assertEquals(3, HabitMath.currentStreak(days(0, 1, 2), today))
        assertEquals(2, HabitMath.currentStreak(days(1, 2), today), "not ticked today yet")
        assertEquals(0, HabitMath.currentStreak(days(2, 3), today))
    }

    @Test
    fun `streak skips days the habit isn't due`() {
        val weekdays = DayMask.WEEKDAYS
        // Today is Saturday: Fri, Thu, Wed done; the weekend doesn't break it.
        assertEquals(3, HabitMath.currentStreak(days(1, 2, 3), today, weekdays))
        assertEquals(5, HabitMath.bestStreak(days(1, 2, 3, 4, 5), weekdays))
        assertTrue(weekdays.isDue(DayOfWeek.MONDAY))
        assertFalse(weekdays.isDue(DayOfWeek.SUNDAY))
        assertEquals(6, DayMask.ALL.toggle(DayOfWeek.SUNDAY).count)
    }

    @Test
    fun `best streak and completion`() {
        assertEquals(3, HabitMath.bestStreak(days(0, 1, 5, 6, 7)))
        assertEquals(0.5, HabitMath.completion(days(0, 1, 2), today.minusDays(5), today))
        assertNull(HabitMath.completion(emptySet(), today, today, DayMask(0)))
    }

    @Test
    fun `auto habits read the day's data`() {
        val facts = DayFacts(trained = true, proteinG = 120.0, proteinTargetG = 125, foodEntries = 3, steps = 9_500, sleepMinutes = 410)
        assertEquals(true, AutoHabits.isDone(HabitKind.TRAIN, null, facts))
        assertEquals(true, AutoHabits.isDone(HabitKind.PROTEIN, null, facts), "within 5% counts")
        assertEquals(true, AutoHabits.isDone(HabitKind.STEPS, 8_000.0, facts))
        assertEquals(false, AutoHabits.isDone(HabitKind.SLEEP, 8.0, facts))
        assertEquals(true, AutoHabits.isDone(HabitKind.SLEEP, 6.5, facts))
        assertNull(AutoHabits.isDone(HabitKind.STEPS, 8_000.0, DayFacts()), "no data is not a miss")
        assertNull(AutoHabits.isDone(HabitKind.CUSTOM, null, facts))
    }

    @Test
    fun `achievements unlock and show what's next`() {
        val stats = AchievementStats(workouts = 12, weekStreak = 3, prs = 1, bestSessionVolumeKg = 1_200.0)
        val unlocked = Achievement.unlocked(stats)
        assertTrue(Achievement.WORKOUTS_10 in unlocked)
        assertTrue(Achievement.TONNE in unlocked)
        assertFalse(Achievement.STREAK_4 in unlocked)
        assertEquals(Achievement.STREAK_4, Achievement.upNext(stats).first())
        assertEquals(0.75, Achievement.STREAK_4.progress(stats), 1e-9)
    }
}

package app.forge.domain.analytics

import app.forge.domain.dataset.ExerciseDataset
import app.forge.domain.dataset.HomePack
import java.io.File
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PersonalRecordsTest {
    private fun s(id: String, at: Long, load: Double?, reps: Int?, seconds: Int? = null) = SetPoint(id, at, load, reps, null, seconds)

    @Test
    fun `best picks each kind of record`() {
        val best = PersonalRecords.best(
            listOf(s("a", 1, 50.0, 10), s("a", 1, 50.0, 8), s("b", 2, 60.0, 5), s("b", 2, 40.0, 15)),
        )
        assertEquals(60.0, best.getValue(RecordKind.HEAVIEST).value)
        assertEquals(15.0, best.getValue(RecordKind.MOST_REPS).value)
        assertEquals("b", best.getValue(RecordKind.E1RM).sessionId) // 60 × 5 ≈ 68.8 beats 50 × 10 ≈ 66.7
        assertEquals(50.0 * 10 + 50.0 * 8, best.getValue(RecordKind.SESSION_VOLUME).value, 1e-9)
    }

    @Test
    fun `new records only count against earlier workouts`() {
        val sets = listOf(s("old", 1, 50.0, 8), s("new", 10, 52.5, 8), s("new", 10, 30.0, 6))
        val prs = PersonalRecords.newInSession(sets, "new").map { it.kind }
        assertTrue(RecordKind.HEAVIEST in prs)
        assertTrue(RecordKind.E1RM in prs)
        assertTrue(RecordKind.MOST_REPS !in prs) // 8 reps ties, doesn't beat
        // The first workout ever has nothing to beat.
        assertTrue(PersonalRecords.newInSession(sets, "old").isEmpty())
    }

    @Test
    fun `holds and e1rm series`() {
        val best = PersonalRecords.best(listOf(s("a", 1, null, null, 45), s("b", 2, null, null, 60)))
        assertEquals(60.0, best.getValue(RecordKind.LONGEST_HOLD).value)
        val series = PersonalRecords.e1rmSeries(listOf(s("b", 20, 55.0, 5), s("a", 10, 50.0, 5), s("a", 10, 40.0, 5)))
        assertEquals(listOf(10L, 20L), series.map { it.first })
        assertTrue(series[1].second > series[0].second)
    }
}

class ActivityTest {
    private val wednesday = LocalDate.of(2026, 10, 7)

    @Test
    fun `week streak counts back through consecutive weeks`() {
        val dates = listOf(wednesday, wednesday.minusWeeks(1), wednesday.minusWeeks(2), wednesday.minusWeeks(4))
        assertEquals(3, Activity.weekStreak(dates, wednesday))
        // Nothing yet this week: the streak still counts from last week.
        assertEquals(2, Activity.weekStreak(listOf(wednesday.minusWeeks(1), wednesday.minusWeeks(2)), wednesday))
        assertEquals(0, Activity.weekStreak(listOf(wednesday.minusWeeks(3)), wednesday))
    }

    @Test
    fun `heatmap covers whole weeks and scales levels to your training`() {
        val perDay = mapOf(
            wednesday to (1 to 1000.0),
            wednesday.minusDays(2) to (1 to 4000.0),
            wednesday.minusDays(7) to (1 to 2000.0),
            wednesday.minusDays(9) to (1 to 3000.0),
        )
        val cells = Activity.heatmap(perDay, wednesday, weeks = 4)
        assertEquals(28, cells.size)
        assertEquals(java.time.DayOfWeek.MONDAY, cells.first().date.dayOfWeek)
        assertEquals(java.time.DayOfWeek.SUNDAY, cells.last().date.dayOfWeek)
        assertEquals(4, cells.first { it.date == wednesday.minusDays(2) }.level)
        assertEquals(1, cells.first { it.date == wednesday }.level)
        assertEquals(0, cells.first { it.date == wednesday.plusDays(1) }.level)
    }
}

class DemoDataTest {
    @Test
    fun `demo data is plausible, deterministic and uses real exercises`() {
        val today = LocalDate.of(2026, 10, 3)
        val plan = DemoData.generate(today)
        assertEquals(plan, DemoData.generate(today))
        assertTrue(plan.sessions.size in 25..36, "sessions ${plan.sessions.size}")
        assertTrue(plan.sessions.all { it.date.isBefore(today) })
        assertTrue(plan.bodyweight.size > 20)
        val file = generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
            .map { File(it, "app/src/main/assets/exercises.json") }.first { it.exists() }
        val ids = (ExerciseDataset.parse(file.readText()) + HomePack.exercises).map { it.sourceId }.toSet()
        plan.sessions.flatMap { it.exercises }.forEach { assertTrue(it.sourceId in ids, it.sourceId) }
        // Squats get heavier over the 12 weeks.
        val squats = plan.sessions.flatMap { s -> s.exercises.filter { it.sourceId == "forge:bag_bear_hug_squat" } }
            .map { e -> e.sets.filter { !it.warmup }.maxOf { it.weightKg!! } }
        assertTrue(squats.last() > squats.first())
    }

    @Test
    fun `sport and cardio days count for the streak and show on the calendar`() {
        val monday = LocalDate.of(2026, 9, 28)
        val lastWeek = monday.minusWeeks(1)
        // Lifted two weeks ago and this week; only played football last week.
        val lifts = setOf(monday.minusWeeks(2), monday)
        assertEquals(1, Activity.weekStreak(lifts, monday))
        assertEquals(3, Activity.weekStreak(lifts + lastWeek, monday))
        val cells = Activity.heatmap(emptyMap(), monday, weeks = 2, activitiesPerDay = mapOf(lastWeek to 1))
        val cell = cells.single { it.date == lastWeek }
        assertEquals(1, cell.level)
        assertTrue(cell.trained)
        assertEquals(0, cells.single { it.date == monday }.level)
    }
}

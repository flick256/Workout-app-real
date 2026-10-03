package app.forge.domain.insights

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class InsightsTest {
    private val today = 20_000L
    private fun sessions(vararg e1rm: Double, rpe: List<Double?> = e1rm.map { null }) =
        e1rm.mapIndexed { i, v -> LiftSession(today - 35 + i * 4L, v, rpe[i]) }

    @Test
    fun `too little data says so`() {
        val r = StallAnalyzer.analyze(StallInput("Bench", sessions(60.0, 61.0), today))
        assertFalse(r.stalled)
        assertEquals(FindingKind.NOT_ENOUGH_DATA, r.findings.single().kind)
    }

    @Test
    fun `progress is recognised`() {
        val r = StallAnalyzer.analyze(StallInput("Bench", sessions(60.0, 61.0, 62.0, 63.0, 64.0, 66.0), today))
        assertFalse(r.stalled)
        assertEquals(FindingKind.PROGRESSING, r.findings.first().kind)
    }

    @Test
    fun `a plateau lists the likely reasons`() {
        val r = StallAnalyzer.analyze(
            StallInput(
                "Bench", sessions(70.0, 71.0, 70.5, 70.8, 70.2, 70.9, rpe = listOf(7.0, 7.0, 7.5, 8.0, 8.5, 9.0)), today,
                muscleName = "Chest", weeklySets = 4.0, weeklyTarget = 10, avgSleepHours = 6.2, avgProteinG = 70.0, proteinTargetG = 110,
            ),
        )
        assertTrue(r.stalled)
        val kinds = r.findings.map { it.kind }
        assertEquals(
            listOf(FindingKind.FLAT, FindingKind.EFFORT_RISING, FindingKind.LOW_VOLUME, FindingKind.LOW_SLEEP, FindingKind.LOW_PROTEIN),
            kinds,
        )
        assertTrue("70 g" in r.asText())
    }

    @Test
    fun `weekly template reads naturally`() {
        val f = WeeklyFacts(
            workouts = 3, workoutsLastWeek = 2, sets = 48, volumeKg = 11_000.0, volumeLastWeekKg = 10_000.0,
            prs = listOf(PrLine("Squat", "e1RM 105 kg")), avgSleepHours = 7.4, foodDaysLogged = 5, avgKcal = 2600,
            avgProteinG = 115, proteinTargetG = 120, habitPercent = 80,
        )
        val text = WeeklyReport.template(f)
        assertTrue(text.startsWith("3 workouts this week (last week: 2), 48 hard sets, volume +10% vs last week."))
        assertTrue("Squat (e1RM 105 kg)" in text)
        assertTrue("(aim for 8–10 h)" in text)
        assertTrue("volume_change_vs_last_week: +10%" in WeeklyReport.factSheet(f))
    }

    @Test
    fun `guard rejects invented numbers and risky advice`() {
        val facts = "workouts_this_week: 3\nhard_sets: 48\naverage_protein_g: 115 (target 120)"
        assertTrue(OutputGuard.check("Great week! You did 3 workouts and 48 hard sets. Protein at 115 g is close to your 120 g goal.", facts).ok)
        assertFalse(OutputGuard.check("Great week! You lifted 12,000 kg across 48 sets and burned 3500 calories.", facts).ok)
        assertFalse(OutputGuard.check("Nice work. Try fasting for 16 hours to lean out faster this month.", facts).ok)
        assertTrue(OutputGuard.check("Solid week with 48 sets. Aim for 8 hours of sleep and add 2 sets for back.", facts).ok, "small general numbers are fine")
    }
}

package app.forge.domain.workout

import app.forge.domain.model.SetType
import kotlin.test.Test
import kotlin.test.assertEquals

class WorkoutStatsTest {

    @Test
    fun `volume ignores warm-ups and unfinished sets`() {
        val summary = WorkoutStats.summarize(
            listOf(
                LoggedSet(SetType.WARMUP, 20.0, 10),
                LoggedSet(SetType.WORKING, 40.0, 8),
                LoggedSet(SetType.WORKING, 40.0, 7),
                LoggedSet(SetType.DROP, 30.0, 6),
                LoggedSet(SetType.WORKING, 40.0, 8, completed = false),
            ),
        )
        assertEquals(3, summary.completedSets)
        assertEquals(21, summary.totalReps)
        assertEquals(40.0 * 8 + 40.0 * 7 + 30.0 * 6, summary.volumeKg)
    }

    @Test
    fun `bodyweight sets count reps but add no volume`() {
        val summary = WorkoutStats.summarize(listOf(LoggedSet(SetType.WORKING, null, 15)))
        assertEquals(15, summary.totalReps)
        assertEquals(0.0, summary.volumeKg)
    }

    @Test
    fun `previous sets line up by type`() {
        val previous = listOf("w1" to SetType.WARMUP, "a" to SetType.WORKING, "b" to SetType.WORKING)
        val matched = WorkoutStats.matchPrevious(
            listOf(SetType.WARMUP, SetType.WARMUP, SetType.WORKING, SetType.WORKING, SetType.WORKING),
            previous,
        ) { it.second }
        assertEquals(listOf("w1", null, "a", "b", null), matched.map { it?.first })
    }
}

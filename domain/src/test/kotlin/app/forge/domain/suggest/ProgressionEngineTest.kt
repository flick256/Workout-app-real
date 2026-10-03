package app.forge.domain.suggest

import app.forge.domain.model.Equipment
import app.forge.domain.model.LogType
import app.forge.domain.suggest.SuggestionKind.ADD_REPS
import app.forge.domain.suggest.SuggestionKind.ADD_TIME
import app.forge.domain.suggest.SuggestionKind.ADD_WEIGHT
import app.forge.domain.suggest.SuggestionKind.FIRST_TIME
import app.forge.domain.suggest.SuggestionKind.HARDER_VARIATION
import app.forge.domain.suggest.SuggestionKind.HOLD
import app.forge.domain.suggest.SuggestionKind.REDUCE
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProgressionEngineTest {

    private fun session(weight: Double?, vararg reps: Int, rpe: Double? = null) =
        PastSession(reps.map { WorkSet(weight, it, rpe = rpe) })

    private fun suggest(
        vararg history: PastSession,
        logType: LogType = LogType.WEIGHT_REPS,
        equipment: Equipment? = Equipment.DUMBBELL,
        owned: List<Double> = emptyList(),
        harder: String? = null,
        min: Int? = 8,
        max: Int? = 12,
    ) = ProgressionEngine.suggest(
        ProgressionInput(logType, history.toList(), min, max, null, equipment, owned, harder),
    )!!

    @Test
    fun `no history asks you to find your level`() {
        val s = suggest()
        assertEquals(FIRST_TIME, s.kind)
        assertNull(s.weightKg)
        assertEquals(8, s.reps)
    }

    @Test
    fun `top of the range on every set adds the smallest step`() {
        val s = suggest(session(20.0, 12, 12, 12))
        assertEquals(ADD_WEIGHT, s.kind)
        assertEquals(22.0, s.weightKg) // dumbbells go up 2 kg
        assertEquals(8, s.reps)
        assertTrue("12" in s.reason)
    }

    @Test
    fun `owned weights decide the next weight`() {
        val s = suggest(session(15.0, 12, 12, 12), equipment = Equipment.WEIGHTED_BAG, owned = listOf(10.0, 15.0, 25.0))
        assertEquals(25.0, s.weightKg)
    }

    @Test
    fun `a grinder at RPE 10 hasn't earned the jump`() {
        val s = suggest(session(20.0, 12, 12, 12, rpe = 10.0))
        assertEquals(ADD_REPS, s.kind)
        assertEquals(20.0, s.weightKg)
    }

    @Test
    fun `inside the range adds a rep`() {
        val s = suggest(session(20.0, 10, 9, 9))
        assertEquals(ADD_REPS, s.kind)
        assertEquals(10, s.reps)
        assertEquals(20.0, s.weightKg)
    }

    @Test
    fun `one miss holds, two misses in a row reduce about 10 percent`() {
        assertEquals(HOLD, suggest(session(30.0, 8, 7, 6)).kind)
        val twice = suggest(session(30.0, 8, 7, 6), session(30.0, 8, 6, 6))
        assertEquals(REDUCE, twice.kind)
        assertEquals(26.0, twice.weightKg) // 27 kg → snapped down to the 2 kg dumbbell grid
    }

    @Test
    fun `a miss after going heavier is just a hold`() {
        // Missed at 32 kg, but last time's miss was at a lighter 30 kg: that's normal after a jump.
        assertEquals(HOLD, suggest(session(32.0, 7, 6, 6), session(30.0, 12, 12, 7)).kind)
    }

    @Test
    fun `bodyweight move with a harder variation suggests moving up`() {
        val s = suggest(session(null, 15, 15, 15), logType = LogType.REPS, min = 8, max = 15, harder = "Diamond Push-Up")
        assertEquals(HARDER_VARIATION, s.kind)
        assertTrue("Diamond Push-Up" in s.headline)
        assertEquals(8, s.reps)
    }

    @Test
    fun `bodyweight move at the top of the ladder adds vest weight`() {
        val s = suggest(session(null, 12, 12, 12), logType = LogType.REPS, owned = listOf(5.0, 10.0))
        assertEquals(ADD_WEIGHT, s.kind)
        assertEquals(5.0, s.weightKg)
    }

    @Test
    fun `with nothing heavier it pushes past the range`() {
        val s = suggest(session(24.0, 12, 12, 12), owned = listOf(10.0, 24.0))
        assertEquals(ADD_REPS, s.kind)
        assertEquals(14, s.reps)
    }

    @Test
    fun `timed holds progress in seconds then to a harder variation`() {
        val inRange = suggest(PastSession(listOf(WorkSet(null, null, 40), WorkSet(null, null, 35))), logType = LogType.DURATION, min = 30, max = 60)
        assertEquals(ADD_TIME, inRange.kind)
        assertEquals(40, inRange.seconds)
        val top = suggest(PastSession(listOf(WorkSet(null, null, 60), WorkSet(null, null, 60))), logType = LogType.DURATION, min = 30, max = 60, harder = "Hollow Body Hold")
        assertEquals(HARDER_VARIATION, top.kind)
    }

    @Test
    fun `cardio has no suggestion`() {
        assertNull(ProgressionEngine.suggest(ProgressionInput(LogType.DISTANCE_DURATION, emptyList())))
    }

    @Test
    fun `step grid`() {
        assertEquals(22.5, ProgressionEngine.nextWeight(21.0, Equipment.BARBELL, emptyList()))
        assertEquals(22.5, ProgressionEngine.nextWeight(20.0, Equipment.BARBELL, emptyList()))
        assertEquals(20.0, ProgressionEngine.nextWeight(16.0, Equipment.KETTLEBELL, emptyList()))
        assertNull(ProgressionEngine.nextWeight(30.0, null, listOf(10.0, 20.0)))
        assertEquals(18.0, ProgressionEngine.lighterWeight(20.0, Equipment.DUMBBELL, emptyList()))
        assertEquals(10.0, ProgressionEngine.lighterWeight(15.0, null, listOf(10.0, 15.0)))
    }
}

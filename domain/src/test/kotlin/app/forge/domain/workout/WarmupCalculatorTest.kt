package app.forge.domain.workout

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WarmupCalculatorTest {

    @Test
    fun `heavy working weight gets a three step ramp`() {
        val plan = WarmupCalculator.plan(100.0)
        assertEquals(
            listOf(WarmupSet(40.0, 8), WarmupSet(60.0, 5), WarmupSet(80.0, 3)),
            plan,
        )
    }

    @Test
    fun `medium weight gets two steps rounded to the plate step`() {
        assertEquals(listOf(WarmupSet(15.0, 8), WarmupSet(22.5, 4)), WarmupCalculator.plan(30.0))
    }

    @Test
    fun `light weight gets one set`() {
        assertEquals(listOf(WarmupSet(5.0, 10)), WarmupCalculator.plan(10.0))
    }

    @Test
    fun `owned weights are used instead of rounding`() {
        // Dumbbells: 5, 8, 12, 16, 20 kg. Working with 20 kg.
        val plan = WarmupCalculator.plan(20.0, ownedWeights = listOf(5.0, 8.0, 12.0, 16.0, 20.0))
        assertEquals(listOf(WarmupSet(8.0, 8), WarmupSet(16.0, 4)), plan)
    }

    @Test
    fun `never suggests a warm-up at or above the working weight`() {
        val plan = WarmupCalculator.plan(22.0, ownedWeights = listOf(22.0, 24.0))
        assertTrue(plan.isEmpty())
        assertTrue(WarmupCalculator.plan(0.0).isEmpty())
    }

    @Test
    fun `duplicate weights after rounding collapse`() {
        // 2.5 kg working: 50% = 1.25 rounds to 2.5 (not lighter) so nothing.
        assertTrue(WarmupCalculator.plan(2.5).isEmpty())
    }
}

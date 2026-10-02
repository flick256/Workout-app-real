package app.forge.domain.calc

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class OneRepMaxTest {

    @Test
    fun `a single rep is its own max`() {
        assertEquals(100.0, OneRepMax.epley(100.0, 1))
        assertEquals(100.0, OneRepMax.brzycki(100.0, 1)!!, 1e-9)
    }

    @Test
    fun `epley matches the textbook value`() {
        // 100 kg × 10 → 100 × (1 + 10/30) = 133.33
        assertEquals(133.333, OneRepMax.epley(100.0, 10)!!, 0.001)
    }

    @Test
    fun `brzycki matches the textbook value`() {
        // 100 kg × 10 → 100 × 36 / 27 = 133.33
        assertEquals(133.333, OneRepMax.brzycki(100.0, 10)!!, 0.001)
    }

    @Test
    fun `rpe adds reps in reserve`() {
        val atFailure = OneRepMax.estimate(80.0, 7)!!
        val fiveAtRpe8 = OneRepMax.estimate(80.0, 5, rpe = 8.0)!!
        assertEquals(atFailure, fiveAtRpe8, 1e-9)
    }

    @Test
    fun `rpe is clamped to the 6 to 10 range`() {
        assertEquals(0, OneRepMax.repsInReserve(10.0))
        assertEquals(0, OneRepMax.repsInReserve(11.0))
        assertEquals(4, OneRepMax.repsInReserve(2.0))
        assertEquals(1, OneRepMax.repsInReserve(8.5))
    }

    @Test
    fun `unreliable or invalid input returns null`() {
        assertNull(OneRepMax.estimate(60.0, 20))
        assertNull(OneRepMax.estimate(60.0, 10, rpe = 6.0)) // 14 effective reps
        assertNull(OneRepMax.estimate(0.0, 5))
        assertNull(OneRepMax.estimate(60.0, 0))
    }
}

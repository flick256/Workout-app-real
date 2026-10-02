package app.forge.domain.bodyweight

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BodyweightLoadTest {

    @Test
    fun `a push-up loads 64 percent of bodyweight`() {
        val e = BodyweightLoad.estimate(70.0, BodyweightProfile.PUSH_UP)
        assertEquals(44.8, e.loadKg, 0.01)
    }

    @Test
    fun `incline and decline match the study at its reference height`() {
        val h = BodyweightLoad.REFERENCE_HEIGHT_CM
        assertEquals(0.55, BodyweightLoad.fraction(BodyweightProfile.INCLINE_PUSH_UP, 30.48, h), 0.001)
        assertEquals(0.41, BodyweightLoad.fraction(BodyweightProfile.INCLINE_PUSH_UP, 60.96, h), 0.001)
        assertEquals(0.70, BodyweightLoad.fraction(BodyweightProfile.DECLINE_PUSH_UP, 30.48, h), 0.001)
        assertEquals(0.74, BodyweightLoad.fraction(BodyweightProfile.DECLINE_PUSH_UP, 60.96, h), 0.001)
        assertEquals(0.64, BodyweightLoad.fraction(BodyweightProfile.INCLINE_PUSH_UP, 0.0, h), 0.001)
    }

    @Test
    fun `the same bench is relatively higher for a shorter person`() {
        val short = BodyweightLoad.fraction(BodyweightProfile.INCLINE_PUSH_UP, 45.0, 155.0)
        val tall = BodyweightLoad.fraction(BodyweightProfile.INCLINE_PUSH_UP, 45.0, 195.0)
        assertTrue(short < tall, "shorter person should push less on the same bench ($short vs $tall)")
    }

    @Test
    fun `extreme elevations are clamped to sensible limits`() {
        assertEquals(0.15, BodyweightLoad.fraction(BodyweightProfile.INCLINE_PUSH_UP, 150.0, 170.0), 0.001)
        assertEquals(0.82, BodyweightLoad.fraction(BodyweightProfile.DECLINE_PUSH_UP, 150.0, 170.0), 0.001)
    }

    @Test
    fun `unknown or silly height falls back to the reference`() {
        val ref = BodyweightLoad.fraction(BodyweightProfile.INCLINE_PUSH_UP, 45.0, null)
        assertEquals(ref, BodyweightLoad.fraction(BodyweightProfile.INCLINE_PUSH_UP, 45.0, 20.0))
    }

    @Test
    fun `added weight counts by how it is carried`() {
        // Pull-up with a 10 kg vest: all of it counts.
        assertEquals(70.0 * 0.95 + 10.0, BodyweightLoad.estimate(70.0, BodyweightProfile.PULL_UP, 10.0).loadKg, 0.001)
        // Push-up with a 10 kg vest: the feet hold some of it.
        assertEquals(70.0 * 0.64 + 7.0, BodyweightLoad.estimate(70.0, BodyweightProfile.PUSH_UP, 10.0).loadKg, 0.001)
    }

    @Test
    fun `squat moves everything above the shins`() {
        // de Leva 1996: 1 - 2 × (shank 4.33% + foot 1.37%) = 88.6%
        assertEquals(1 - 2 * (0.0433 + 0.0137), BodyweightProfile.SQUAT.fraction, 0.001)
    }

    @Test
    fun `every profile has a sane fraction and a note`() {
        BodyweightProfile.entries.forEach {
            assertTrue(it.fraction in 0.1..1.0, "${it.name} fraction ${it.fraction}")
            assertTrue(it.addedFactor in 0.0..1.0, "${it.name} addedFactor")
            assertTrue(it.note.isNotBlank(), "${it.name} needs a note")
        }
    }
}

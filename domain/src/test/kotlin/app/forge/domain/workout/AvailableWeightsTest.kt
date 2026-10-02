package app.forge.domain.workout

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AvailableWeightsTest {

    @Test
    fun `range covers both ends`() {
        assertEquals(listOf(2.5, 5.0, 7.5, 10.0), AvailableWeights.range(2.5, 10.0, 2.5))
        assertEquals(listOf(10.0), AvailableWeights.range(10.0, 10.0, 2.0))
    }

    @Test
    fun `range rejects nonsense`() {
        assertFailsWith<IllegalArgumentException> { AvailableWeights.range(10.0, 5.0, 1.0) }
        assertFailsWith<IllegalArgumentException> { AvailableWeights.range(0.0, 5.0, 0.0) }
    }

    @Test
    fun `normalize sorts and removes duplicates and zeros`() {
        assertEquals(listOf(5.0, 10.0, 15.0), AvailableWeights.normalize(listOf(15.0, 0.0, 5.0, 10.0, 5.001)))
    }

    @Test
    fun `snap picks the closest owned weight and prefers lighter on ties`() {
        val owned = listOf(10.0, 15.0, 20.0)
        assertEquals(15.0, AvailableWeights.snap(14.0, owned))
        assertEquals(10.0, AvailableWeights.snap(12.5, owned))
        assertEquals(20.0, AvailableWeights.snap(40.0, owned))
        assertEquals(7.0, AvailableWeights.snap(7.0, emptyList()))
    }
}

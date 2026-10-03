package app.forge.domain.heart

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HeartRateTest {
    private fun bytes(vararg b: Int) = ByteArray(b.size) { b[it].toByte() }

    @Test
    fun `parses 8-bit heart rate with contact`() {
        assertEquals(HrReading(72, contact = true), HeartRateParser.parse(bytes(0x06, 72)))
        assertEquals(HrReading(72, contact = false), HeartRateParser.parse(bytes(0x04, 72)))
        assertEquals(HrReading(140), HeartRateParser.parse(bytes(0x00, 140)))
    }

    @Test
    fun `parses 16-bit heart rate, energy and RR intervals`() {
        // flags: 16-bit HR + energy + RR; HR 0x00A0 = 160; energy 0x0100 = 256 kJ; RR 1024 → 1000 ms, 512 → 500 ms.
        val r = HeartRateParser.parse(bytes(0x19, 0xA0, 0x00, 0x00, 0x01, 0x00, 0x04, 0x00, 0x02))!!
        assertEquals(160, r.bpm)
        assertEquals(256, r.energyKj)
        assertEquals(listOf(1000, 500), r.rrMs)
    }

    @Test
    fun `rejects junk`() {
        assertNull(HeartRateParser.parse(bytes(0x00)))
        assertNull(HeartRateParser.parse(bytes(0x00, 0)))
        assertNull(HeartRateParser.parse(bytes(0x01, 0x10)))
    }

    @Test
    fun `zones and max heart rate`() {
        assertEquals(196, HeartRateMath.maxHr(17))
        assertEquals(HrZone.Z3, HrZone.of(140, 196))
        assertEquals(HrZone.Z5, HrZone.of(180, 196))
        assertNull(HrZone.of(80, 196))
    }

    @Test
    fun `summary is time weighted and caps gaps`() {
        val s = HeartRateMath.summarize(
            listOf(HrSample(0, 100), HrSample(10_000, 160), HrSample(20_000, 160), HrSample(200_000, 120)),
            196,
        )!!
        // 100 for 10 s, 160 for 10 s, 160 for 30 s (gap capped), 120 for 1 s.
        assertEquals(147, s.avg) // (1000 + 1600 + 4800 + 120) / 51 s
        assertEquals(160, s.max)
        assertEquals(100, s.min)
        assertEquals(40L, s.zoneSeconds[HrZone.Z4])
        assertNull(HeartRateMath.summarize(emptyList(), 196))
    }
}

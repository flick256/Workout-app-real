package app.forge.domain.calc

import app.forge.domain.model.WeightUnit
import kotlin.test.Test
import kotlin.test.assertEquals

class UnitsTest {

    @Test
    fun `kg and lb round-trip`() {
        assertEquals(100.0, Units.lbToKg(Units.kgToLb(100.0)), 1e-9)
        assertEquals(220.462, Units.kgToLb(100.0), 0.001)
        assertEquals(45.359, Units.toKg(100.0, WeightUnit.LB), 0.001)
        assertEquals(50.0, Units.fromKg(50.0, WeightUnit.KG))
    }

    @Test
    fun `rounds to equipment step`() {
        assertEquals(62.5, Units.roundTo(61.9, 2.5))
        assertEquals(60.0, Units.roundTo(61.2, 2.5))
        assertEquals(12.0, Units.roundTo(12.4, 1.0))
    }

    @Test
    fun `formats without trailing zeros`() {
        assertEquals("60", Units.format(60.0))
        assertEquals("62.5", Units.format(62.5))
        assertEquals("61.25", Units.format(61.2499999))
    }
}

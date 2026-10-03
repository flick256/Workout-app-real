package app.forge.fitness.feature.setup

import app.forge.domain.model.WeightUnit
import org.junit.Assert.assertEquals
import org.junit.Test

class SetupAnswersTest {
    @Test
    fun switchingUnitsConvertsTheTypedBodyweight() {
        val lb = SetupAnswers(unit = WeightUnit.LB, bodyweight = "180")
        val kg = lb.withUnit(WeightUnit.KG)
        assertEquals(WeightUnit.KG, kg.unit)
        assertEquals(81.6, kg.bodyweight.toDouble(), 0.05)
        // And back again lands where it started.
        assertEquals(180.0, kg.withUnit(WeightUnit.LB).bodyweight.toDouble(), 0.2)
    }

    @Test
    fun blankBodyweightJustChangesTheUnit() {
        val a = SetupAnswers(unit = WeightUnit.KG).withUnit(WeightUnit.LB)
        assertEquals(WeightUnit.LB, a.unit)
        assertEquals("", a.bodyweight)
    }
}

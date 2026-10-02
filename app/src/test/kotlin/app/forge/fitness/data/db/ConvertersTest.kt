package app.forge.fitness.data.db

import app.forge.domain.model.Muscle
import org.junit.Assert.assertEquals
import org.junit.Test

class ConvertersTest {
    private val converters = Converters()

    @Test
    fun stringListRoundTrips() {
        val steps = listOf("Lie down.", "Brace, then \"crunch\".", "")
        assertEquals(steps, converters.jsonToStringList(converters.stringListToJson(steps)))
    }

    @Test
    fun muscleListRoundTrips() {
        val muscles = listOf(Muscle.CHEST, Muscle.TRICEPS, Muscle.SHOULDERS)
        assertEquals(muscles, converters.jsonToMuscleList(converters.muscleListToJson(muscles)))
    }

    @Test
    fun unknownMuscleNamesAreSkipped() {
        assertEquals(listOf(Muscle.LATS), converters.jsonToMuscleList("""["LATS","WINGS"]"""))
    }
}

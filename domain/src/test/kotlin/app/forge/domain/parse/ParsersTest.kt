package app.forge.domain.parse

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ParsersTest {

    @Test
    fun `reads an australian nutrition panel`() {
        val rows = listOf(
            "NUTRITION INFORMATION",
            "Servings per package: 12",
            "Serving size: 40g",
            "Avg Quantity per Serving   Avg Quantity per 100g",
            "Energy 636kJ 1590kJ",
            "Protein 4.6g 11.6g",
            "Fat, total 3.4g 8.6g",
            "- saturated 0.6g 1.5g",
            "Carbohydrate 22.6g 56.6g",
            "- sugars 0.4g 1.1g",
            "Dietary Fibre 4.2g 10.4g",
            "Sodium 2mg 4mg",
        )
        val r = assertNotNull(NutritionLabelParser.parse(rows))
        assertEquals(LabelBasis.PER_100G, r.basis)
        assertEquals(40.0, r.servingG)
        assertEquals(380.0, r.values[LabelField.ENERGY_KCAL]!!, 0.1)
        assertEquals(11.6, r.values[LabelField.PROTEIN])
        assertEquals(8.6, r.values[LabelField.FAT])
        assertEquals(56.6, r.values[LabelField.CARBS])
        assertEquals(1.1, r.values[LabelField.SUGARS])
        assertEquals(10.4, r.values[LabelField.FIBRE])
        assertEquals(0.0, r.values[LabelField.SALT]) // 4 mg sodium ≈ 0.01 g salt, rounded
        assertEquals(emptyList(), r.missing)
    }

    @Test
    fun `reads a per-serving label with calories and OCR slips`() {
        val rows = listOf("Nutrition Facts", "Serving size 1 bar (45g)", "Calories 2O0", "Total Fat 8g", "Sodium 150mg", "Total Carbohydrate 24g", "Protein 1O g")
        val r = assertNotNull(NutritionLabelParser.parse(rows))
        assertEquals(LabelBasis.PER_SERVING, r.basis)
        assertEquals(200.0, r.values[LabelField.ENERGY_KCAL])
        assertEquals(10.0, r.values[LabelField.PROTEIN])
        assertEquals(0.4, r.values[LabelField.SALT]) // 150 mg sodium × 2.5
    }

    @Test
    fun `nothing useful gives null`() {
        assertNull(NutritionLabelParser.parse(listOf("Ingredients: oats", "Made in Australia")))
        assertNull(NutritionLabelParser.parse(emptyList()))
    }

    @Test
    fun `parses common ways of saying a set`() {
        assertEquals(SetCommand("bench", 3, 8, 60.0), SetCommandParser.parse("3x8 bench at 60"))
        assertEquals(SetCommand("bench", 3, 8, 60.0), SetCommandParser.parse("bench 60kg 3 sets of 8"))
        assertEquals(SetCommand("squat", 1, 5, 100.0), SetCommandParser.parse("squat 100 for 5"))
        assertEquals(SetCommand("pushups", 3, 15), SetCommandParser.parse("pushups 3x15"))
        assertEquals(SetCommand("plank", 3, null, null, 45), SetCommandParser.parse("plank 3x45s"))
        assertEquals(SetCommand("deadlift", 1, 5, 140.0, rpe = 8.0), SetCommandParser.parse("deadlift 140 kg 5 reps rpe 8"))
        val curls = assertNotNull(SetCommandParser.parse("curls three by twelve at 25 pounds"))
        assertEquals(3, curls.sets)
        assertEquals(12, curls.reps)
        assertEquals(11.34, curls.weightKg!!, 0.01)
        assertEquals("squat", SetCommandParser.parse("squat 100 5")!!.exerciseQuery)
        assertEquals(5, SetCommandParser.parse("squat 100 5")!!.reps)
    }

    @Test
    fun `rejects things that aren't sets`() {
        assertNull(SetCommandParser.parse("hello"))
        assertNull(SetCommandParser.parse("3x8"))
        assertNull(SetCommandParser.parse("bench 900 kg 3x8"))
    }

    @Test
    fun `matches spoken names to exercises`() {
        val names = listOf("Barbell Bench Press - Medium Grip", "Dumbbell Bench Press", "Pushups", "Pull-Up", "Barbell Squat", "Plank")
        assertEquals("Pull-Up", ExerciseMatcher.best("pullups", names, { it }))
        assertEquals("Barbell Squat", ExerciseMatcher.best("squat", names, { it }))
        assertEquals("Dumbbell Bench Press", ExerciseMatcher.best("bench", names, { it }, preferred = { it.startsWith("Dumbbell") }))
        assertNull(ExerciseMatcher.best("zumba", names, { it }))
    }
}

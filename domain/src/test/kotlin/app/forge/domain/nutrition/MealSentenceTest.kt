package app.forge.domain.nutrition

import kotlin.test.Test
import kotlin.test.assertEquals

class MealSentenceTest {
    @Test
    fun `a typed breakfast becomes items`() {
        assertEquals(
            listOf(MealItem(2.0, null, "weet-bix"), MealItem(null, null, "milk"), MealItem(1.0, null, "banana")),
            MealSentence.parse("2 weet-bix with milk and a banana"),
        )
    }

    @Test
    fun `units, weights and words for numbers`() {
        assertEquals(
            listOf(
                MealItem(100.0, "g", "rice"),
                MealItem(250.0, "ml", "milk"),
                MealItem(2.0, "slice", "toast"),
                MealItem(0.5, "cup", "oats"),
                MealItem(3.0, null, "eggs"),
            ),
            MealSentence.parse("I had 100g rice, 250 ml of milk, two slices of toast, half a cup of oats and three eggs"),
        )
    }

    @Test
    fun `dishes with and in their name stay whole`() {
        assertEquals(
            listOf(MealItem(null, null, "fish and chips"), MealItem(1.0, null, "coke")),
            MealSentence.parse("fish and chips and a coke"),
        )
        assertEquals(listOf("double quarter pounder", "large fries"), MealSentence.parse("a double quarter pounder + large fries").map { it.food })
    }

    private val bread = listOf(Portion("1 slice", 32.0), Portion("1 cup, cubed", 45.0))
    private val banana = listOf(Portion("1 cup, sliced", 150.0), Portion("1 medium", 120.0))

    @Test
    fun `amounts come from the food's own portions`() {
        assertEquals(64.0, MealSentence.amount(MealItem(2.0, "slice", "toast"), bread, null, null).grams)
        // A plain count uses the "whole item" portion, not a cupful.
        val one = MealSentence.amount(MealItem(1.0, null, "banana"), banana, null, null)
        assertEquals(120.0, one.grams)
        assertEquals("1 medium (120 g)", one.explanation)
        // A saved serving wins for a count ("2 burgers").
        assertEquals(626.0, MealSentence.amount(MealItem(2.0, null, "dqp"), emptyList(), 313.0, "1 burger").grams)
    }

    @Test
    fun `weights are used as given and missing portions use standard sizes`() {
        assertEquals(100.0, MealSentence.amount(MealItem(100.0, "g", "rice"), bread, null, null).grams)
        assertEquals(125.0, MealSentence.amount(MealItem(0.5, "cup", "milk"), emptyList(), null, null).grams)
        assertEquals("2 × 1 tablespoon (20 g)", MealSentence.amount(MealItem(2.0, "tablespoon", "peanut butter"), emptyList(), null, null).explanation)
        assertEquals(100.0, MealSentence.amount(MealItem(null, null, "mystery"), emptyList(), null, null).grams)
    }
}

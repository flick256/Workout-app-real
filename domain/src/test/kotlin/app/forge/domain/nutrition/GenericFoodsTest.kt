package app.forge.domain.nutrition

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GenericFoodsTest {
    private fun food(name: String) = GenericFood(name, name, Nutrients(kcal = 100.0, proteinG = 1.0, carbsG = 1.0, fatG = 1.0))

    private val index = GenericFoodIndex(
        listOf(
            "Flour, wheat, white, high protein or bread making flour",
            "Bread, from white flour, commercial",
            "Bread, wholemeal, commercial",
            "Flour, wheat, white, self-raising",
            "Oats, rolled, uncooked",
            "Bread roll, from white flour, commercial",
            "Banana, cavendish, peeled, raw",
            "Cake or bread, banana, commercial",
            "Chicken, breast, lean flesh, grilled",
            "Chicken burger, chicken breast, with salad, fast food chain",
            "Turkish delight",
            "Yeast, baker's, dried",
            "Bacon & egg roll",
            "Egg, chicken, whole, raw",
        ).map(::food),
    )

    private fun names(query: String) = index.search(query).foods.map { it.name }

    @Test
    fun `the food itself ranks above things merely containing the words`() {
        assertEquals("Bread, from white flour, commercial", names("white bread").first())
        assertEquals("Banana, cavendish, peeled, raw", names("banana").first())
        assertEquals("Chicken, breast, lean flesh, grilled", names("chicken breast").first())
        assertEquals("Egg, chicken, whole, raw", names("eggs").first())
    }

    @Test
    fun `words match whole word starts, not the middle of words`() {
        assertTrue(names("raisin").isEmpty())
        assertTrue("Bread roll, from white flour, commercial" in names("bread rolls"))
    }

    @Test
    fun `an unknown brand is set aside and the rest still finds the food`() {
        val result = index.search("bakers delight wholemeal bread")
        assertEquals(listOf("bakers", "delight"), result.ignoredWords)
        assertEquals("Bread, wholemeal, commercial", result.foods.first().name)
        // Even when the brand's words happen to appear in other foods.
        assertEquals("Bread, from white flour, commercial", index.search("bakers delight white bread").foods.first().name)
    }
}

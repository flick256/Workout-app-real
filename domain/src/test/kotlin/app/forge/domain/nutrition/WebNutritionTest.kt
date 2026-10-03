package app.forge.domain.nutrition

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class WebNutritionTest {
    @Test
    fun `search result links are decoded and junk is dropped`() {
        val html = """
            <a rel="nofollow" class="result__a" href="//duckduckgo.com/l/?uddg=https%3A%2F%2Fwww.youtube.com%2Fwatch%3Fv%3D1&amp;rut=x">yt</a>
            <a rel="nofollow" class="result__a" href="//duckduckgo.com/l/?uddg=https%3A%2F%2Fwww.calorieking.com%2Fau%2Fen%2Ffoods%2Fdqp&amp;rut=y">ck</a>
            <a rel="nofollow" class="result__a" href="//duckduckgo.com/l/?uddg=https%3A%2F%2Fmcdonalds.com.au%2Fmenu%2Fdouble-quarter-pounder&amp;rut=z">mc</a>
        """
        val links = WebNutrition.searchResults(html)
        assertEquals(listOf("https://www.calorieking.com/au/en/foods/dqp", "https://mcdonalds.com.au/menu/double-quarter-pounder"), links)
        // The chain's own site first, then good nutrition sites.
        assertEquals("https://mcdonalds.com.au/menu/double-quarter-pounder", WebNutrition.rank(links).first())
    }

    @Test
    fun `a page becomes readable text`() {
        val text = WebNutrition.pageText(
            "<html><head><title>x</title></head><body><script>var a=1;</script><table><tr><td>Energy</td><td>3470 kJ</td></tr>" +
                "<tr><td>Protein</td><td>54.0&nbsp;g</td></tr></table><p>Fish &amp; chips</p></body></html>",
        )
        assertFalse("var a" in text)
        assertTrue("Energy | 3470 kJ" in text.replace(Regex(" +"), " ").replace("| Energy", "Energy"))
        assertTrue("Fish & chips" in text)
    }

    private val panel = """
        Double Quarter Pounder
        Nutrition Information | Average Quantity per Serving | Average Quantity per 100g
        Serving size: 313 g
        Energy | 3470 kJ | 1110 kJ
        Protein | 54.0 g | 17.3 g
        Fat, total | 52.0 g | 16.6 g
        - Saturated | 20.0 g | 6.4 g
        Carbohydrate | 38.0 g | 12.1 g
        - Sugars | 10.0 g | 3.2 g
        Sodium | 1360 mg | 435 mg
    """.trimIndent()

    @Test
    fun `an Australian panel is read per serve`() {
        val n = assertNotNull(WebNutrition.readPanel(panel))
        assertEquals(3470 / 4.184, n.kcal, 0.1)
        assertEquals(54.0, n.proteinG)
        assertEquals(52.0, n.fatG)
        assertEquals(38.0, n.carbsG)
        assertEquals(313.0, n.servingG)
        assertFalse(n.perHundred)
        val info = n.toFoodInfo("Double Quarter Pounder", "McDonald's")
        assertEquals(829.0, info.per100g.forGrams(313.0).kcal, 1.0)
        assertEquals(1.36 * 2.5, info.per100g.forGrams(313.0).saltG!!, 0.01)
    }

    @Test
    fun `numbers that don't add up are rejected`() {
        // 900 kcal can't come from 5 g of everything.
        assertFalse(WebNutrition.isPlausible(PageNutrition(null, 900.0, 5.0, 5.0, 5.0, null, null, perHundred = false)))
        assertTrue(WebNutrition.isPlausible(PageNutrition(null, 829.0, 54.0, 38.0, 52.0, 313.0, null, perHundred = false)))
    }

    @Test
    fun `AI answers must use numbers that are on the page`() {
        val good = PageNutrition(null, 829.3, 54.0, 38.0, 52.0, 313.0, null, perHundred = false)
        assertTrue(WebNutrition.numbersOnPage(good, panel))
        val madeUp = good.copy(proteinG = 61.0)
        assertFalse(WebNutrition.numbersOnPage(madeUp, panel))
    }

    @Test
    fun `the nutrition part of a long page is found`() {
        val page = "Menu ".repeat(800) + "\n" + panel + "\n" + "Footer links ".repeat(400)
        val window = assertNotNull(WebNutrition.nutritionWindow(page))
        assertTrue("Protein | 54.0 g" in window)
        assertTrue(window.length <= 2_400)
    }
}

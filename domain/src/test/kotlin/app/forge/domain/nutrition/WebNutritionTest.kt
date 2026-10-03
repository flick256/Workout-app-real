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

    @Test
    fun `Bing and Mojeek result links are read`() {
        val target = "https://www.bakersdelight.com.au/products/low-gi-white-loaf"
        val encoded = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(target.toByteArray())
        val bing = """<ol id="b_results"><li class="b_algo" data-id=""><div class="b_title"><h2>
            <a target="_blank" href="https://www.bing.com/ck/a?!&amp;&amp;p=abc&amp;u=a1$encoded&amp;ntb=1">Low GI White</a></h2></div></li>
            <li class="b_algo"><h2><a href="https://www.fatsecret.com.au/calories-nutrition/bakers-delight/low-gi-white">x</a></h2></li></ol>"""
        assertEquals(listOf(target, "https://www.fatsecret.com.au/calories-nutrition/bakers-delight/low-gi-white"), WebNutrition.bingResults(bing))
        val mojeek = """<ul class="results-standard"><li><a class="ob" href="https://www.calorieking.com/au/en/foods/x">t</a>
            <h2><a class="title" href="https://www.calorieking.com/au/en/foods/x">t</a></h2></li></ul>"""
        assertEquals(listOf("https://www.calorieking.com/au/en/foods/x"), WebNutrition.mojeekResults(mojeek))
    }

    @Test
    fun `FatSecret Australia search results are read`() {
        val html = """<table class="generic searchResult"><tr><td class="borderBottom">
            <a class="prominent" href="/calories-nutrition/bakers-delight/low-gi-white-loaf" onclick="">Low GI White Loaf</a>
            <a class="brand" href="/calories-nutrition/bakers-delight">(Bakers Delight)</a><br/>
            <div class="smallText greyText greyLink">Per 1 slice - Calories: 99kcal | Fat: 0.90g | Carbs: 17.20g | Protein: 4.10g<br/></div></td></tr>
            <tr><td class="borderBottom"><a class="prominent" href="/calories-nutrition/generic/bread-white">White Bread</a>
            <div>Per 1 regular slice - Calories: 66kcal | Fat: 0.82g | Carbs: 12.65g | Protein: 1.91g</div></td></tr></table>"""
        val hits = WebNutrition.fatSecretSiteResults(html)
        assertEquals(2, hits.size)
        val loaf = hits.first()
        assertEquals("Low GI White Loaf", loaf.name)
        assertEquals("Bakers Delight", loaf.brand)
        assertEquals("1 slice", loaf.per)
        assertEquals(99.0, loaf.kcal)
        assertEquals(4.1, loaf.proteinG)
        assertEquals("https://www.fatsecret.com.au/calories-nutrition/bakers-delight/low-gi-white-loaf", loaf.url)
        assertEquals(null, hits[1].brand)
    }

    @Test
    fun `serving weights are found in common forms`() {
        assertEquals(78.0, WebNutrition.servingGrams("Serving size: 2 slices (78g)"))
        assertEquals(39.0, WebNutrition.servingGrams("Serving Size\n1 slice (39 g)".replace("\n", " ")))
        assertEquals(45.0, WebNutrition.servingGrams("Nutrition per 1 slice (45 g)"))
    }

    @Test
    fun `schema-org nutrition in page data is read`() {
        val html = """<html><script type="application/ld+json">{"@context":"https://schema.org","@type":"Product","name":"Low GI White",
            "nutrition":{"@type":"NutritionInformation","servingSize":"2 slices (78 g)","calories":"775 kJ","proteinContent":"8.0 g",
            "carbohydrateContent":"33.6 g","fatContent":"1.4 g","sodiumContent":"370 mg"}}</script><body>Bread</body></html>"""
        val n = assertNotNull(WebNutrition.structuredNutrition(html))
        assertEquals(775 / 4.184, n.kcal, 0.1)
        assertEquals(8.0, n.proteinG)
        assertEquals(78.0, n.servingG)
        assertEquals("2 slices (78 g)", n.servingLabel)
        assertEquals(370.0, n.sodiumMg)
    }

    @Test
    fun `nutrition kept in page scripts still reaches the reader`() {
        val html = """<html><body><div id="app"></div><script id="__NEXT_DATA__">{"props":{"food":{"name":"Low GI White",
            "energy":"Energy 1000kJ","nutrients":[{"label":"Protein","value":"9.5 g"}]}}}</script></body></html>"""
        val text = WebNutrition.pageText(html)
        assertTrue("Protein" in text && "9.5 g" in text)
    }
}

package app.forge.domain.nutrition

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FatSecretTest {
    @Test
    fun `search results read the per-serving summary`() {
        val body = """{"foods":{"food":[
            {"brand_name":"McDonald's","food_description":"Per 1 burger - Calories: 844kcal | Fat: 52.00g | Carbs: 38.00g | Protein: 54.00g",
             "food_id":"30240","food_name":"Double Quarter Pounder","food_type":"Brand"},
            {"food_description":"Per 100g - Calories: 89kcal | Fat: 0.33g | Carbs: 22.84g | Protein: 1.09g",
             "food_id":"5388","food_name":"Banana","food_type":"Generic"}],
            "max_results":"20","page_number":"0","total_results":"2"}}"""
        val hits = FatSecret.parseSearch(body)
        assertEquals(2, hits.size)
        val dqp = hits.first()
        assertEquals("McDonald's", dqp.brand)
        assertEquals("1 burger", dqp.per)
        assertEquals(844.0, dqp.kcal)
        assertEquals(54.0, dqp.proteinG)
        assertEquals("100g", hits[1].per)
    }

    @Test
    fun `a single result and no results are both handled`() {
        val one = """{"foods":{"food":{"food_id":"1","food_name":"Weet-Bix","food_description":"Per 2 biscuits - Calories: 112kcal"},"total_results":"1"}}"""
        assertEquals(listOf("Weet-Bix"), FatSecret.parseSearch(one).map { it.name })
        assertTrue(FatSecret.parseSearch("""{"foods":{"max_results":"20","page_number":"0","total_results":"0"}}""").isEmpty())
    }

    @Test
    fun `food details become per 100 g with the weighed serving`() {
        val body = """{"food":{"food_id":"30240","food_name":"Double Quarter Pounder","brand_name":"McDonald's",
            "servings":{"serving":[
              {"serving_description":"1 burger","metric_serving_amount":"313.000","metric_serving_unit":"g","is_default":"1",
               "calories":"844","carbohydrate":"38.00","protein":"54.00","fat":"52.00","sodium":"1360","fiber":"2"}]}}}"""
        val food = assertNotNull(FatSecret.parseFood(body))
        assertEquals(313.0, food.servingG)
        assertEquals("1 burger", food.servingLabel)
        assertEquals(844.0 * 100 / 313, food.per100g.kcal, 0.01)
        // Back to one burger: the numbers FatSecret gave.
        assertEquals(844.0, food.per100g.forGrams(313.0).kcal, 0.01)
        assertEquals(1.36 * 2.5, food.per100g.forGrams(313.0).saltG!!, 0.01)
    }

    @Test
    fun `a serving without a weight still keeps per-serving numbers right`() {
        val body = """{"food":{"food_id":"9","food_name":"Chicken Wings","servings":{"serving":
            {"serving_description":"3 wings","is_default":"1","calories":"290","protein":"24","carbohydrate":"9","fat":"17"}}}}"""
        val food = assertNotNull(FatSecret.parseFood(body))
        assertEquals(100.0, food.servingG)
        assertEquals(290.0, food.per100g.forGrams(100.0).kcal, 0.01)
        assertTrue(food.servingLabel!!.startsWith("3 wings"))
    }

    @Test
    fun `errors are recognised`() {
        val e = assertNotNull(FatSecret.error("""{"error":{"code":21,"message":"Invalid IP address detected: '1.2.3.4'"}}"""))
        assertEquals(21, e.code)
        assertNull(FatSecret.error("""{"foods":{}}"""))
    }
}

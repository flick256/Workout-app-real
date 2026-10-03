package app.forge.domain.nutrition

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NutritionTest {

    @Test
    fun `portions scale from per 100 g`() {
        val oats = Nutrients(kcal = 380.0, proteinG = 13.0, carbsG = 60.0, fatG = 7.0, fiberG = 10.0)
        val bowl = oats.forGrams(60.0)
        assertEquals(228.0, bowl.kcal, 1e-9)
        assertEquals(7.8, bowl.proteinG, 1e-9)
        assertEquals(6.0, bowl.fiberG!!, 1e-9)
        val day = bowl + Nutrients(kcal = 100.0, proteinG = 20.0)
        assertEquals(328.0, day.kcal, 1e-9)
        assertEquals(6.0, day.fiberG!!, 1e-9)
        assertNull((Nutrients() + Nutrients()).fiberG)
    }

    @Test
    fun `meal defaults follow the clock`() {
        assertEquals(Meal.BREAKFAST, Meal.forHour(7))
        assertEquals(Meal.LUNCH, Meal.forHour(12))
        assertEquals(Meal.DINNER, Meal.forHour(19))
        assertEquals(Meal.SNACKS, Meal.forHour(15))
        assertEquals(Meal.SNACKS, Meal.forHour(23))
    }

    @Test
    fun `mifflin st jeor matches the published formula`() {
        // 10×70 + 6.25×178 − 5×17 + 5 = 1732.5
        assertEquals(1732.5, NutritionTargets.bmr(70.0, 178.0, 17, Sex.MALE), 1e-9)
        assertEquals(1566.5, NutritionTargets.bmr(70.0, 178.0, 17, Sex.FEMALE), 1e-9)
    }

    @Test
    fun `targets for an active teenager maintaining`() {
        val t = NutritionTargets.calculate(TargetsInput(70.0, 178.0, 17, Sex.MALE, ActivityLevel.MODERATE, NutritionGoal.MAINTAIN))
        assertEquals(2690, t.kcal) // 1732.5 × 1.55 = 2685 → 2690
        assertEquals(112, t.proteinG)
        assertEquals(75, t.fatG)
        // Macros add back up to the calories (within rounding).
        assertTrue(kotlin.math.abs(t.proteinG * 4 + t.carbsG * 4 + t.fatG * 9 - t.kcal) <= 10)
    }

    @Test
    fun `teen deficits are capped and gains are gentle`() {
        val base = TargetsInput(70.0, 178.0, 17, Sex.MALE, ActivityLevel.MODERATE, NutritionGoal.MAINTAIN)
        val maintain = NutritionTargets.calculate(base).kcal
        val lose = NutritionTargets.calculate(base.copy(goal = NutritionGoal.LOSE))
        assertEquals(maintain - 250, lose.kcal)
        assertTrue(lose.explanation.any { "under 18" in it })
        assertEquals(140, lose.proteinG)
        val adultLose = NutritionTargets.calculate(base.copy(ageYears = 25, goal = NutritionGoal.LOSE)).kcal
        val adultMaintain = NutritionTargets.calculate(base.copy(ageYears = 25)).kcal
        assertEquals(adultMaintain - 400, adultLose)
        assertEquals(maintain + 300, NutritionTargets.calculate(base.copy(goal = NutritionGoal.GAIN)).kcal)
    }

    @Test
    fun `never below resting burn`() {
        val t = NutritionTargets.calculate(TargetsInput(45.0, 150.0, 40, Sex.FEMALE, ActivityLevel.SEDENTARY, NutritionGoal.LOSE))
        val bmr = NutritionTargets.bmr(45.0, 150.0, 40, Sex.FEMALE)
        assertTrue(t.kcal >= bmr * 1.1 - 5)
    }

    @Test
    fun `parses an open food facts product`() {
        val body = """
            {"code":"9300633603205","status":1,"product":{
              "product_name":"Rolled Oats","brands":"Uncle Tobys, Nestle",
              "serving_size":"40 g","serving_quantity":"40",
              "nutriments":{"energy-kj_100g":1590,"proteins_100g":"11.6","carbohydrates_100g":56.6,
                            "fat_100g":8.6,"fiber_100g":10.4,"sugars_100g":1.1,"sodium_100g":0.004}}}
        """.trimIndent()
        val food = assertNotNull(OpenFoodFacts.parseProduct(body))
        assertEquals("Rolled Oats", food.name)
        assertEquals("Uncle Tobys", food.brand)
        assertEquals("9300633603205", food.barcode)
        assertEquals(380.0, food.per100g.kcal, 0.1)
        assertEquals(11.6, food.per100g.proteinG, 1e-9)
        assertEquals(0.01, food.per100g.saltG!!, 1e-9)
        assertEquals(40.0, food.servingG)
        assertEquals("40 g", food.servingLabel)
    }

    @Test
    fun `missing or unusable products are rejected`() {
        assertNull(OpenFoodFacts.parseProduct("""{"code":"123","status":0,"status_verbose":"product not found"}"""))
        assertNull(OpenFoodFacts.parseProduct("""{"status":1,"product":{"product_name":"Mystery","nutriments":{}}}"""))
        assertNull(OpenFoodFacts.parseProduct("not json"))
        // Energy can be worked out from the macros.
        val fromMacros = OpenFoodFacts.parseProduct(
            """{"status":1,"product":{"product_name":"Bar","nutriments":{"proteins_100g":20,"carbohydrates_100g":50,"fat_100g":10}}}""",
        )
        assertEquals(370.0, fromMacros!!.per100g.kcal, 1e-9)
    }

    @Test
    fun `parses search results and skips junk`() {
        val body = """{"count":2,"products":[
            {"code":"1","product_name":"Greek Yoghurt","nutriments":{"energy-kcal_100g":97,"proteins_100g":9}},
            {"code":"2","nutriments":{"energy-kcal_100g":50}},
            "oops"]}"""
        val results = OpenFoodFacts.parseSearch(body)
        assertEquals(listOf("Greek Yoghurt"), results.map { it.name })
        assertEquals(0.0, results.single().per100g.fatG, 1e-9)
    }

    @Test
    fun `parses Search-a-licious results and puts Australian products first`() {
        val body = """{"count":3,"page":1,"page_size":25,"hits":[
            {"code":"3","product_name":{"en":"Wholemeal Bread","main":"Pain complet"},"brands":["Le Fournil"],
             "countries_tags":["en:france"],"nutriments":{"energy-kcal_100g":240}},
            {"code":"4","product_name":"Wholemeal Block Loaf","brands":["Bakers Delight"],
             "countries_tags":["en:australia"],"nutriments":{"energy-kj_100g":1004,"proteins_100g":10.1}},
            {"code":"5","product_name":"No energy","nutriments":{}},
            {"code":"6","product_name":"Хлеб белый","lang":"ru","countries_tags":["en:russia"],"nutriments":{"energy-kcal_100g":250}},
            {"code":"7","product_name":"Pain de mie","lang":"fr","countries_tags":["en:france"],"nutriments":{"energy-kcal_100g":260}}]}"""
        val results = OpenFoodFacts.parseSearch(body)
        assertEquals(listOf("Wholemeal Block Loaf", "Wholemeal Bread"), results.map { it.name })
        assertEquals("Bakers Delight", results.first().brand)
        assertEquals("Le Fournil", results.last().brand)
        assertEquals(240.0, results.first().per100g.kcal, 0.5)
    }

    @Test
    fun `barcode validation`() {
        assertTrue(OpenFoodFacts.isValidBarcode("9300633603205"))
        assertTrue(OpenFoodFacts.isValidBarcode("12345670"))
        assertTrue(!OpenFoodFacts.isValidBarcode("12345"))
        assertTrue(!OpenFoodFacts.isValidBarcode("93006336032AB"))
    }
}

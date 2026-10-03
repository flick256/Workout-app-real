package app.forge.fitness.data.nutrition

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.forge.domain.nutrition.FoodInfo
import app.forge.domain.nutrition.Meal
import app.forge.domain.nutrition.Nutrients
import app.forge.domain.nutrition.Sex
import app.forge.fitness.data.FakeTime
import app.forge.fitness.data.TestDb
import app.forge.fitness.data.db.ForgeDatabase
import app.forge.fitness.data.db.FoodSource
import app.forge.fitness.data.prefs.CustomTargets
import app.forge.fitness.data.prefs.UserPreferences
import app.forge.fitness.data.prefs.UserPreferencesRepository
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Stands in for Open Food Facts, counting calls so tests can check the offline cache. */
private class FakeCatalog : FoodCatalog {
    val products = mutableMapOf<String, FoodInfo>()
    var calls = 0
    var offline = false

    override suspend fun product(barcode: String): LookupResult {
        calls++
        if (offline) return LookupResult.Failed("offline")
        return products[barcode]?.let { LookupResult.Found(it) } ?: LookupResult.NotFound
    }

    override suspend fun search(query: String): SearchResult =
        SearchResult.Results(products.values.filter { query.lowercase() in it.name.lowercase() })
}

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = android.app.Application::class)
class FoodRepositoryTest {
    private lateinit var db: ForgeDatabase
    private val time = FakeTime()
    private val catalog = FakeCatalog()
    private val today = LocalDate.of(2026, 10, 3)
    private val oats = FoodInfo(
        name = "Rolled Oats", brand = "Uncle Tobys", barcode = "9300633603205",
        per100g = Nutrients(380.0, 11.6, 56.6, 8.6, fiberG = 10.4), servingG = 40.0, servingLabel = "40 g",
    )

    @After
    fun tearDown() = db.close()

    private fun repo(scope: TestScope): FoodRepository {
        db = TestDb.inMemory()
        val file = File(TestDb.context.filesDir, "food-test.preferences_pb").apply { delete() }
        val prefs = UserPreferencesRepository(PreferenceDataStoreFactory.create(scope = TestScope(scope.testScheduler)) { file })
        return FoodRepository(db.foodDao(), db.bodyMetricDao(), prefs, catalog, time)
    }

    @Test
    fun scannedFoodsAreCachedForOfflineUse() = runTest {
        val repo = repo(this)
        catalog.products[oats.barcode!!] = oats
        val first = repo.lookupBarcode("9300633603205") as BarcodeResult.Found
        assertEquals(FoodSource.OPEN_FOOD_FACTS.name, first.food.source)
        catalog.offline = true
        val second = repo.lookupBarcode("9300633603205") as BarcodeResult.Found
        assertEquals(first.food.id, second.food.id)
        assertEquals("second scan didn't need the internet", 1, catalog.calls)
    }

    @Test
    fun unknownAndInvalidBarcodes() = runTest {
        val repo = repo(this)
        assertEquals(BarcodeResult.NotFound("12345670"), repo.lookupBarcode("12345670"))
        assertEquals(BarcodeResult.Invalid, repo.lookupBarcode("abc"))
        catalog.offline = true
        assertTrue(repo.lookupBarcode("9300633603205") is BarcodeResult.Failed)
    }

    @Test
    fun loggingScalesNutritionAndTotalsTheDay() = runTest {
        val repo = repo(this)
        val food = repo.saveFromCatalog(oats)
        repo.log(food, 60.0, Meal.BREAKFAST, today)
        repo.quickAdd("Lunch out", Nutrients(kcal = 650.0, proteinG = 35.0), Meal.LUNCH, today)
        val day = repo.observeDay(today).first()
        assertEquals(2, day.size)
        assertEquals(228.0 + 650.0, day.total().kcal, 1e-9)
        assertEquals(6.96 + 35.0, day.total().proteinG, 1e-9)
        assertEquals("Rolled Oats (Uncle Tobys)", day.first().name)
        assertEquals(listOf(food.id), repo.observeRecent().first().map { it.id })
    }

    @Test
    fun editingAnEntryRescalesIt() = runTest {
        val repo = repo(this)
        val food = repo.saveFromCatalog(oats)
        val id = repo.log(food, 40.0, Meal.BREAKFAST, today)
        repo.updateEntry(id, 80.0, Meal.SNACKS)
        val e = repo.getEntry(id)!!
        assertEquals(304.0, e.kcal, 1e-9)
        assertEquals(Meal.SNACKS.name, e.meal)
        repo.deleteEntry(id)
        assertEquals(0, repo.observeDay(today).first().size)
        repo.restoreEntry(id)
        assertEquals(1, repo.observeDay(today).first().size)
    }

    @Test
    fun loggedEntriesKeepTheirNumbersWhenTheFoodChanges() = runTest {
        val repo = repo(this)
        val food = repo.saveFromCatalog(oats)
        repo.log(food, 100.0, Meal.BREAKFAST, today)
        repo.saveCustom(food.id, oats.copy(per100g = oats.per100g.copy(kcal = 999.0)))
        assertEquals(380.0, repo.observeDay(today).first().single().kcal, 1e-9)
        // Your edit survives a later re-scan.
        catalog.products[oats.barcode!!] = oats
        val again = repo.lookupBarcode(oats.barcode!!) as BarcodeResult.Found
        assertEquals(999.0, again.food.kcal, 1e-9)
    }

    @Test
    fun copyingYesterdaysBreakfast() = runTest {
        val repo = repo(this)
        val food = repo.saveFromCatalog(oats)
        repo.log(food, 50.0, Meal.BREAKFAST, today.minusDays(1))
        repo.log(food, 20.0, Meal.SNACKS, today.minusDays(1))
        assertEquals(1, repo.copyMeal(today.minusDays(1), Meal.BREAKFAST, today))
        assertEquals(50.0, repo.observeDay(today).first().single().grams!!, 1e-9)
    }

    @Test
    fun barcodeClashesAreCaught() = runTest {
        val repo = repo(this)
        val a = repo.saveCustom(null, oats.copy(name = "A", barcode = "11111111"))
        val b = repo.saveCustom(null, oats.copy(name = "B", barcode = "22222222"))
        val error = runCatching { repo.saveCustom(b, oats.copy(name = "B", barcode = "11111111")) }.exceptionOrNull()
        assertTrue(error is BarcodeInUse)
        repo.deleteFood(a)
        repo.saveCustom(b, oats.copy(name = "B", barcode = "11111111"))
        assertEquals(b, db.foodDao().getByBarcode("11111111")!!.id)
    }

    @Test
    fun targetsNeedYourDetails() {
        val missing = FoodRepository.targetsFor(UserPreferences(), null, 2026) as TargetsState.Missing
        assertEquals(4, missing.needed.size)
        val prefs = UserPreferences(heightCm = 178.0, birthYear = 2009, sex = Sex.MALE)
        val calculated = FoodRepository.targetsFor(prefs, 70.0, 2026)
        assertEquals(2690, calculated.targetsOrNull!!.kcal)
        val custom = FoodRepository.targetsFor(prefs.copy(customTargets = CustomTargets(2500, 150, 280, 80)), 70.0, 2026)
        assertTrue(custom is TargetsState.Custom)
        assertNull(FoodRepository.targetsFor(UserPreferences(), 70.0, 2026).targetsOrNull)
    }
}

package app.forge.fitness.data.nutrition

import app.forge.domain.model.BodyMetricKind
import app.forge.domain.nutrition.DailyTargets
import app.forge.domain.nutrition.FoodInfo
import app.forge.domain.nutrition.GenericFood
import app.forge.domain.nutrition.Meal
import app.forge.domain.nutrition.Nutrients
import app.forge.domain.nutrition.NutritionTargets
import app.forge.domain.nutrition.OpenFoodFacts
import app.forge.domain.nutrition.TargetsInput
import app.forge.fitness.data.db.BodyMetricDao
import app.forge.fitness.data.db.FoodDao
import app.forge.fitness.data.db.FoodEntity
import app.forge.fitness.data.db.FoodLogEntity
import app.forge.fitness.data.db.FoodSource
import app.forge.fitness.data.prefs.UserPreferences
import app.forge.fitness.data.prefs.UserPreferencesRepository
import app.forge.fitness.di.TimeSource
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

val FoodEntity.per100g: Nutrients get() = Nutrients(kcal, proteinG, carbsG, fatG, fiberG, sugarG, saltG)

val FoodLogEntity.nutrients: Nutrients get() = Nutrients(kcal, proteinG, carbsG, fatG, fiberG, sugarG, saltG)

fun List<FoodLogEntity>.total(): Nutrients = fold(Nutrients.ZERO) { sum, e -> sum + e.nutrients }

/** Where targets came from, so the UI can explain them. */
sealed interface TargetsState {
    data class Calculated(val targets: DailyTargets) : TargetsState
    data class Custom(val targets: DailyTargets) : TargetsState

    /** Needs these before it can calculate (e.g. "your weight", "your birth year"). */
    data class Missing(val needed: List<String>) : TargetsState

    val targetsOrNull: DailyTargets? get() = when (this) {
        is Calculated -> targets
        is Custom -> targets
        is Missing -> null
    }
}

@Singleton
class FoodRepository @Inject constructor(
    private val dao: FoodDao,
    private val bodyMetrics: BodyMetricDao,
    private val preferences: UserPreferencesRepository,
    private val catalog: FoodCatalog,
    private val time: TimeSource,
) {
    // ---- Log ---------------------------------------------------------------------------

    fun observeDay(day: LocalDate): Flow<List<FoodLogEntity>> = dao.observeDay(day.toEpochDay())

    fun observeRange(from: LocalDate, to: LocalDate): Flow<List<FoodLogEntity>> =
        dao.observeRange(from.toEpochDay(), to.toEpochDay())

    suspend fun getEntry(id: String): FoodLogEntity? = dao.getEntry(id)

    /** Logs [grams] of [food] and returns the entry id. */
    suspend fun log(food: FoodEntity, grams: Double, meal: Meal, day: LocalDate): String {
        val now = time.now()
        val id = UUID.randomUUID().toString()
        val n = food.per100g.forGrams(grams)
        dao.insertEntries(
            listOf(
                FoodLogEntity(
                    id = id, foodId = food.id, name = displayName(food), epochDay = day.toEpochDay(), meal = meal.name,
                    grams = grams, kcal = n.kcal, proteinG = n.proteinG, carbsG = n.carbsG, fatG = n.fatG,
                    fiberG = n.fiberG, sugarG = n.sugarG, saltG = n.saltG,
                    loggedAt = now, createdAt = now, updatedAt = now,
                ),
            ),
        )
        return id
    }

    /** Just calories and macros, e.g. a meal out. */
    suspend fun quickAdd(name: String, nutrients: Nutrients, meal: Meal, day: LocalDate): String {
        val now = time.now()
        val id = UUID.randomUUID().toString()
        dao.insertEntries(
            listOf(
                FoodLogEntity(
                    id = id, name = name.ifBlank { "Quick add" }, epochDay = day.toEpochDay(), meal = meal.name,
                    kcal = nutrients.kcal, proteinG = nutrients.proteinG, carbsG = nutrients.carbsG, fatG = nutrients.fatG,
                    loggedAt = now, createdAt = now, updatedAt = now,
                ),
            ),
        )
        return id
    }

    /** Changes the amount (rescaling the nutrition) and/or the meal of an entry. */
    suspend fun updateEntry(id: String, grams: Double?, meal: Meal) {
        val entry = dao.getEntry(id) ?: return
        val now = time.now()
        val old = entry.grams
        val updated = if (grams != null && old != null && old > 0 && grams != old) {
            val n = entry.nutrients * (grams / old)
            entry.copy(
                grams = grams, kcal = n.kcal, proteinG = n.proteinG, carbsG = n.carbsG, fatG = n.fatG,
                fiberG = n.fiberG, sugarG = n.sugarG, saltG = n.saltG,
            )
        } else {
            entry
        }
        dao.updateEntry(updated.copy(meal = meal.name, updatedAt = now))
    }

    suspend fun deleteEntry(id: String) = editEntry(id) { it.copy(deletedAt = time.now()) }

    suspend fun restoreEntry(id: String) = editEntry(id) { it.copy(deletedAt = null) }

    /** Copies one meal from another day (e.g. "same breakfast as yesterday"). Returns how many. */
    suspend fun copyMeal(fromDay: LocalDate, meal: Meal, toDay: LocalDate): Int {
        val now = time.now()
        val source = dao.getMeal(fromDay.toEpochDay(), meal.name)
        dao.insertEntries(
            source.mapIndexed { i, e ->
                e.copy(
                    id = UUID.randomUUID().toString(), epochDay = toDay.toEpochDay(),
                    loggedAt = now + i, createdAt = now, updatedAt = now, isDemo = false,
                )
            },
        )
        return source.size
    }

    private suspend fun editEntry(id: String, change: (FoodLogEntity) -> FoodLogEntity) {
        val entry = dao.getEntry(id) ?: return
        dao.updateEntry(change(entry).copy(updatedAt = time.now()))
    }

    // ---- Foods -------------------------------------------------------------------------

    fun search(query: String): Flow<List<FoodEntity>> = dao.search(query.trim())

    fun observeRecent(limit: Int = 30): Flow<List<FoodEntity>> = dao.observeRecent(limit)

    fun observeFavorites(): Flow<List<FoodEntity>> = dao.observeFavorites()

    suspend fun getFood(id: String): FoodEntity? = dao.getFood(id)

    suspend fun toggleFavorite(id: String) {
        val food = dao.getFood(id) ?: return
        dao.updateFood(food.copy(favorite = !food.favorite, updatedAt = time.now()))
    }

    /**
     * Finds a barcode: on the phone first (works offline, and keeps your edits), then
     * Open Food Facts, saving what it finds so next time is instant.
     */
    suspend fun lookupBarcode(barcode: String): BarcodeResult {
        val code = barcode.trim()
        if (!OpenFoodFacts.isValidBarcode(code)) return BarcodeResult.Invalid
        dao.getByBarcode(code)?.let { local ->
            if (local.deletedAt == null) return BarcodeResult.Found(local)
        }
        return when (val r = catalog.product(code)) {
            is LookupResult.Found -> BarcodeResult.Found(saveFromCatalog(r.food.copy(barcode = r.food.barcode ?: code)))
            LookupResult.NotFound -> BarcodeResult.NotFound(code)
            is LookupResult.Failed -> BarcodeResult.Failed(r.message)
        }
    }

    suspend fun searchOnline(query: String): SearchResult = catalog.search(query)

    /** Saves (or refreshes) a food from Open Food Facts, matched by barcode. */
    suspend fun saveFromCatalog(info: FoodInfo): FoodEntity {
        val now = time.now()
        val existing = info.barcode?.let { dao.getByBarcode(it) }
        if (existing != null) {
            // Keep foods you've edited yourself as they are; just bring deleted ones back.
            val refreshed = if (existing.source == FoodSource.CUSTOM.name) existing else existing.withInfo(info)
            val restored = refreshed.copy(deletedAt = null, updatedAt = now)
            if (restored != existing) dao.updateFood(restored)
            return restored
        }
        val food = FoodEntity(
            id = UUID.randomUUID().toString(), name = info.name, kcal = 0.0, proteinG = 0.0, carbsG = 0.0, fatG = 0.0,
            source = FoodSource.OPEN_FOOD_FACTS.name, createdAt = now, updatedAt = now,
        ).withInfo(info)
        dao.insertFood(food)
        return food
    }

    /**
     * A food from the bundled database. It gets a fixed id, so picking it again (or on
     * another phone, via a backup) never makes a duplicate.
     */
    suspend fun saveGeneric(food: GenericFood): FoodEntity {
        val now = time.now()
        val id = "ausnut-${food.key}"
        dao.getFood(id)?.let { existing ->
            val restored = existing.copy(deletedAt = null, updatedAt = if (existing.deletedAt != null) now else existing.updatedAt)
            if (restored != existing) dao.updateFood(restored)
            return restored
        }
        val entity = FoodEntity(
            id = id, name = food.name, kcal = 0.0, proteinG = 0.0, carbsG = 0.0, fatG = 0.0,
            source = FoodSource.GENERIC.name, createdAt = now, updatedAt = now,
        ).withInfo(FoodInfo(name = food.name, per100g = food.per100g))
        dao.insertFood(entity)
        return entity
    }

    /** Creates (id null) or updates one of your own foods. Returns its id. */
    suspend fun saveCustom(id: String?, info: FoodInfo): String {
        val now = time.now()
        val existing = id?.let { dao.getFood(it) }
        if (existing != null) {
            val owner = info.barcode?.let { dao.getByBarcode(it) }
            if (owner != null && owner.id != existing.id && owner.deletedAt == null) throw BarcodeInUse(owner.name)
            if (owner != null && owner.id != existing.id) {
                // A deleted food holds the barcode: release it so the unique index allows the move.
                dao.updateFood(owner.copy(barcode = null, updatedAt = now))
            }
            dao.updateFood(existing.withInfo(info).copy(source = FoodSource.CUSTOM.name, updatedAt = now))
            return existing.id
        }
        // A barcode you've typed in might already belong to a deleted food.
        val clash = info.barcode?.let { dao.getByBarcode(it) }
        if (clash != null) {
            dao.updateFood(clash.withInfo(info).copy(source = FoodSource.CUSTOM.name, deletedAt = null, updatedAt = now))
            return clash.id
        }
        val food = FoodEntity(
            id = UUID.randomUUID().toString(), name = info.name, kcal = 0.0, proteinG = 0.0, carbsG = 0.0, fatG = 0.0,
            source = FoodSource.CUSTOM.name, createdAt = now, updatedAt = now,
        ).withInfo(info)
        dao.insertFood(food)
        return food.id
    }

    suspend fun deleteFood(id: String) {
        val food = dao.getFood(id) ?: return
        dao.updateFood(food.copy(deletedAt = time.now(), updatedAt = time.now()))
    }

    private fun FoodEntity.withInfo(info: FoodInfo) = copy(
        name = info.name,
        brand = info.brand,
        barcode = info.barcode,
        kcal = info.per100g.kcal,
        proteinG = info.per100g.proteinG,
        carbsG = info.per100g.carbsG,
        fatG = info.per100g.fatG,
        fiberG = info.per100g.fiberG,
        sugarG = info.per100g.sugarG,
        saltG = info.per100g.saltG,
        servingG = info.servingG,
        servingLabel = info.servingLabel,
    )

    // ---- Targets -----------------------------------------------------------------------

    fun observeTargets(): Flow<TargetsState> =
        combine(preferences.preferences, bodyMetrics.observeLatest(BodyMetricKind.WEIGHT)) { prefs, weight ->
            targetsFor(prefs, weight?.value, LocalDate.now().year)
        }

    companion object {
        fun displayName(food: FoodEntity) = if (food.brand != null) "${food.name} (${food.brand})" else food.name

        fun targetsFor(prefs: UserPreferences, weightKg: Double?, currentYear: Int): TargetsState {
            prefs.customTargets?.let { c ->
                return TargetsState.Custom(DailyTargets(c.kcal, c.proteinG, c.carbsG, c.fatG, listOf("Your own targets.")))
            }
            val needed = buildList {
                if (weightKg == null) add("your weight")
                if (prefs.heightCm == null) add("your height")
                if (prefs.birthYear == null) add("your birth year")
                if (prefs.sex == null) add("sex (for the formula)")
            }
            if (needed.isNotEmpty()) return TargetsState.Missing(needed)
            val age = (currentYear - prefs.birthYear!!).coerceIn(13, 100)
            return TargetsState.Calculated(
                NutritionTargets.calculate(
                    TargetsInput(weightKg!!, prefs.heightCm!!, age, prefs.sex!!, prefs.activityLevel, prefs.nutritionGoal),
                ),
            )
        }
    }
}

class BarcodeInUse(val foodName: String) : IllegalArgumentException("That barcode already belongs to $foodName")

sealed interface BarcodeResult {
    data class Found(val food: FoodEntity) : BarcodeResult
    data class NotFound(val barcode: String) : BarcodeResult
    data class Failed(val message: String) : BarcodeResult
    data object Invalid : BarcodeResult
}

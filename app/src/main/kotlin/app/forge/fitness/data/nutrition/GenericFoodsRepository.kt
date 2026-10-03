package app.forge.fitness.data.nutrition

import android.content.Context
import app.forge.domain.nutrition.GenericFood
import app.forge.domain.nutrition.GenericFoodIndex
import app.forge.domain.nutrition.GenericMatches
import app.forge.domain.nutrition.Nutrients
import app.forge.domain.nutrition.Portion
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
private data class GenericFoodsFile(val foods: List<Row>) {
    @Serializable
    data class Row(
        @SerialName("k") val key: String,
        @SerialName("n") val name: String,
        @SerialName("e") val kcal: Double,
        @SerialName("p") val protein: Double = 0.0,
        @SerialName("c") val carbs: Double = 0.0,
        @SerialName("f") val fat: Double = 0.0,
        @SerialName("fi") val fibre: Double? = null,
        @SerialName("s") val sugars: Double? = null,
        @SerialName("salt") val salt: Double? = null,
        /** Portion sizes: [{"d": "1 slice", "g": 32}]. */
        @SerialName("m") val measures: List<Measure> = emptyList(),
    )

    @Serializable
    data class Measure(@SerialName("d") val label: String, @SerialName("g") val grams: Double)
}

/**
 * The bundled Australian food database (AUSNUT 2023, FSANZ): 3,700+ everyday foods,
 * offline. Built by tools/food_data/fetch_ausnut.py. Loaded the first time you search.
 */
@Singleton
class GenericFoodsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val lock = Mutex()
    private var index: GenericFoodIndex? = null

    private suspend fun index(): GenericFoodIndex = lock.withLock {
        index ?: withContext(Dispatchers.IO) {
            val rows = runCatching {
                context.assets.open(ASSET).bufferedReader().use { json.decodeFromString(GenericFoodsFile.serializer(), it.readText()).foods }
            }.getOrDefault(emptyList())
            GenericFoodIndex(
                rows.map {
                    GenericFood(
                        key = it.key,
                        name = it.name,
                        per100g = Nutrients(
                            kcal = it.kcal, proteinG = it.protein, carbsG = it.carbs, fatG = it.fat,
                            fiberG = it.fibre, sugarG = it.sugars, saltG = it.salt,
                        ),
                        portions = it.measures.filter { m -> m.grams > 0 }.map { m -> Portion(m.label, m.grams) },
                    )
                },
            )
        }.also { index = it }
    }

    suspend fun byKey(key: String): GenericFood? = index().byKey(key)

    suspend fun search(query: String, limit: Int = 20): GenericMatches {
        val i = index()
        return withContext(Dispatchers.Default) { i.search(query, limit) }
    }

    private companion object {
        const val ASSET = "generic_foods.json"
    }
}

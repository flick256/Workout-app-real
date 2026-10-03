package app.forge.domain.nutrition

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

/**
 * Reads Open Food Facts API responses (https://world.openfoodfacts.org). Product data is
 * crowd-sourced, so every field is treated as optional and numbers may arrive as strings.
 */
object OpenFoodFacts {
    /** Only the fields Forge uses, to keep responses small. */
    const val FIELDS = "code,product_name,product_name_en,generic_name,brands,nutriments,serving_size,serving_quantity"

    private val json = Json { ignoreUnknownKeys = true }

    /** A product lookup (`/api/v2/product/{barcode}.json`). Null when not found or unusable. */
    fun parseProduct(body: String): FoodInfo? {
        val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull() ?: return null
        if ((root["status"] as? JsonPrimitive)?.intOrNull == 0) return null
        val product = root["product"] as? JsonObject ?: return null
        return toFood(product, fallbackBarcode = (root["code"] as? JsonPrimitive)?.contentOrNull)
    }

    /** A search (`/cgi/search.pl?json=1`): the usable products, in the order given. */
    fun parseSearch(body: String): List<FoodInfo> {
        val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull() ?: return emptyList()
        val products = runCatching { root["products"]?.jsonArray }.getOrNull() ?: return emptyList()
        return products.mapNotNull { (it as? JsonObject)?.let { p -> toFood(p, null) } }
    }

    fun toFood(product: JsonObject, fallbackBarcode: String?): FoodInfo? {
        val name = listOf("product_name", "product_name_en", "generic_name")
            .firstNotNullOfOrNull { product.string(it)?.trim()?.takeIf(String::isNotEmpty) } ?: return null
        val n = product["nutriments"] as? JsonObject ?: return null

        val protein = n.number("proteins_100g")
        val carbs = n.number("carbohydrates_100g")
        val fat = n.number("fat_100g")
        val kcal = n.number("energy-kcal_100g")
            ?: n.number("energy-kj_100g")?.let { it / KJ_PER_KCAL }
            // "energy_100g" is in kJ.
            ?: n.number("energy_100g")?.let { it / KJ_PER_KCAL }
            ?: if (protein != null && carbs != null && fat != null) protein * 4 + carbs * 4 + fat * 9 else null
        if (kcal == null) return null

        val serving = product.number("serving_quantity")?.takeIf { it > 0 && it < 5_000 }
        return FoodInfo(
            name = name,
            brand = product.string("brands")?.split(',')?.firstOrNull()?.trim()?.takeIf(String::isNotEmpty),
            barcode = product.string("code") ?: fallbackBarcode,
            per100g = Nutrients(
                kcal = kcal,
                proteinG = protein ?: 0.0,
                carbsG = carbs ?: 0.0,
                fatG = fat ?: 0.0,
                fiberG = n.number("fiber_100g"),
                sugarG = n.number("sugars_100g"),
                saltG = n.number("salt_100g") ?: n.number("sodium_100g")?.let { it * 2.5 },
            ),
            servingG = serving,
            servingLabel = product.string("serving_size")?.trim()?.takeIf { serving != null && it.isNotEmpty() },
        )
    }

    /** Barcodes are digits only; EAN-8, UPC-A (12), EAN-13 and GTIN-14. */
    fun isValidBarcode(code: String): Boolean = code.length in setOf(8, 12, 13, 14) && code.all(Char::isDigit)

    private const val KJ_PER_KCAL = 4.184

    private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

    private fun JsonObject.number(key: String): Double? {
        val p = this[key] as? JsonPrimitive ?: return null
        return (p.doubleOrNull ?: p.contentOrNull?.replace(',', '.')?.toDoubleOrNull())?.takeIf { it.isFinite() && it >= 0 }
    }
}

package app.forge.fitness.data.nutrition

import app.forge.domain.nutrition.FoodInfo
import app.forge.domain.nutrition.OpenFoodFacts
import app.forge.fitness.BuildConfig
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed interface LookupResult {
    data class Found(val food: FoodInfo) : LookupResult
    data object NotFound : LookupResult
    data class Failed(val message: String) : LookupResult
}

sealed interface SearchResult {
    data class Results(val foods: List<FoodInfo>) : SearchResult
    data class Failed(val message: String) : SearchResult
}

/** Where foods come from when they aren't on the phone yet. */
interface FoodCatalog {
    suspend fun product(barcode: String): LookupResult
    suspend fun search(query: String): SearchResult
}

/**
 * Open Food Facts: a free, open database of 3M+ packaged foods (good Australian
 * coverage). The only thing Forge ever sends over the internet is the barcode or search
 * words you look up; nothing about you is sent.
 */
@Singleton
class OpenFoodFactsClient @Inject constructor() : FoodCatalog {

    override suspend fun product(barcode: String): LookupResult = withContext(Dispatchers.IO) {
        runCatching {
            val (code, body) = get("$BASE/api/v2/product/$barcode.json?fields=${OpenFoodFacts.FIELDS}")
            when {
                code == 404 -> LookupResult.NotFound
                code !in 200..299 -> LookupResult.Failed("Open Food Facts returned an error ($code)")
                else -> OpenFoodFacts.parseProduct(body)?.let { LookupResult.Found(it) } ?: LookupResult.NotFound
            }
        }.getOrElse { LookupResult.Failed(offlineMessage(it)) }
    }

    /**
     * Name search. Open Food Facts retired its old search page (it now mostly answers "503
     * unavailable"), so this uses their new search service, Search-a-licious, and only falls
     * back to the old one if the new one fails.
     */
    override suspend fun search(query: String): SearchResult = withContext(Dispatchers.IO) {
        val q = URLEncoder.encode(query.trim(), "UTF-8")
        val primary = attempt("$SEARCH/search?q=$q&page_size=$PAGE&langs=en&fields=${OpenFoodFacts.FIELDS}")
        if (primary is SearchResult.Results) return@withContext primary
        val legacy = attempt(
            "$BASE/cgi/search.pl?search_terms=$q&search_simple=1&action=process&json=1&page_size=$PAGE" +
                "&fields=${OpenFoodFacts.FIELDS}",
        )
        if (legacy is SearchResult.Results) legacy else primary
    }

    private fun attempt(url: String): SearchResult = runCatching {
        val (code, body) = get(url)
        when {
            code == 429 || code == 503 -> SearchResult.Failed("Open Food Facts is busy right now. Try again in a minute.")
            code !in 200..299 -> SearchResult.Failed("Open Food Facts returned an error ($code)")
            else -> SearchResult.Results(OpenFoodFacts.parseSearch(body))
        }
    }.getOrElse { SearchResult.Failed(offlineMessage(it)) }

    private fun get(url: String): Pair<Int, String> {
        val connection = URL(url).openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            // Open Food Facts asks every app to identify itself.
            connection.setRequestProperty("User-Agent", "Forge/${BuildConfig.VERSION_NAME} (personal Android fitness app)")
            connection.setRequestProperty("Accept", "application/json")
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            code to (stream?.bufferedReader()?.use { it.readText() } ?: "")
        } finally {
            connection.disconnect()
        }
    }

    private fun offlineMessage(e: Throwable) =
        if (e is IOException) "Couldn't reach Open Food Facts. Check your internet connection." else (e.message ?: "Lookup failed")

    private companion object {
        const val BASE = "https://world.openfoodfacts.org"
        const val SEARCH = "https://search.openfoodfacts.org"
        const val PAGE = 30
        const val TIMEOUT_MS = 12_000
    }
}

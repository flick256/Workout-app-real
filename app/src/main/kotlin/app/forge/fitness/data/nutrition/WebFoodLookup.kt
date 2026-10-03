package app.forge.fitness.data.nutrition

import app.forge.domain.nutrition.FoodInfo
import app.forge.domain.nutrition.PageNutrition
import app.forge.domain.nutrition.WebNutrition
import app.forge.fitness.data.ai.AiAssistant
import java.io.IOException
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A food read from a web page, waiting for you to check it. */
data class WebFood(val info: FoodInfo, val url: String, val site: String, val readByAi: Boolean)

sealed interface WebLookupResult {
    /** [more] are other pages to try if this one looks wrong. */
    data class Found(val food: WebFood, val more: List<String>) : WebLookupResult
    data class NotFound(val message: String) : WebLookupResult
    data class Failed(val message: String) : WebLookupResult
}

/**
 * For foods no database has (new menu items, local shops): searches the web (DuckDuckGo),
 * opens the most promising pages (the chain's own site first) and reads the nutrition
 * panel, with the on-device AI when it's installed. Every result is checked (the energy
 * must match the macros; the AI's numbers must appear on the page) and shown to you with
 * its source before anything is saved.
 */
@Singleton
class WebFoodLookup @Inject constructor(
    private val ai: AiAssistant,
) {
    suspend fun lookup(query: String, onProgress: (String) -> Unit = {}): WebLookupResult = withContext(Dispatchers.IO) {
        val q = query.trim()
        onProgress("Searching the web…")
        val urls = runCatching { search("$q nutrition information kJ protein") }
            .getOrElse { return@withContext WebLookupResult.Failed(offline(it)) }
        if (urls.isEmpty()) return@withContext WebLookupResult.NotFound("The web search came back empty. Try different words.")
        tryPages(q, urls, onProgress)
    }

    /** The next pages from an earlier search, when the first answer didn't look right. */
    suspend fun tryPages(query: String, urls: List<String>, onProgress: (String) -> Unit = {}): WebLookupResult =
        withContext(Dispatchers.IO) {
            val ranked = WebNutrition.rank(urls)
            ranked.take(MAX_PAGES).forEachIndexed { i, url ->
                val site = WebNutrition.host(url) ?: return@forEachIndexed
                onProgress("Reading $site…")
                val found = runCatching { read(query, url) }.getOrNull()
                if (found != null) {
                    return@withContext WebLookupResult.Found(
                        WebFood(found.first.toFoodInfo(titleCase(query), brandFor(site)), url, site, found.second),
                        more = ranked.drop(i + 1),
                    )
                }
            }
            WebLookupResult.NotFound(
                if (ai.isAvailable) "Couldn't find nutrition numbers for \"$query\" on the pages found. Try adding the brand, " +
                    "or create it with your own numbers."
                else "Couldn't read nutrition numbers from the pages found. The on-device AI can read many more page " +
                    "layouts (Settings → On-device AI), or create it with your own numbers.",
            )
        }

    private fun search(q: String): List<String> {
        val encoded = URLEncoder.encode(q, "UTF-8")
        val headers = mapOf("User-Agent" to Http.USER_AGENT_BROWSER, "Accept-Language" to "en-AU,en;q=0.9")
        for (url in listOf("https://html.duckduckgo.com/html/?q=$encoded&kl=au-en", "https://lite.duckduckgo.com/lite/?q=$encoded&kl=au-en")) {
            val r = Http.get(url, headers, timeoutMs = 10_000)
            if (r.code in 200..299) {
                val links = WebNutrition.searchResults(r.body)
                if (links.isNotEmpty()) return links
            }
        }
        return emptyList()
    }

    /** The page's nutrition, and whether the AI read it. */
    private suspend fun read(query: String, url: String): Pair<PageNutrition, Boolean>? {
        val r = Http.get(
            url,
            mapOf("User-Agent" to Http.USER_AGENT_BROWSER, "Accept-Language" to "en-AU,en;q=0.9", "Accept" to "text/html"),
            timeoutMs = 10_000,
            maxChars = 1_500_000,
        )
        if (r.code !in 200..299) return null
        val text = WebNutrition.pageText(r.body)
        val window = WebNutrition.nutritionWindow(text) ?: return null
        ai.readNutrition(query, window)?.let { return it to true }
        return WebNutrition.readPanel(window)?.let { it to false }
    }

    private fun titleCase(q: String) = q.split(' ').filter { it.isNotBlank() }.joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }

    private fun brandFor(site: String): String? = BRANDS.entries.firstOrNull { (domain, _) -> site == domain || site.endsWith(".$domain") }?.value

    private fun offline(e: Throwable) =
        if (e is IOException) "Couldn't reach the web. Check your internet connection." else (e.message ?: "Web lookup failed")

    private companion object {
        const val MAX_PAGES = 4
        val BRANDS = mapOf(
            "mcdonalds.com.au" to "McDonald's", "kfc.com.au" to "KFC", "hungryjacks.com.au" to "Hungry Jack's",
            "dominos.com.au" to "Domino's", "subway.com" to "Subway", "guzmanygomez.com.au" to "Guzman y Gomez",
            "nandos.com.au" to "Nando's", "redrooster.com.au" to "Red Rooster", "oporto.com.au" to "Oporto",
            "grilld.com.au" to "Grill'd", "zambrero.com.au" to "Zambrero", "bakersdelight.com.au" to "Bakers Delight",
            "boostjuice.com.au" to "Boost Juice", "starbucks.com.au" to "Starbucks", "pizzahut.com.au" to "Pizza Hut",
        )
    }
}

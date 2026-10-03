package app.forge.fitness.data.nutrition

import app.forge.domain.nutrition.FoodInfo
import app.forge.domain.nutrition.Nutrients
import app.forge.domain.nutrition.PageNutrition
import app.forge.domain.nutrition.WebNutrition
import app.forge.domain.nutrition.WebNutrition.SiteHit
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
    /** [more] are other pages to try if this one looks wrong; [tried] says what happened on the way. */
    data class Found(val food: WebFood, val more: List<String>, val tried: List<String>) : WebLookupResult
    data class NotFound(val message: String, val tried: List<String>) : WebLookupResult
    data class Failed(val message: String, val tried: List<String> = emptyList()) : WebLookupResult
}

sealed interface QuickWebResult {
    data class Hits(val hits: List<SiteHit>) : QuickWebResult
    data class Failed(val message: String) : QuickWebResult
}

/**
 * Finds foods on the web, no account or key needed.
 *
 * - [quickSearch]: FatSecret Australia's public food search, a list of matching products
 *   with calories per serve (Australian brands, chains, bakeries), in one request.
 * - [lookup]: a full web search (DuckDuckGo, Bing, then Mojeek, whichever answers) that
 *   opens the most promising pages, the chain's own site first, and reads the nutrition:
 *   page data (schema.org) first, then the on-device AI, then a plain table reader.
 *
 * Every result is checked (energy must match the macros; the AI's numbers must be on the
 * page) and shown with its source before anything is saved. Only your search words are
 * sent, at the pace you search, like using the sites in a browser.
 */
@Singleton
class WebFoodLookup @Inject constructor(
    private val ai: AiAssistant,
) {
    private val browser = mapOf(
        "User-Agent" to Http.USER_AGENT_BROWSER,
        "Accept-Language" to "en-AU,en;q=0.9",
        "Accept" to "text/html,application/xhtml+xml",
    )

    suspend fun quickSearch(query: String): QuickWebResult = withContext(Dispatchers.IO) {
        runCatching {
            val q = URLEncoder.encode(query.trim(), "UTF-8")
            val r = Http.get("$FS_AU/calories-nutrition/search?q=$q", browser, timeoutMs = 10_000)
            when {
                r.code !in 200..299 -> QuickWebResult.Failed("fatsecret.com.au didn't answer (${r.code})")
                else -> QuickWebResult.Hits(WebNutrition.fatSecretSiteResults(r.body, FS_AU).take(25))
            }
        }.getOrElse { QuickWebResult.Failed(offline(it)) }
    }

    /**
     * A quick-search hit as a food: its per-serve numbers, with the serving's weight read
     * from its page (or counted as 100 g when the page doesn't say).
     */
    suspend fun detail(hit: SiteHit): FoodInfo? = withContext(Dispatchers.IO) {
        val kcal = hit.kcal ?: return@withContext null
        val grams = runCatching {
            val r = Http.get(hit.url, browser, timeoutMs = 10_000)
            if (r.code in 200..299) WebNutrition.servingGrams(WebNutrition.pageText(r.body)) else null
        }.getOrNull()
        val serving = PageNutrition(
            name = hit.name, kcal = kcal, proteinG = hit.proteinG ?: 0.0, carbsG = hit.carbsG ?: 0.0, fatG = hit.fatG ?: 0.0,
            servingG = grams, servingLabel = hit.per?.let { p -> grams?.let { "$p (${it.toInt()} g)" } ?: p }, perHundred = false,
        )
        serving.toFoodInfo(hit.name, hit.brand)
    }

    suspend fun lookup(query: String, onProgress: (String) -> Unit = {}): WebLookupResult = withContext(Dispatchers.IO) {
        val q = query.trim()
        val tried = mutableListOf<String>()
        onProgress("Searching the web…")
        val urls = try {
            search("$q nutrition information", tried)
        } catch (e: IOException) {
            return@withContext WebLookupResult.Failed(offline(e), tried)
        }
        if (urls.isEmpty()) {
            return@withContext WebLookupResult.NotFound("No search engine answered with results. Check your internet, or try again in a minute.", tried)
        }
        read(q, urls, tried, onProgress)
    }

    /** The next pages from an earlier search, when the first answer didn't look right. */
    suspend fun tryPages(query: String, urls: List<String>, onProgress: (String) -> Unit = {}): WebLookupResult =
        withContext(Dispatchers.IO) { read(query, urls, mutableListOf(), onProgress) }

    private suspend fun read(query: String, urls: List<String>, tried: MutableList<String>, onProgress: (String) -> Unit): WebLookupResult {
        val ranked = WebNutrition.rank(urls)
        ranked.take(MAX_PAGES).forEachIndexed { i, url ->
            val site = WebNutrition.host(url) ?: return@forEachIndexed
            onProgress("Reading $site…")
            val outcome = runCatching { readPage(query, url) }.getOrElse { Outcome.Problem("couldn't open (${it.javaClass.simpleName})") }
            when (outcome) {
                is Outcome.Read -> {
                    tried += "$site: found it (${outcome.how})"
                    return WebLookupResult.Found(
                        WebFood(outcome.nutrition.toFoodInfo(titleCase(query), brandFor(site)), url, site, outcome.how == "AI"),
                        more = ranked.drop(i + 1),
                        tried = tried,
                    )
                }
                is Outcome.Problem -> tried += "$site: ${outcome.reason}"
            }
        }
        return WebLookupResult.NotFound(
            "Couldn't read nutrition for \"$query\" from the pages found." +
                (if (ai.isAvailable) "" else " The on-device AI reads many more page layouts (Settings → On-device AI).") +
                " You can also create it with the numbers from the packet or website.",
            tried,
        )
    }

    private sealed interface Outcome {
        data class Read(val nutrition: PageNutrition, val how: String) : Outcome
        data class Problem(val reason: String) : Outcome
    }

    private suspend fun readPage(query: String, url: String): Outcome {
        val r = Http.get(url, browser, timeoutMs = 10_000, maxChars = 2_000_000)
        if (r.code !in 200..299) return Outcome.Problem("couldn't open (${r.code})")
        WebNutrition.structuredNutrition(r.body)?.let { return Outcome.Read(it, "page data") }
        val text = WebNutrition.pageText(r.body)
        val window = WebNutrition.nutritionWindow(text) ?: return Outcome.Problem("no nutrition panel on the page")
        ai.readNutrition(query, window)?.let { return Outcome.Read(it, "AI") }
        WebNutrition.readPanel(window)?.let { return Outcome.Read(it, "table") }
        return Outcome.Problem(if (ai.isAvailable) "nutrition panel found but the numbers didn't check out" else "nutrition panel found but couldn't be read without the AI")
    }

    /** Asks DuckDuckGo, Bing and Mojeek; uses whatever answers, best results first. */
    private fun search(q: String, tried: MutableList<String>): List<String> {
        val encoded = URLEncoder.encode(q, "UTF-8")
        val lists = mutableListOf<List<String>>()
        var reachedAny = false
        var lastError: IOException? = null
        fun engine(name: String, block: () -> Http.Response, parse: (String) -> List<String>) {
            try {
                val r = block()
                reachedAny = true
                val links = if (r.code in 200..299) parse(r.body) else emptyList()
                tried += when {
                    r.code !in 200..299 -> "$name: refused (${r.code})"
                    links.isEmpty() -> "$name: no results (it may have asked for a robot check)"
                    else -> "$name: ${links.size} results"
                }
                if (links.isNotEmpty()) lists += links
            } catch (e: IOException) {
                lastError = e
                tried += "$name: couldn't connect"
            }
        }
        engine("DuckDuckGo", {
            Http.postForm(
                "https://html.duckduckgo.com/html/", "q=$encoded&kl=au-en",
                browser + ("Referer" to "https://html.duckduckgo.com/"),
            )
        }, WebNutrition::searchResults)
        if (lists.sumOf { it.size } < 4) {
            engine("Bing", { Http.get("https://www.bing.com/search?q=$encoded&setlang=en-AU&cc=AU", browser) }, WebNutrition::bingResults)
        }
        if (lists.sumOf { it.size } < 4) {
            engine("Mojeek", { Http.get("https://www.mojeek.com/search?q=$encoded", browser) }, WebNutrition::mojeekResults)
        }
        if (!reachedAny) lastError?.let { throw it }
        // Interleave the engines' lists so each one's best results come early.
        val merged = mutableListOf<String>()
        val longest = lists.maxOfOrNull { it.size } ?: 0
        for (i in 0 until longest) lists.forEach { l -> l.getOrNull(i)?.let(merged::add) }
        return merged.distinct()
    }

    private fun titleCase(q: String) = q.split(' ').filter { it.isNotBlank() }.joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }

    private fun brandFor(site: String): String? = BRANDS.entries.firstOrNull { (domain, _) -> site == domain || site.endsWith(".$domain") }?.value

    private fun offline(e: Throwable) =
        if (e is IOException) "Couldn't reach the web. Check your internet connection." else (e.message ?: "Web lookup failed")

    private companion object {
        const val FS_AU = "https://www.fatsecret.com.au"
        const val MAX_PAGES = 5
        val BRANDS = mapOf(
            "mcdonalds.com.au" to "McDonald's", "kfc.com.au" to "KFC", "hungryjacks.com.au" to "Hungry Jack's",
            "dominos.com.au" to "Domino's", "subway.com" to "Subway", "guzmanygomez.com.au" to "Guzman y Gomez",
            "nandos.com.au" to "Nando's", "redrooster.com.au" to "Red Rooster", "oporto.com.au" to "Oporto",
            "grilld.com.au" to "Grill'd", "zambrero.com.au" to "Zambrero", "bakersdelight.com.au" to "Bakers Delight",
            "boostjuice.com.au" to "Boost Juice", "starbucks.com.au" to "Starbucks", "pizzahut.com.au" to "Pizza Hut",
        )
    }
}

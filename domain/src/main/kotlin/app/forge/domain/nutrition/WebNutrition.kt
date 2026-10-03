package app.forge.domain.nutrition

import java.net.URI
import java.net.URLDecoder
import kotlin.math.abs
import kotlin.math.roundToInt

/** Numbers read from a nutrition page, for one serving (or per 100 g when [perHundred]). */
data class PageNutrition(
    val name: String?,
    val kcal: Double,
    val proteinG: Double,
    val carbsG: Double,
    val fatG: Double,
    val servingG: Double?,
    val servingLabel: String?,
    val perHundred: Boolean,
    val sugarG: Double? = null,
    val fiberG: Double? = null,
    val sodiumMg: Double? = null,
) {
    /** In Forge's per-100 g form. A serving with no weight is counted as 100 g so per-serving numbers stay right. */
    fun toFoodInfo(fallbackName: String, brand: String?): FoodInfo {
        val grams = servingG?.takeIf { it > 0 && it < 5_000 }
        val scale = if (perHundred) 1.0 else 100.0 / (grams ?: 100.0)
        return FoodInfo(
            name = name?.takeIf { it.isNotBlank() && it.length <= 80 } ?: fallbackName,
            brand = brand,
            per100g = Nutrients(
                kcal = kcal * scale,
                proteinG = proteinG * scale,
                carbsG = carbsG * scale,
                fatG = fatG * scale,
                fiberG = fiberG?.times(scale),
                sugarG = sugarG?.times(scale),
                saltG = sodiumMg?.let { it * 2.5 / 1000 * scale },
            ),
            servingG = grams ?: if (perHundred) null else 100.0,
            servingLabel = when {
                perHundred -> servingLabel?.takeIf { grams != null }
                grams != null -> servingLabel ?: "1 serve"
                else -> (servingLabel ?: "1 serve") + " (weight unknown, counted as 100 g)"
            },
        )
    }
}

/**
 * Finding a food's nutrition on the web: reading search results, turning a page into
 * text, finding the nutrition panel, reading the numbers, and checking they make sense.
 * Everything here is plain text work; the app does the fetching (and the AI reading).
 */
object WebNutrition {

    /** Result links from DuckDuckGo's HTML pages, in order, without ads or DuckDuckGo's own links. */
    fun searchResults(html: String): List<String> {
        val links = mutableListOf<String>()
        Regex("""uddg=([^&"']+)""").findAll(html).forEach { m ->
            runCatching { URLDecoder.decode(m.groupValues[1], "UTF-8") }.getOrNull()?.let(links::add)
        }
        Regex("""class=["']result(?:__a|-link)["'][^>]*href=["'](https?://[^"']+)["']""").findAll(html).forEach { links += it.groupValues[1] }
        Regex("""href=["'](https?://[^"']+)["'][^>]*class=["']result(?:__a|-link)["']""").findAll(html).forEach { links += it.groupValues[1] }
        return clean(links)
    }

    /** Result links from Bing (which may wrap them in bing.com/ck/a?…&u=a1<base64>). */
    fun bingResults(html: String): List<String> {
        val links = Regex("""<li class="b_algo"[\s\S]*?<a[^>]+href="([^"]+)"""").findAll(html).map { it.groupValues[1] }.toList()
        return clean(links.mapNotNull(::unwrapBing))
    }

    /** Result links from Mojeek. */
    fun mojeekResults(html: String): List<String> {
        val links = mutableListOf<String>()
        Regex("""class="(?:ob|title)"[^>]*href="(https?://[^"]+)"""").findAll(html).forEach { links += it.groupValues[1] }
        Regex("""href="(https?://[^"]+)"[^>]*class="(?:ob|title)"""").findAll(html).forEach { links += it.groupValues[1] }
        return clean(links)
    }

    private fun unwrapBing(href: String): String? {
        val url = href.replace("&amp;", "&")
        if (!url.contains("bing.com/ck/")) return url.takeIf { it.startsWith("http") }
        val u = Regex("[?&]u=a1([^&]+)").find(url)?.groupValues?.get(1) ?: return null
        return runCatching {
            val padded = u.replace('-', '+').replace('_', '/').let { it + "=".repeat((4 - it.length % 4) % 4) }
            String(java.util.Base64.getDecoder().decode(padded))
        }.getOrNull()?.takeIf { it.startsWith("http") }
    }

    private fun clean(links: List<String>): List<String> = links
        .map { it.replace("&amp;", "&") }
        .filter { url -> host(url)?.let { h -> SKIP_HOSTS.none { h == it || h.endsWith(".$it") } } == true }
        .filterNot { it.contains("/y.js") || it.endsWith(".pdf", ignoreCase = true) }
        .distinct()

    /** One product from a nutrition site's own search page (FatSecret Australia). */
    data class SiteHit(
        val name: String,
        val brand: String?,
        val url: String,
        val per: String?,
        val kcal: Double?,
        val proteinG: Double?,
        val carbsG: Double?,
        val fatG: Double?,
    )

    /**
     * FatSecret Australia's public search page (fatsecret.com.au/calories-nutrition/search?q=…):
     * each result has a name link, an optional "(Brand)" and "Per 1 slice - Calories: 94kcal | …".
     */
    fun fatSecretSiteResults(html: String, base: String = "https://www.fatsecret.com.au"): List<SiteHit> {
        val starts = Regex("""<a[^>]*class="prominent"[^>]*>""").findAll(html).map { it.range.first }.toList()
        return starts.mapIndexedNotNull { i, start ->
            val chunk = html.substring(start, if (i + 1 < starts.size) starts[i + 1] else minOf(html.length, start + 1_500))
            val link = Regex("""href="([^"]+)"""").find(chunk)?.groupValues?.get(1) ?: return@mapIndexedNotNull null
            val name = Regex("""class="prominent"[^>]*>([^<]+)<""").find(chunk)?.groupValues?.get(1)?.let(::decodeEntities)?.trim()
                ?.takeIf { it.isNotEmpty() } ?: return@mapIndexedNotNull null
            val brand = Regex("""class="brand"[^>]*>\s*\(?([^<)]+)\)?\s*<""").find(chunk)?.groupValues?.get(1)?.let(::decodeEntities)?.trim()
            val text = decodeEntities(chunk.replace(Regex("<[^>]+>"), " ")).replace(Regex("\\s+"), " ")
            val described = Regex("(?i)Per (.+?) - Calories").find(text)
            SiteHit(
                name = name,
                brand = brand?.takeIf { it.isNotEmpty() },
                url = if (link.startsWith("http")) link else base + link,
                per = described?.groupValues?.get(1)?.trim(),
                kcal = labelled(text, "Calories"),
                proteinG = labelled(text, "Protein"),
                carbsG = labelled(text, "Carbs"),
                fatG = labelled(text, "Fat"),
            )
        }.filter { it.kcal != null }
    }

    /** The weight of a serving on a food page: "1 slice (39 g)" or "Serving size 39g". */
    fun servingGrams(text: String): Double? {
        Regex("(?i)serv(?:ing|e)\\s*size[^\\n]{0,60}").find(text)?.value?.let { line ->
            Regex("(\\d+(?:\\.\\d+)?)\\s*(g|ml)\\b", RegexOption.IGNORE_CASE).findAll(line).lastOrNull()
                ?.groupValues?.get(1)?.toDoubleOrNull()?.takeIf { it > 0 && it < 3_000 }?.let { return it }
        }
        return Regex("\\((\\d+(?:\\.\\d+)?)\\s*g\\)").find(text)?.groupValues?.get(1)?.toDoubleOrNull()?.takeIf { it > 0 && it < 3_000 }
    }

    private fun labelled(text: String, label: String): Double? =
        Regex("$label:\\s*([0-9]+(?:[.,][0-9]+)?)", RegexOption.IGNORE_CASE).find(text)?.groupValues?.get(1)?.replace(',', '.')?.toDoubleOrNull()

    /** Puts pages known for clean, Australian nutrition info first; keeps the search order otherwise. */
    fun rank(urls: List<String>): List<String> = urls.withIndex().sortedBy { (i, url) ->
        val h = host(url).orEmpty()
        val bonus = when {
            OFFICIAL_AU.any { h == it || h.endsWith(".$it") } -> -100
            GOOD_SOURCES.any { h == it || h.endsWith(".$it") } -> -50
            h.endsWith(".au") -> -20
            else -> 0
        }
        bonus + i
    }.map { it.value }

    fun host(url: String): String? = runCatching { URI(url).host?.lowercase()?.removePrefix("www.") }.getOrNull()

    /** The readable text of a page: no scripts or styles, rows on their own lines, entities decoded. */
    fun pageText(html: String): String {
        // Many sites keep the numbers in page data (JSON) rather than visible text: keep that part.
        val data = Regex("(?is)<script[^>]*>(.*?)</script>").findAll(html)
            .map { it.groupValues[1] }
            .filter { it.contains("protein", ignoreCase = true) && it.length < 400_000 }
            .map { json ->
                val start = (json.indexOf("protein", ignoreCase = true) - 3_000).coerceAtLeast(0)
                json.substring(start, minOf(json.length, start + 6_000))
                    .replace(Regex("[{}\\[\\]]"), "\n").replace("\",\"", "\n").replace("\":", ": ").replace("\"", "")
            }
            .take(3)
            .joinToString("\n")
        var t = html
        t = t.replace(Regex("(?is)<(script|style|noscript|svg|head)[^>]*>.*?</\\1>"), " ")
        t = t.replace(Regex("(?is)<!--.*?-->"), " ")
        t = t.replace(Regex("(?i)<\\s*(br|/p|/div|/tr|/li|/h[1-6]|/dt|/dd|/table|/section)[^>]*>"), "\n")
        t = t.replace(Regex("(?i)<\\s*(td|th)[^>]*>"), " | ")
        t = t.replace(Regex("<[^>]+>"), " ")
        t = decodeEntities(t)
        return (t + "\n" + data).lines().map { it.replace(Regex("[ \\t\\u00A0]+"), " ").trim() }.filter { it.isNotEmpty() }.joinToString("\n")
    }

    /**
     * schema.org NutritionInformation in a page's JSON-LD ("calories": "250 calories",
     * "proteinContent": "10 g", "servingSize": "2 slices (78 g)"): the most reliable source.
     */
    fun structuredNutrition(html: String): PageNutrition? {
        val block = Regex("(?is)<script[^>]*application/ld\\+json[^>]*>(.*?)</script>").findAll(html)
            .map { it.groupValues[1] }.firstOrNull { it.contains("NutritionInformation") || it.contains("proteinContent") } ?: return null
        fun field(key: String): String? = Regex("\"$key\"\\s*:\\s*\"?([^\",}]+)").find(block)?.groupValues?.get(1)?.trim()
        fun number(v: String?) = v?.let { Regex("\\d+(?:[.,]\\d+)?").find(it)?.value?.replace(',', '.')?.toDoubleOrNull() }
        val caloriesText = field("calories") ?: return null
        val energy = number(caloriesText) ?: return null
        val kcal = if (caloriesText.contains("kj", ignoreCase = true)) energy / KJ_PER_KCAL else energy
        val serving = field("servingSize")
        val n = PageNutrition(
            name = null,
            kcal = kcal,
            proteinG = number(field("proteinContent")) ?: return null,
            carbsG = number(field("carbohydrateContent")) ?: return null,
            fatG = number(field("fatContent")) ?: return null,
            servingG = serving?.let { Regex("(\\d+(?:\\.\\d+)?)\\s*(g|ml)\\b", RegexOption.IGNORE_CASE).findAll(it).lastOrNull()?.groupValues?.get(1)?.toDoubleOrNull() },
            servingLabel = serving?.let(::decodeEntities),
            perHundred = serving?.replace(" ", "")?.lowercase() in setOf("100g", "100ml"),
            sugarG = number(field("sugarContent")),
            fiberG = number(field("fiberContent")),
            sodiumMg = field("sodiumContent")?.let { v -> number(v)?.let { if (v.contains("mg", true)) it else it * 1000 } },
        )
        return n.takeIf { isPlausible(it) }
    }

    /** The part of the page around its nutrition panel, small enough for the on-device AI. */
    fun nutritionWindow(text: String, maxChars: Int = 2_400): String? {
        val keys = listOf("energy", "kj", "calorie", "protein", "fat", "carb")
        val lower = text.lowercase()
        var best = -1
        var bestScore = 0
        var i = 0
        while (i < lower.length) {
            val end = minOf(lower.length, i + 700)
            val chunk = lower.substring(i, end)
            val score = keys.count { it in chunk } * 10 + Regex("\\d+(\\.\\d+)?\\s*(g|kj|kcal|mg)\\b").findAll(chunk).count()
            if (score > bestScore) { bestScore = score; best = i }
            i += 200
        }
        if (best < 0 || bestScore < 40) return null
        val start = (best - 500).coerceAtLeast(0)
        return text.substring(start, minOf(text.length, start + maxChars))
    }

    /**
     * Reads a plain nutrition panel without the AI. Australian panels have a "per serve" and a
     * "per 100 g" column; when two amounts follow a label, the first is per serve.
     */
    fun readPanel(text: String): PageNutrition? {
        fun amounts(label: String, unit: String): List<Double> {
            val m = Regex("(?i)$label[^0-9\\n]{0,40}((?:\\d+(?:[.,]\\d+)?\\s*$unit\\b[^0-9\\n]{0,12}){1,2})").find(text) ?: return emptyList()
            return Regex("(\\d+(?:[.,]\\d+)?)\\s*$unit\\b", RegexOption.IGNORE_CASE).findAll(m.groupValues[1])
                .mapNotNull { it.groupValues[1].replace(',', '.').toDoubleOrNull() }.toList()
        }
        val kj = amounts("energy", "kj").ifEmpty { amounts("kilojoules", "kj") }
        val kcal = amounts("(?:energy|calories)", "(?:kcal|cal)").ifEmpty { amounts("calories", "") }
        val protein = amounts("protein", "g")
        val fat = amounts("(?:fat,? total|total fat|fat)", "g")
        val carbs = amounts("(?:carbohydrates?,? total|total carbohydrates?|carbohydrates?|carbs)", "g")
        val energy = kcal.firstOrNull() ?: kj.firstOrNull()?.let { it / KJ_PER_KCAL } ?: return null
        val p = protein.firstOrNull() ?: return null
        val f = fat.firstOrNull() ?: return null
        val c = carbs.firstOrNull() ?: return null
        val serving = servingGrams(text)?.takeIf { Regex("(?i)serv(?:ing|e)\\s*size").containsMatchIn(text) }
        val perHundredOnly = serving == null && Regex("(?i)per\\s*100\\s*(g|ml)").containsMatchIn(text) &&
            !Regex("(?i)per\\s*serv").containsMatchIn(text)
        val result = PageNutrition(
            name = null, kcal = energy, proteinG = p, carbsG = c, fatG = f,
            servingG = serving, servingLabel = serving?.let { g ->
                Regex("(?i)serv(?:ing|e)\\s*size:?\\s*([^\\n|]{1,40})").find(text)?.groupValues?.get(1)?.trim()
                    ?.takeIf { it.isNotEmpty() && it.any(Char::isLetter) && !it.startsWith("${g.roundToInt()}") }
                    ?.let { "$it" } ?: "1 serve (${g.roundToInt()} g)"
            }, perHundred = perHundredOnly,
            sugarG = amounts("sugars?", "g").firstOrNull(),
            sodiumMg = amounts("sodium", "mg").firstOrNull(),
        )
        return result.takeIf { isPlausible(it) }
    }

    /**
     * Energy should roughly match the macros (4/4/9 kcal per gram, with leeway for fibre and
     * alcohol), and nothing can be negative or absurd. Catches misread columns and AI slips.
     */
    fun isPlausible(n: PageNutrition): Boolean {
        val values = listOf(n.kcal, n.proteinG, n.carbsG, n.fatG)
        if (values.any { it < 0 || !it.isFinite() }) return false
        if (n.kcal <= 0 || n.kcal > 5_000) return false
        if (n.perHundred && (n.kcal > 950 || n.proteinG + n.carbsG + n.fatG > 105)) return false
        val fromMacros = n.proteinG * 4 + n.carbsG * 4 + n.fatG * 9
        return abs(fromMacros - n.kcal) <= maxOf(30.0, n.kcal * 0.25)
    }

    /** True when every number the AI gave appears on the page (energy may appear as kJ instead). */
    fun numbersOnPage(n: PageNutrition, text: String): Boolean {
        val onPage = Regex("\\d+(?:[.,]\\d+)?").findAll(text).mapNotNull { it.value.replace(',', '.').toDoubleOrNull() }.toList()
        fun present(v: Double) = onPage.any { abs(it - v) <= maxOf(0.051, v * 0.005) }
        val energyOk = present(n.kcal) || onPage.any { abs(it / KJ_PER_KCAL - n.kcal) <= maxOf(1.5, n.kcal * 0.02) }
        return energyOk && present(n.proteinG) && present(n.carbsG) && present(n.fatG)
    }

    fun decodeEntities(s: String): String {
        var t = s.replace("&nbsp;", " ").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
            .replace("&quot;", "\"").replace("&#39;", "'").replace("&apos;", "'").replace("&rsquo;", "'").replace("&reg;", "®")
        t = Regex("&#(\\d+);").replace(t) { m -> m.groupValues[1].toIntOrNull()?.let { String(Character.toChars(it)) } ?: m.value }
        t = Regex("&#x([0-9a-fA-F]+);").replace(t) { m -> m.groupValues[1].toIntOrNull(16)?.let { String(Character.toChars(it)) } ?: m.value }
        return t
    }

    private const val KJ_PER_KCAL = 4.184

    private val SKIP_HOSTS = setOf(
        "duckduckgo.com", "youtube.com", "facebook.com", "instagram.com", "tiktok.com", "reddit.com", "x.com",
        "twitter.com", "pinterest.com", "pinterest.com.au", "amazon.com", "amazon.com.au", "ubereats.com", "doordash.com",
        "menulog.com.au", "wikipedia.org",
    )

    /** Chains' own sites in Australia. */
    private val OFFICIAL_AU = setOf(
        "mcdonalds.com.au", "mcdonalds.com", "kfc.com.au", "hungryjacks.com.au", "dominos.com.au", "subway.com",
        "guzmanygomez.com.au", "guzmanygomez.com", "nandos.com.au", "redrooster.com.au", "oporto.com.au", "grilld.com.au",
        "zambrero.com.au", "bakersdelight.com.au", "boostjuice.com.au", "starbucks.com.au", "gloriajeanscoffees.com.au",
        "pizzahut.com.au", "tacobell.com.au", "carlsjr.com.au", "sushihub.com.au", "muffinbreak.com.au",
    )

    /** Sites with tidy, Australian-aware nutrition pages. */
    private val GOOD_SOURCES = setOf(
        "calorieking.com", "fatsecret.com.au", "eatthismuch.com", "nutritionix.com", "myfooddata.com",
        "nutritionvalue.org", "woolworths.com.au", "coles.com.au", "aussiefastfoodmacros.com",
    )
}

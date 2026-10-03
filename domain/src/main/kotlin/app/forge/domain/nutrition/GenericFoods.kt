package app.forge.domain.nutrition

/**
 * A generic food from the bundled Australian food database (AUSNUT 2023): breads, fruit,
 * meat, takeaway, home-cooked dishes... Names read like "Bread, from white flour, commercial".
 */
data class GenericFood(val key: String, val name: String, val per100g: Nutrients, val portions: List<Portion> = emptyList())

/** What a search found, and whether it had to ignore some words (e.g. a brand name). */
data class GenericMatches(val foods: List<GenericFood>, val ignoredWords: List<String> = emptyList())

/**
 * Word-based search over [GenericFood] names. Every query word has to match the start of a
 * word in the name ("roll" finds "rolls", but "raisin" doesn't find "self-raising"), and
 * foods whose main word (before the first comma) matches come first, so "white bread"
 * ranks "Bread, from white flour" above "Flour, wheat, white, bread making".
 */
class GenericFoodIndex(private val foods: List<GenericFood>) {
    private val words: List<List<String>> = foods.map { words(it.name) }
    private val heads: List<List<String>> = foods.map { words(it.name.substringBefore(',')) }

    val size: Int get() = foods.size
    private val keyed: Map<String, GenericFood> by lazy { foods.associateBy { it.key } }

    fun byKey(key: String): GenericFood? = keyed[key]

    fun search(query: String, limit: Int = 20): GenericMatches {
        val tokens = words(query).filter { it !in STOP_WORDS }.distinct().take(MAX_WORDS)
        if (tokens.isEmpty()) return GenericMatches(emptyList())
        val all = rank(tokens)
        if (all.isNotEmpty()) return GenericMatches(all.take(limit).map { foods[it.first] })
        // Nothing has every word (often because of a brand, like "Bakers Delight"): use the
        // most words that still find something, and say which ones were left out.
        for (size in tokens.size - 1 downTo 1) {
            val best = subsets(tokens, size)
                .map { subset -> subset to rank(subset) }
                .filter { it.second.isNotEmpty() }
                .maxByOrNull { it.second.first().second }
                ?: continue
            return GenericMatches(best.second.take(limit).map { foods[it.first] }, ignoredWords = tokens - best.first.toSet())
        }
        return GenericMatches(emptyList())
    }

    /** (food index, score), best first. */
    private fun rank(tokens: List<String>): List<Pair<Int, Double>> =
        foods.indices
            .mapNotNull { i ->
                var score = 0.0
                for (t in tokens) {
                    val w = words[i]
                    if (w.none { matches(it, t) }) return@mapNotNull null
                    score += if (w.any { exact(it, t) }) 3 else 1
                    if (heads[i].any { matches(it, t) }) score += 4
                    if (exact(w.first(), t)) score += 2
                }
                // Prefer plain, shorter names ("Banana, raw" over "Banana, cake, iced, homemade").
                score -= words[i].size * 0.15
                i to score
            }
            .sortedWith(compareByDescending<Pair<Int, Double>> { it.second }.thenBy { foods[it.first].name.length })

    private fun subsets(tokens: List<String>, size: Int): List<List<String>> {
        if (size == 0) return listOf(emptyList())
        if (tokens.size < size) return emptyList()
        val head = tokens.first()
        val rest = tokens.drop(1)
        return subsets(rest, size - 1).map { listOf(head) + it } + subsets(rest, size)
    }

    companion object {
        private val STOP_WORDS = setOf("and", "with", "the", "a", "of", "in", "or")
        private const val MAX_WORDS = 6

        fun words(text: String): List<String> =
            text.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }

        /**
         * A query word matches a name word it is (or is the plural/singular of), or a word it
         * starts while you're still typing ("chick" → "chicken"). A start that's only one letter
         * short doesn't count, so "raisin" isn't taken for "raising".
         */
        private fun matches(nameWord: String, token: String): Boolean =
            exact(nameWord, token) || (nameWord.startsWith(token) && nameWord.length - token.length >= 2)

        private fun exact(nameWord: String, token: String): Boolean =
            nameWord == token || nameWord == token.removeSuffix("s") || nameWord == token.removeSuffix("es") ||
                nameWord.removeSuffix("s") == token
    }
}

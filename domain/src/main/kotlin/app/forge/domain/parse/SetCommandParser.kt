package app.forge.domain.parse

import kotlin.math.roundToInt

/** "3x8 bench at 60" → 3 sets of 8 reps at 60 kg on whatever "bench" matches. */
data class SetCommand(
    val exerciseQuery: String,
    val sets: Int = 1,
    val reps: Int? = null,
    val weightKg: Double? = null,
    val seconds: Int? = null,
    val rpe: Double? = null,
) {
    fun describe(): String = buildString {
        append(if (sets > 1) "$sets × " else "1 set of ")
        when {
            reps != null -> append("$reps")
            seconds != null -> append("${seconds}s")
        }
        weightKg?.let { append(" @ ${(it * 10).roundToInt() / 10.0} kg") }
        rpe?.let { append(" RPE $it") }
    }
}

/**
 * Turns what you'd say or type into a set (no AI needed for the common phrasings):
 * "3x8 bench at 60", "bench 60kg 3 sets of 8", "squat 100 for 5", "pushups 3x15",
 * "plank 3x45s", "deadlift 140 kg 5 reps rpe 8", "curls 3 by 12 at 25 pounds".
 */
object SetCommandParser {
    private const val LB_TO_KG = 0.45359237

    private val setsXReps = Regex("""(\d+)\s*(?:x|×|by|sets? of)\s*(\d+)\s*(s|sec|secs|seconds)?\b""")
    private val weight = Regex("""(\d+(?:\.\d+)?)\s*(kg|kgs|kilos?|lb|lbs|pounds?)\b""")
    // Word boundaries so "squat 100" isn't read as "squ" + "at 100".
    private val atWeight = Regex("""(?:\bat|@|\bwith)\s*(\d+(?:\.\d+)?)\b""")
    private val forReps = Regex("""(?:\bfor|\bx)\s*(\d+)\s*(?:reps?)?\b""")
    private val reps = Regex("""(\d+)\s*reps?\b""")
    private val setsOnly = Regex("""(\d+)\s*sets?\b""")
    private val seconds = Regex("""(\d+)\s*(s|sec|secs|seconds)\b""")
    private val rpe = Regex("""rpe\s*(\d+(?:\.\d+)?)""")
    private val numberWords = mapOf(
        "one" to "1", "two" to "2", "three" to "3", "four" to "4", "five" to "5", "six" to "6", "seven" to "7",
        "eight" to "8", "nine" to "9", "ten" to "10", "eleven" to "11", "twelve" to "12", "fifteen" to "15", "twenty" to "20",
    )
    private val fillers = setOf(
        "i", "did", "do", "just", "a", "an", "of", "the", "and", "at", "for", "with", "x", "by", "set", "sets", "rep", "reps",
        "kg", "kgs", "kilo", "kilos", "lb", "lbs", "pound", "pounds", "s", "sec", "secs", "seconds", "rpe", "on", "log", "add",
    )

    fun parse(input: String): SetCommand? {
        var text = " " + input.lowercase().replace(',', '.').replace("-", " ") + " "
        numberWords.forEach { (word, digit) -> text = text.replace(Regex("""\b$word\b"""), digit) }

        var sets = 1
        var repCount: Int? = null
        var secs: Int? = null
        var kg: Double? = null
        val consumed = mutableListOf<IntRange>()

        setsXReps.find(text)?.let { m ->
            sets = m.groupValues[1].toInt()
            if (m.groupValues[3].isNotEmpty()) secs = m.groupValues[2].toInt() else repCount = m.groupValues[2].toInt()
            consumed += m.range
        }
        weight.find(text)?.takeIf { m -> consumed.none { it.overlaps(m.range) } }?.let { m ->
            val v = m.groupValues[1].toDouble()
            kg = if (m.groupValues[2].startsWith("l") || m.groupValues[2].startsWith("p")) v * LB_TO_KG else v
            consumed += m.range
        }
        if (kg == null) {
            atWeight.find(text)?.takeIf { m -> consumed.none { it.overlaps(m.range) } }?.let { m ->
                kg = m.groupValues[1].toDouble(); consumed += m.range
            }
        }
        rpe.find(text)?.let { m -> consumed += m.range }
        val rpeValue = rpe.find(text)?.groupValues?.get(1)?.toDouble()?.takeIf { it in 1.0..10.0 }
        if (repCount == null && secs == null) {
            (reps.find(text) ?: forReps.find(text))?.takeIf { m -> consumed.none { it.overlaps(m.range) } }?.let { m ->
                repCount = m.groupValues[1].toInt(); consumed += m.range
            }
        }
        if (repCount == null && secs == null) {
            seconds.find(text)?.takeIf { m -> consumed.none { it.overlaps(m.range) } }?.let { m ->
                secs = m.groupValues[1].toInt(); consumed += m.range
            }
        }
        setsOnly.find(text)?.takeIf { m -> consumed.none { it.overlaps(m.range) } }?.let { m ->
            sets = m.groupValues[1].toInt(); consumed += m.range
        }
        val loose = Regex("""\b(\d+(?:\.\d+)?)\b""").findAll(text).filter { m -> consumed.none { it.overlaps(m.range) } }.toList()
        if (repCount != null && kg == null && loose.size == 1) {
            // "squat 100 for 5": reps are known, so the leftover number is the weight.
            kg = loose.first().groupValues[1].toDouble(); consumed += loose.first().range
        } else if (repCount == null && secs == null) {
            // A bare "squat 100 5": the bigger number is the weight, the smaller the reps.
            if (loose.size == 2 && kg == null) {
                val (a, b) = loose.map { it.groupValues[1].toDouble() }
                kg = maxOf(a, b); repCount = minOf(a, b).toInt()
                loose.forEach { consumed += it.range }
            } else if (loose.size == 1) {
                repCount = loose.first().groupValues[1].toDouble().toInt(); consumed += loose.first().range
            }
        }

        val name = text.indices.filter { i -> consumed.none { i in it } }.map { text[it] }.joinToString("")
            .replace(Regex("""[^a-z ]"""), " ")
            .split(' ').filter { it.isNotBlank() && it !in fillers }
            .joinToString(" ")
        if (name.isBlank() || (repCount == null && secs == null)) return null
        if (sets !in 1..20 || (repCount ?: 1) !in 1..200 || (secs ?: 1) !in 1..3_600 || (kg ?: 0.0) !in 0.0..600.0) return null
        return SetCommand(name, sets, repCount, kg?.let { (it * 100).roundToInt() / 100.0 }, secs, rpeValue)
    }

    private fun IntRange.overlaps(other: IntRange) = first <= other.last && other.first <= last
}

/** Finds the exercise you meant from a few spoken words. */
object ExerciseMatcher {
    private val synonyms = mapOf(
        "bench" to "bench press", "ohp" to "overhead press", "rdl" to "romanian deadlift", "dl" to "deadlift",
        "pushups" to "push up", "pushup" to "push up", "pullups" to "pull up", "pullup" to "pull up", "chinups" to "chin up",
        "chinup" to "chin up", "dips" to "dip", "curls" to "curl", "squats" to "squat", "lunges" to "lunge", "rows" to "row",
        "situps" to "sit up", "crunches" to "crunch", "planks" to "plank", "shrugs" to "shrug", "raises" to "raise",
    )

    private fun words(s: String): List<String> = s.lowercase().replace(Regex("""[^a-z0-9 ]"""), " ")
        .split(' ').filter { it.isNotBlank() }
        .flatMap { (synonyms[it] ?: it).split(' ') }
        .map { it.removeSuffix("s").ifEmpty { it } }

    /**
     * Best match by shared words; ties go to exercises in [preferred] (e.g. already in this
     * workout) and then the shorter, more basic name. Null if nothing shares a word.
     */
    fun <T> best(query: String, candidates: List<T>, name: (T) -> String, preferred: (T) -> Boolean = { false }): T? {
        val q = words(query).toSet()
        if (q.isEmpty()) return null
        return candidates.map { c ->
            val n = words(name(c))
            val shared = q.count { it in n }
            val score = shared * 10.0 - (n.size - shared) * 0.5 + (if (preferred(c)) 6.0 else 0.0) + (if (n.toSet().containsAll(q)) 3.0 else 0.0)
            Triple(c, shared, score)
        }.filter { it.second > 0 }.maxByOrNull { it.third }?.first
    }
}

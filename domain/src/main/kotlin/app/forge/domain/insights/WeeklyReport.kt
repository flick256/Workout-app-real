package app.forge.domain.insights

import kotlin.math.roundToInt

data class PrLine(val exercise: String, val what: String)

/** The week in numbers, all computed by Forge. */
data class WeeklyFacts(
    val workouts: Int,
    val workoutsLastWeek: Int,
    val sets: Int,
    val volumeKg: Double,
    val volumeLastWeekKg: Double,
    val prs: List<PrLine> = emptyList(),
    val activities: Int = 0,
    val activityMinutes: Int = 0,
    /** Muscles well under their weekly set target. */
    val undertrained: List<String> = emptyList(),
    val avgSleepHours: Double? = null,
    val avgSteps: Int? = null,
    val foodDaysLogged: Int = 0,
    val avgKcal: Int? = null,
    val kcalTarget: Int? = null,
    val avgProteinG: Int? = null,
    val proteinTargetG: Int? = null,
    /** Habit completion this week, 0..100. */
    val habitPercent: Int? = null,
    val goalLines: List<String> = emptyList(),
)

object WeeklyReport {

    /** A clear summary with no AI at all (also the fallback if the AI says something off). */
    fun template(f: WeeklyFacts): String = buildList {
        add(
            when {
                f.workouts == 0 -> "No workouts logged this week."
                else -> "${f.workouts} workout${s(f.workouts)} this week (last week: ${f.workoutsLastWeek}), ${f.sets} hard sets" +
                    if (f.volumeLastWeekKg > 0) ", volume ${pct(f.volumeKg, f.volumeLastWeekKg)} vs last week." else "."
            },
        )
        if (f.prs.isNotEmpty()) add("New records: " + f.prs.take(4).joinToString { "${it.exercise} (${it.what})" } + ".")
        if (f.activities > 0) add("Plus ${f.activities} sport/cardio session${s(f.activities)} (${f.activityMinutes} min).")
        if (f.undertrained.isNotEmpty()) add("Light on: ${f.undertrained.take(3).joinToString()}.")
        f.avgSleepHours?.let { add("Sleep averaged ${r1(it)} h" + if (it < 8) " (aim for 8–10 h)." else ".") }
        if (f.foodDaysLogged > 0 && f.avgProteinG != null) {
            add(
                "Food logged on ${f.foodDaysLogged} day${s(f.foodDaysLogged)}: about ${f.avgKcal ?: "–"} kcal and ${f.avgProteinG} g protein a day" +
                    (f.proteinTargetG?.let { " (target $it g)." } ?: "."),
            )
        }
        f.habitPercent?.let { add("Habits: $it% done.") }
        addAll(f.goalLines.take(3))
    }.joinToString(" ")

    /** The facts as compact lines for the AI to put into words. */
    fun factSheet(f: WeeklyFacts): String = buildList {
        add("workouts_this_week: ${f.workouts}")
        add("workouts_last_week: ${f.workoutsLastWeek}")
        add("hard_sets: ${f.sets}")
        if (f.volumeLastWeekKg > 0) add("volume_change_vs_last_week: ${pct(f.volumeKg, f.volumeLastWeekKg)}")
        f.prs.forEach { add("new_record: ${it.exercise} ${it.what}") }
        if (f.activities > 0) add("sport_or_cardio_sessions: ${f.activities} (${f.activityMinutes} min)")
        if (f.undertrained.isNotEmpty()) add("muscles_below_weekly_set_target: ${f.undertrained.joinToString()}")
        f.avgSleepHours?.let { add("average_sleep_hours: ${r1(it)}") }
        f.avgSteps?.let { add("average_steps: $it") }
        if (f.foodDaysLogged > 0) {
            add("days_food_logged: ${f.foodDaysLogged}")
            f.avgKcal?.let { add("average_kcal: $it" + (f.kcalTarget?.let { t -> " (target $t)" } ?: "")) }
            f.avgProteinG?.let { add("average_protein_g: $it" + (f.proteinTargetG?.let { t -> " (target $t)" } ?: "")) }
        }
        f.habitPercent?.let { add("habits_done_percent: $it") }
        f.goalLines.forEach { add("goal: $it") }
    }.joinToString("\n")

    private fun s(n: Int) = if (n == 1) "" else "s"
    private fun r1(v: Double) = ((v * 10).roundToInt() / 10.0).toString()
    private fun pct(now: Double, before: Double): String {
        val p = ((now - before) / before * 100).roundToInt()
        return if (p >= 0) "+$p%" else "$p%"
    }
}

/**
 * Checks what a small on-device model wrote before you see it. It must not introduce
 * numbers that aren't in the facts it was given, and must not give risky diet advice.
 * If it fails, Forge shows its own plain summary instead.
 */
object OutputGuard {
    private val number = Regex("""\d+(?:\.\d+)?""")
    private val banned = listOf(
        "fast for", "fasting", "skip meal", "skipping meal", "starv", "detox", "cleanse", "laxative", "diuretic",
        "steroid", "sarm", "prohormone", "fat burner", "calorie deficit of", "eat less than", "1200 calories", "1000 calories",
        "purge", "water cut", "sauna suit",
    )

    data class Verdict(val ok: Boolean, val reason: String? = null)

    fun check(output: String, facts: String): Verdict {
        val text = output.trim()
        if (text.length < 20) return Verdict(false, "too short")
        if (text.length > 1_500) return Verdict(false, "too long")
        val lower = text.lowercase()
        banned.firstOrNull { it in lower }?.let { return Verdict(false, "unsafe advice ($it)") }
        val allowed = number.findAll(facts).map { it.value.toDouble() }.toSet()
        val invented = number.findAll(text).map { it.value.toDouble() }
            // Small counts ("2–3 sets", "8–10 h", "a 1RM") are general guidance, not claims about your data.
            .filter { it > 10 }
            .filterNot { n -> allowed.any { kotlin.math.abs(it - n) <= maxOf(0.6, it * 0.02) } }
            .toList()
        return if (invented.isEmpty()) Verdict(true) else Verdict(false, "numbers not in your data: ${invented.take(3).joinToString()}")
    }
}

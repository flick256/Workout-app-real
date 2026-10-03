package app.forge.domain.nutrition

import kotlin.math.max
import kotlin.math.roundToInt

enum class Sex(val label: String) { MALE("Male"), FEMALE("Female"), UNSPECIFIED("Prefer not to say") }

enum class ActivityLevel(val label: String, val description: String, val factor: Double) {
    SEDENTARY("Mostly sitting", "School/desk day, little walking, no training", 1.2),
    LIGHT("Lightly active", "Training 1–3 days a week or lots of walking", 1.375),
    MODERATE("Active", "Training 3–5 days a week", 1.55),
    VERY("Very active", "Hard training or sport 6–7 days a week", 1.725),
    ATHLETE("Athlete", "Twice-a-day training or a physical job plus training", 1.9),
}

enum class NutritionGoal(val label: String, val kcalDelta: Int) {
    LOSE("Lose fat slowly", -400),
    MAINTAIN("Maintain", 0),
    GAIN("Build muscle (lean gain)", 300),
}

data class TargetsInput(
    val weightKg: Double,
    val heightCm: Double,
    val ageYears: Int,
    val sex: Sex,
    val activity: ActivityLevel,
    val goal: NutritionGoal,
)

data class DailyTargets(
    val kcal: Int,
    val proteinG: Int,
    val carbsG: Int,
    val fatG: Int,
    /** Plain-language working, shown under "How is this worked out?". */
    val explanation: List<String>,
)

/**
 * Daily calorie and macro targets.
 *
 *  - Resting burn (BMR) uses the Mifflin–St Jeor equation, the most accurate of the
 *    common formulas for adults (within ~10% for most people).
 *  - Multiplied by an activity factor to estimate the whole day (TDEE).
 *  - Goal adjustment is deliberately gentle. Under 18 you're still growing, so a
 *    deficit is capped at 250 kcal: under-eating hurts growth, hormones and training.
 *  - Protein 1.6 g per kg (2.0 when losing fat) — the range where studies stop seeing
 *    extra muscle gain. Fat at least 25% of energy (and ≥ 0.8 g/kg), carbs make up the rest.
 */
object NutritionTargets {
    const val TEEN_MAX_DEFICIT = 250

    fun bmr(weightKg: Double, heightCm: Double, ageYears: Int, sex: Sex): Double {
        val base = 10 * weightKg + 6.25 * heightCm - 5 * ageYears
        return base + when (sex) {
            Sex.MALE -> 5.0
            Sex.FEMALE -> -161.0
            Sex.UNSPECIFIED -> -78.0
        }
    }

    fun calculate(input: TargetsInput): DailyTargets {
        val bmr = bmr(input.weightKg, input.heightCm, input.ageYears, input.sex)
        val tdee = bmr * input.activity.factor
        val teen = input.ageYears < 18
        val delta = if (teen && input.goal.kcalDelta < 0) max(input.goal.kcalDelta, -TEEN_MAX_DEFICIT) else input.goal.kcalDelta
        // Never go below resting burn + 10%, whatever the goal.
        val kcal = round10(max(tdee + delta, bmr * 1.1))

        val proteinPerKg = if (input.goal == NutritionGoal.LOSE) 2.0 else 1.6
        val protein = (input.weightKg * proteinPerKg).roundToInt()
        val fat = max(kcal * 0.25 / 9, input.weightKg * 0.8).roundToInt()
        val carbs = ((kcal - protein * 4 - fat * 9) / 4.0).roundToInt().coerceAtLeast(0)

        val explanation = buildList {
            add("Resting burn (Mifflin–St Jeor): ${bmr.roundToInt()} kcal")
            add("× ${input.activity.factor} for \"${input.activity.label}\" = ${tdee.roundToInt()} kcal to maintain")
            when {
                delta == 0 -> add("Goal: maintain, so no adjustment")
                teen && input.goal.kcalDelta < delta -> add(
                    "Goal: ${input.goal.label.lowercase()}, ${delta} kcal. Kept small because you're under 18 " +
                        "and still growing; slow and steady keeps your training and growth on track.",
                )
                else -> add("Goal: ${input.goal.label.lowercase()}, ${if (delta > 0) "+" else ""}$delta kcal")
            }
            add("Protein ${proteinPerKg} g per kg of bodyweight = $protein g")
            add("Fat ≥ 25% of calories = $fat g; carbs fill the rest = $carbs g")
            add("These are estimates. Watch your weight trend for 2–3 weeks and adjust by 100–200 kcal if needed.")
        }
        return DailyTargets(kcal, protein, carbs, fat, explanation)
    }

    private fun round10(v: Double) = ((v / 10).roundToInt() * 10)
}

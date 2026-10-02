package app.forge.domain.bodyweight

import app.forge.domain.model.Equipment
import app.forge.domain.model.ExerciseCategory

/**
 * Picks a [BodyweightProfile] for a library exercise from its name. Only bodyweight
 * strength moves qualify; stretches, cardio and anything done with a barbell, machine
 * etc. return null.
 */
object BodyweightMatcher {

    private val bodyweightEquipment = setOf(null, Equipment.BODY_ONLY, Equipment.OTHER, Equipment.PULL_UP_BAR)

    /** Names that mention a bodyweight move but aren't one (or the load is unknowable). */
    private val excluded = listOf(
        "depth jump", "band assisted", "drop push", "kettlebell", "suspended push",
        "pulldown", "pull-in", "pull in", "stretch", "partner", "donkey", "nordic",
    )

    private val rules: List<Pair<Regex, BodyweightProfile>> = listOf(
        "handstand push" to BodyweightProfile.HANDSTAND_PUSH_UP,
        "(single|one)[- ]arm push" to BodyweightProfile.ONE_ARM_PUSH_UP,
        "archer push" to BodyweightProfile.ARCHER_PUSH_UP,
        "elevated pike" to BodyweightProfile.ELEVATED_PIKE_PUSH_UP,
        "pike push" to BodyweightProfile.PIKE_PUSH_UP,
        "pseudo planche" to BodyweightProfile.PSEUDO_PLANCHE_PUSH_UP,
        "incline push" to BodyweightProfile.INCLINE_PUSH_UP,
        "decline push|push-?ups? with feet (elevated|on)" to BodyweightProfile.DECLINE_PUSH_UP,
        "knee push" to BodyweightProfile.KNEE_PUSH_UP,
        "wall push" to BodyweightProfile.WALL_PUSH_UP,
        "push-? ?ups?\\b|pushups?" to BodyweightProfile.PUSH_UP,
        "muscle[- ]?up" to BodyweightProfile.MUSCLE_UP,
        "(one|single)[- ]arm (chin|pull)" to BodyweightProfile.ONE_ARM_PULL_UP,
        "archer pull" to BodyweightProfile.ARCHER_PULL_UP,
        "chin|pull-? ?ups?|pullups?" to BodyweightProfile.PULL_UP,
        "feet[- ]elevated (inverted )?row" to BodyweightProfile.FEET_ELEVATED_ROW,
        "inverted row|australian|table row|bodyweight mid row|suspended row" to BodyweightProfile.INVERTED_ROW,
        "towel row|door(way)? row" to BodyweightProfile.DOORWAY_ROW,
        "bench dip" to BodyweightProfile.BENCH_DIP,
        "\\bdips?\\b" to BodyweightProfile.DIP,
        "pistol|single-leg (high )?box squat|shrimp squat" to BodyweightProfile.PISTOL_SQUAT,
        "bulgarian|rear[- ]foot[- ]elevated" to BodyweightProfile.BULGARIAN_SPLIT_SQUAT,
        "split squat|lunge" to BodyweightProfile.SPLIT_SQUAT,
        "step[- ]up" to BodyweightProfile.STEP_UP,
        "bodyweight squat|jump squat|air squat|prisoner squat" to BodyweightProfile.SQUAT,
        "single[- ]leg glute bridge" to BodyweightProfile.SINGLE_LEG_GLUTE_BRIDGE,
        "glute bridge|butt lift" to BodyweightProfile.GLUTE_BRIDGE,
        "single[- ]leg calf" to BodyweightProfile.SINGLE_LEG_CALF_RAISE,
        "calf raise" to BodyweightProfile.CALF_RAISE,
        "hanging (leg|knee) raise" to BodyweightProfile.HANGING_LEG_RAISE,
        "sit-? ?ups?" to BodyweightProfile.SIT_UP,
        "crunch" to BodyweightProfile.CRUNCH,
    ).map { (pattern, profile) -> Regex(pattern, RegexOption.IGNORE_CASE) to profile }

    fun match(name: String, equipment: Equipment?, category: ExerciseCategory, isStatic: Boolean): BodyweightProfile? {
        if (category != ExerciseCategory.STRENGTH && category != ExerciseCategory.PLYOMETRICS) return null
        if (isStatic || equipment !in bodyweightEquipment) return null
        val lower = name.lowercase()
        if (excluded.any { it in lower }) return null
        return rules.firstOrNull { (regex, _) -> regex.containsMatchIn(lower) }?.second
    }
}

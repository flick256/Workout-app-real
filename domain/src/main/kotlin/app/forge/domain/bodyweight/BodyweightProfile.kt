package app.forge.domain.bodyweight

/** How much we trust a profile's number. Shown in the app next to the estimate. */
enum class Evidence(val label: String) {
    /** Measured with force plates in a published study. */
    MEASURED("Measured in research"),

    /** Worked out from body-segment masses (which parts of you actually move). */
    PHYSICS("Calculated from body-segment masses"),

    /** A reasoned estimate; no good direct measurement exists. */
    ESTIMATE("Estimate"),
}

/** Whether raising your hands or feet changes the load, and which one. */
enum class Elevation { NONE, HANDS, FEET }

/**
 * How much of your bodyweight a calisthenics movement makes you lift or push.
 *
 * [fraction] is the share of bodyweight carried by the working limb(s) on a flat
 * surface. For [unilateral] moves (one-arm push-up, pistol squat) it's the load on
 * the single working arm or leg, which is why those feel so much harder.
 *
 * [addedFactor] is how much of a vest or bag actually counts. Weight hanging from you
 * in a pull-up counts fully (1.0). A vest during push-ups is partly held by your feet,
 * so only part of it counts.
 *
 * Sources:
 *  - Ebben WP et al. (2011) "Kinetic analysis of several variations of push-ups",
 *    J Strength Cond Res 25(10):2891–2894. Push-up 64%, knees 49%, hands raised
 *    30/61 cm 55%/41%, feet raised 30/61 cm 70%/74% of bodyweight.
 *  - de Leva P (1996) "Adjustments to Zatsiorsky–Seluyanov's segment inertia
 *    parameters", J Biomech 29(9):1223–1230. Body-segment masses: hand 0.61%,
 *    forearm 1.62%, foot 1.37%, shank 4.33%, thigh 14.16% of bodyweight.
 */
enum class BodyweightProfile(
    val label: String,
    val fraction: Double,
    val evidence: Evidence,
    val note: String,
    val unilateral: Boolean = false,
    val addedFactor: Double = 1.0,
    val elevation: Elevation = Elevation.NONE,
    val defaultElevationCm: Double = 0.0,
) {
    // ---- Push ---------------------------------------------------------------------------
    PUSH_UP(
        "Push-up", 0.64, Evidence.MEASURED,
        "Your hands carry about 64% of your bodyweight (Ebben 2011). Hand width changes " +
            "which muscles work hardest, not the load.",
        addedFactor = 0.7,
    ),
    KNEE_PUSH_UP("Knee push-up", 0.49, Evidence.MEASURED, "About 49% of bodyweight (Ebben 2011).", addedFactor = 0.6),
    INCLINE_PUSH_UP(
        "Incline push-up (hands raised)", 0.64, Evidence.MEASURED,
        "Raising your hands takes load off: 55% at 30 cm and 41% at 61 cm for an average " +
            "person (Ebben 2011). Scaled to your height.",
        addedFactor = 0.6, elevation = Elevation.HANDS, defaultElevationCm = 45.0,
    ),
    DECLINE_PUSH_UP(
        "Decline push-up (feet raised)", 0.64, Evidence.MEASURED,
        "Raising your feet adds load: 70% at 30 cm and 74% at 61 cm (Ebben 2011). " +
            "Scaled to your height.",
        addedFactor = 0.75, elevation = Elevation.FEET, defaultElevationCm = 45.0,
    ),
    WALL_PUSH_UP("Wall push-up", 0.20, Evidence.ESTIMATE, "Standing almost upright, very little load reaches your hands.", addedFactor = 0.2),
    PIKE_PUSH_UP("Pike push-up", 0.70, Evidence.ESTIMATE, "Hips high shifts more weight over your hands than a push-up.", addedFactor = 0.7),
    ELEVATED_PIKE_PUSH_UP("Elevated pike push-up", 0.82, Evidence.ESTIMATE, "Feet on a box: close to a handstand push-up.", addedFactor = 0.8),
    PSEUDO_PLANCHE_PUSH_UP("Pseudo planche push-up", 0.75, Evidence.ESTIMATE, "Leaning forward over your hands loads them more than a push-up.", addedFactor = 0.75),
    ARCHER_PUSH_UP(
        "Archer push-up", 0.45, Evidence.ESTIMATE,
        "Roughly 70% of a push-up's load goes through the working arm; the straight arm assists.",
        unilateral = true, addedFactor = 0.5,
    ),
    ONE_ARM_PUSH_UP(
        "One-arm push-up", 0.64, Evidence.ESTIMATE,
        "A full push-up's load on a single arm.",
        unilateral = true, addedFactor = 0.7,
    ),
    HANDSTAND_PUSH_UP(
        "Handstand push-up", 0.95, Evidence.PHYSICS,
        "You press everything except your hands and forearms: about 95% of bodyweight.",
    ),
    DIP(
        "Dip", 0.95, Evidence.PHYSICS,
        "Your arms lift everything except your hands and forearms: about 95% of bodyweight.",
    ),
    BENCH_DIP("Bench dip", 0.50, Evidence.ESTIMATE, "With feet on the floor, your legs take about half the load.", addedFactor = 0.9),

    // ---- Pull ---------------------------------------------------------------------------
    PULL_UP(
        "Pull-up / chin-up", 0.95, Evidence.PHYSICS,
        "You lift everything except your hands and forearms: about 95% of bodyweight.",
    ),
    ARCHER_PULL_UP("Archer pull-up", 0.70, Evidence.ESTIMATE, "Most of the load on the working arm.", unilateral = true),
    ONE_ARM_PULL_UP("One-arm pull-up", 0.95, Evidence.PHYSICS, "Your whole bodyweight on one arm.", unilateral = true),
    MUSCLE_UP("Muscle-up", 0.95, Evidence.PHYSICS, "Pull and press your body over the bar: about 95% of bodyweight."),
    INVERTED_ROW(
        "Inverted / table row", 0.60, Evidence.ESTIMATE,
        "Hanging under a bar or sturdy table, heels on the floor: about 60% of bodyweight. " +
            "Lower bar = harder.",
        addedFactor = 0.7,
    ),
    FEET_ELEVATED_ROW("Feet-elevated row", 0.75, Evidence.ESTIMATE, "Body horizontal with feet raised.", addedFactor = 0.8),
    DOORWAY_ROW(
        "Doorway / towel row", 0.40, Evidence.ESTIMATE,
        "Leaning back from a door frame or towel: the further you lean, the harder it gets.",
        addedFactor = 0.5,
    ),

    // ---- Legs ---------------------------------------------------------------------------
    SQUAT(
        "Squat", 0.886, Evidence.PHYSICS,
        "Your legs lift everything above your shins: about 89% of bodyweight (de Leva 1996).",
    ),
    SPLIT_SQUAT(
        "Split squat / lunge", 0.60, Evidence.ESTIMATE,
        "The front leg carries roughly two-thirds of the moving load.",
        unilateral = true,
    ),
    BULGARIAN_SPLIT_SQUAT(
        "Bulgarian split squat", 0.75, Evidence.ESTIMATE,
        "Rear foot raised: the front leg carries about 85% of the moving load.",
        unilateral = true,
    ),
    STEP_UP("Step-up", 0.94, Evidence.PHYSICS, "One leg lifts everything but itself below the knee.", unilateral = true),
    PISTOL_SQUAT(
        "Pistol / single-leg squat", 0.94, Evidence.PHYSICS,
        "One leg lifts everything except its own shin and foot: about 94% of bodyweight.",
        unilateral = true,
    ),
    GLUTE_BRIDGE("Glute bridge", 0.50, Evidence.ESTIMATE, "Your hips lift roughly half of your bodyweight."),
    SINGLE_LEG_GLUTE_BRIDGE("Single-leg glute bridge", 0.50, Evidence.ESTIMATE, "The same lift, on one leg.", unilateral = true),
    CALF_RAISE("Calf raise", 0.97, Evidence.PHYSICS, "Everything except your feet: about 97% of bodyweight."),
    SINGLE_LEG_CALF_RAISE("Single-leg calf raise", 0.97, Evidence.PHYSICS, "The same, on one leg.", unilateral = true),

    // ---- Core ---------------------------------------------------------------------------
    HANGING_LEG_RAISE(
        "Hanging leg raise", 0.40, Evidence.PHYSICS,
        "Your legs are about 40% of your bodyweight (de Leva 1996).",
    ),
    SIT_UP("Sit-up", 0.35, Evidence.ESTIMATE, "Lifts your upper body around the hips.", addedFactor = 1.0),
    CRUNCH("Crunch", 0.25, Evidence.ESTIMATE, "A shorter lift of the upper body.", addedFactor = 1.0);

    companion object {
        fun fromKey(key: String?): BodyweightProfile? = key?.let { k -> entries.firstOrNull { it.name == k } }
    }
}

package app.forge.domain.bodyweight

import kotlin.math.max
import kotlin.math.min

/** The result of a load calculation, ready to show: "≈ 44 kg (64% of 68 kg)". */
data class LoadEstimate(
    val loadKg: Double,
    /** Share of bodyweight on the working limb(s) after elevation scaling. */
    val fraction: Double,
    val bodyweightKg: Double,
    val addedKg: Double,
    val profile: BodyweightProfile,
)

/**
 * Turns bodyweight + a movement profile into "how much you're actually lifting".
 *
 * load = bodyweight × fraction(profile, elevation, height) + added weight × addedFactor
 */
object BodyweightLoad {
    /** Average height of the Ebben (2011) subjects' population; used if yours is unknown. */
    const val REFERENCE_HEIGHT_CM = 175.0

    // Ebben 2011 data points as (elevation ÷ height, fraction of bodyweight).
    private val handsRaised = listOf(0.0 to 0.64, 30.48 / REFERENCE_HEIGHT_CM to 0.55, 60.96 / REFERENCE_HEIGHT_CM to 0.41)
    private val feetRaised = listOf(0.0 to 0.64, 30.48 / REFERENCE_HEIGHT_CM to 0.70, 60.96 / REFERENCE_HEIGHT_CM to 0.74)

    private const val MIN_HANDS_RAISED = 0.15
    private const val MAX_FEET_RAISED = 0.82

    /**
     * Share of bodyweight for [profile]. For elevation-dependent push-ups, the height
     * of the bench is compared with your height, because a 45 cm bench tilts a short
     * person more than a tall one.
     */
    fun fraction(profile: BodyweightProfile, elevationCm: Double? = null, heightCm: Double? = null): Double {
        val elevation = elevationCm ?: profile.defaultElevationCm
        val height = heightCm?.takeIf { it in 100.0..250.0 } ?: REFERENCE_HEIGHT_CM
        val ratio = (elevation / height).coerceAtLeast(0.0)
        return when (profile.elevation) {
            Elevation.NONE -> profile.fraction
            Elevation.HANDS -> max(MIN_HANDS_RAISED, interpolate(handsRaised, ratio))
            Elevation.FEET -> min(MAX_FEET_RAISED, interpolate(feetRaised, ratio))
        }
    }

    fun estimate(
        bodyweightKg: Double,
        profile: BodyweightProfile,
        addedKg: Double? = null,
        elevationCm: Double? = null,
        heightCm: Double? = null,
    ): LoadEstimate {
        val f = fraction(profile, elevationCm, heightCm)
        val added = (addedKg ?: 0.0).coerceAtLeast(0.0)
        return LoadEstimate(
            loadKg = bodyweightKg * f + added * profile.addedFactor,
            fraction = f,
            bodyweightKg = bodyweightKg,
            addedKg = added,
            profile = profile,
        )
    }

    /** Straight-line interpolation through [points], continuing the end slopes beyond them. */
    internal fun interpolate(points: List<Pair<Double, Double>>, x: Double): Double {
        val segment = points.zipWithNext().firstOrNull { (_, b) -> x <= b.first } ?: points.zipWithNext().last()
        val (a, b) = segment
        val t = (x - a.first) / (b.first - a.first)
        return a.second + t * (b.second - a.second)
    }
}

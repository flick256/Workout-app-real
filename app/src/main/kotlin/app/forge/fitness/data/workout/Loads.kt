package app.forge.fitness.data.workout

import app.forge.domain.bodyweight.BodyweightLoad
import app.forge.domain.bodyweight.BodyweightProfile
import app.forge.domain.bodyweight.LoadEstimate
import app.forge.domain.model.LogType
import app.forge.fitness.data.db.ExerciseEntity
import app.forge.fitness.data.db.SetEntryEntity

/** Works out [SetEntryEntity.loadKg]: what you actually moved per rep. */
object Loads {

    fun profileOf(exercise: ExerciseEntity): BodyweightProfile? = BodyweightProfile.fromKey(exercise.bodyweightProfile)

    /** The bodyweight share for this exercise with no added weight, or null if unknown. */
    fun baseEstimate(exercise: ExerciseEntity, bodyweightKg: Double?, heightCm: Double?): LoadEstimate? {
        val profile = profileOf(exercise) ?: return null
        val bw = bodyweightKg ?: return null
        return BodyweightLoad.estimate(bw, profile, null, exercise.bodyweightElevationCm, heightCm)
    }

    /**
     * - Bodyweight moves: bodyweight share + added weight (needs your bodyweight).
     * - Weighted lifts: the weight on the bar/bag/dumbbell.
     * - Timed or cardio sets: no load.
     */
    fun loadFor(set: SetEntryEntity, exercise: ExerciseEntity, bodyweightKg: Double?, heightCm: Double?): Double? {
        val profile = profileOf(exercise)
        return when {
            profile != null && bodyweightKg != null -> BodyweightLoad.estimate(
                bodyweightKg, profile, set.weightKg, exercise.bodyweightElevationCm, heightCm,
            ).loadKg
            profile != null -> set.weightKg
            exercise.logType == LogType.WEIGHT_REPS -> set.weightKg
            exercise.logType == LogType.REPS -> set.weightKg?.takeIf { it > 0 }
            else -> null
        }
    }
}

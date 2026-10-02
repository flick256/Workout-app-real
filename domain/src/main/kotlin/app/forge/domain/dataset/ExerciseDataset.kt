package app.forge.domain.dataset

import app.forge.domain.bodyweight.BodyweightMatcher
import app.forge.domain.bodyweight.BodyweightProfile
import app.forge.domain.model.Equipment
import app.forge.domain.model.ExerciseCategory
import app.forge.domain.model.LogType
import app.forge.domain.model.Muscle
import java.util.UUID
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** One exercise as it appears in free-exercise-db's `exercises.json`. */
@Serializable
data class RawExercise(
    val id: String,
    val name: String,
    val force: String? = null,
    val level: String? = null,
    val mechanic: String? = null,
    val equipment: String? = null,
    val primaryMuscles: List<String> = emptyList(),
    val secondaryMuscles: List<String> = emptyList(),
    val instructions: List<String> = emptyList(),
    val category: String,
    val images: List<String> = emptyList(),
)

/** A bundled exercise, mapped into the app's own types and ready to insert. */
data class ExerciseSeed(
    val id: String,
    val sourceId: String,
    val name: String,
    val primaryMuscles: List<Muscle>,
    val secondaryMuscles: List<Muscle>,
    val equipment: Equipment?,
    val category: ExerciseCategory,
    val logType: LogType,
    /** Set for bodyweight moves, so the app can work out the load from your bodyweight. */
    val bodyweightProfile: BodyweightProfile?,
    /** Bench/box height for incline and decline moves; null = the profile's default. */
    val bodyweightElevationCm: Double? = null,
    val mechanic: String?,
    val force: String?,
    val level: String?,
    val instructions: List<String>,
    val images: List<String>,
)

object ExerciseDataset {
    /**
     * Bump this whenever the bundled JSON changes; the app then re-imports bundled
     * exercises on next launch. Your own custom exercises are never touched.
     */
    const val VERSION = 2

    private val json = Json { ignoreUnknownKeys = true }

    fun parse(text: String): List<ExerciseSeed> =
        json.decodeFromString<List<RawExercise>>(text).map(::toSeed)

    /**
     * IDs are derived from the dataset ID, so the same exercise has the same UUID on
     * every phone and in every backup.
     */
    fun stableId(sourceId: String): String =
        UUID.nameUUIDFromBytes("free-exercise-db:$sourceId".toByteArray()).toString()

    fun toSeed(raw: RawExercise): ExerciseSeed {
        val category = category(raw.category)
        val equipment = equipment(raw.equipment)
        val isStatic = raw.force == "static"
        val profile = BodyweightMatcher.match(raw.name, equipment, category, isStatic)
        return ExerciseSeed(
            id = stableId(raw.id),
            sourceId = raw.id,
            name = raw.name.trim(),
            primaryMuscles = raw.primaryMuscles.mapNotNull(::muscle),
            secondaryMuscles = raw.secondaryMuscles.mapNotNull(::muscle),
            equipment = equipment,
            category = category,
            // Bodyweight moves are logged as reps (+ optional added weight).
            logType = if (profile != null) LogType.REPS else LogType.infer(category, equipment, isStatic),
            bodyweightProfile = profile,
            mechanic = raw.mechanic,
            force = raw.force,
            level = raw.level,
            instructions = raw.instructions,
            images = raw.images,
        )
    }

    fun muscle(name: String): Muscle? = when (name.lowercase().trim()) {
        "abdominals" -> Muscle.ABDOMINALS
        "abductors" -> Muscle.ABDUCTORS
        "adductors" -> Muscle.ADDUCTORS
        "biceps" -> Muscle.BICEPS
        "calves" -> Muscle.CALVES
        "chest" -> Muscle.CHEST
        "forearms" -> Muscle.FOREARMS
        "glutes" -> Muscle.GLUTES
        "hamstrings" -> Muscle.HAMSTRINGS
        "lats" -> Muscle.LATS
        "lower back" -> Muscle.LOWER_BACK
        "middle back" -> Muscle.MIDDLE_BACK
        "neck" -> Muscle.NECK
        "quadriceps" -> Muscle.QUADRICEPS
        "shoulders" -> Muscle.SHOULDERS
        "traps" -> Muscle.TRAPS
        "triceps" -> Muscle.TRICEPS
        else -> null
    }

    fun equipment(name: String?): Equipment? = when (name?.lowercase()?.trim()) {
        null -> null
        "body only" -> Equipment.BODY_ONLY
        "dumbbell" -> Equipment.DUMBBELL
        "barbell" -> Equipment.BARBELL
        "kettlebells" -> Equipment.KETTLEBELL
        "bands" -> Equipment.BANDS
        "cable" -> Equipment.CABLE
        "machine" -> Equipment.MACHINE
        "e-z curl bar" -> Equipment.EZ_BAR
        "medicine ball" -> Equipment.MEDICINE_BALL
        "exercise ball" -> Equipment.EXERCISE_BALL
        "foam roll" -> Equipment.FOAM_ROLL
        else -> Equipment.OTHER
    }

    fun category(name: String): ExerciseCategory = when (name.lowercase().trim()) {
        "stretching" -> ExerciseCategory.STRETCHING
        "plyometrics" -> ExerciseCategory.PLYOMETRICS
        "cardio" -> ExerciseCategory.CARDIO
        "powerlifting" -> ExerciseCategory.POWERLIFTING
        "olympic weightlifting" -> ExerciseCategory.OLYMPIC_WEIGHTLIFTING
        "strongman" -> ExerciseCategory.STRONGMAN
        else -> ExerciseCategory.STRENGTH
    }
}

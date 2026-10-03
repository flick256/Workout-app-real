package app.forge.domain.activity

import app.forge.domain.model.Muscle
import app.forge.domain.model.Muscle.ABDOMINALS
import app.forge.domain.model.Muscle.ADDUCTORS
import app.forge.domain.model.Muscle.BICEPS
import app.forge.domain.model.Muscle.CALVES
import app.forge.domain.model.Muscle.CHEST
import app.forge.domain.model.Muscle.FOREARMS
import app.forge.domain.model.Muscle.GLUTES
import app.forge.domain.model.Muscle.HAMSTRINGS
import app.forge.domain.model.Muscle.LATS
import app.forge.domain.model.Muscle.LOWER_BACK
import app.forge.domain.model.Muscle.MIDDLE_BACK
import app.forge.domain.model.Muscle.QUADRICEPS
import app.forge.domain.model.Muscle.SHOULDERS
import app.forge.domain.model.Muscle.TRICEPS
import app.forge.domain.suggest.MuscleWork
import kotlin.math.min

enum class ActivityKind(val label: String) { SPORT("Sport"), CARDIO("Cardio"), MOBILITY("Mobility") }

/**
 * Things you do outside the gym. [primary]/[secondary] muscles are the ones the activity
 * tires out, so recovery and "what should I train" account for a football game the
 * same way they account for leg day.
 */
enum class Sport(
    val label: String,
    val kind: ActivityKind,
    val primary: List<Muscle>,
    val secondary: List<Muscle> = emptyList(),
    /** Typical effort on a 1–10 scale, used as the starting value when logging. */
    val defaultIntensity: Int = 6,
    val hasDistance: Boolean = false,
) {
    FOOTBALL("Football (soccer)", ActivityKind.SPORT, listOf(QUADRICEPS, HAMSTRINGS, GLUTES, CALVES), listOf(ADDUCTORS, ABDOMINALS), 7),
    AUSTRALIAN_FOOTBALL("Australian football", ActivityKind.SPORT, listOf(QUADRICEPS, HAMSTRINGS, GLUTES, CALVES), listOf(SHOULDERS, ABDOMINALS), 8),
    BASKETBALL("Basketball", ActivityKind.SPORT, listOf(QUADRICEPS, CALVES, GLUTES), listOf(HAMSTRINGS, SHOULDERS), 7),
    NETBALL("Netball", ActivityKind.SPORT, listOf(QUADRICEPS, CALVES, GLUTES), listOf(HAMSTRINGS, SHOULDERS), 7),
    RUGBY("Rugby", ActivityKind.SPORT, listOf(QUADRICEPS, HAMSTRINGS, GLUTES, SHOULDERS), listOf(CHEST, LOWER_BACK, ABDOMINALS), 8),
    CRICKET("Cricket", ActivityKind.SPORT, listOf(SHOULDERS), listOf(QUADRICEPS, ABDOMINALS), 4),
    TENNIS("Tennis", ActivityKind.SPORT, listOf(SHOULDERS, CALVES, QUADRICEPS), listOf(FOREARMS, ABDOMINALS), 6),
    BADMINTON("Badminton", ActivityKind.SPORT, listOf(CALVES, QUADRICEPS, SHOULDERS), listOf(FOREARMS), 6),
    VOLLEYBALL("Volleyball", ActivityKind.SPORT, listOf(QUADRICEPS, CALVES, SHOULDERS), listOf(GLUTES), 6),
    TABLE_TENNIS("Table tennis", ActivityKind.SPORT, emptyList(), listOf(SHOULDERS, FOREARMS), 4),
    MARTIAL_ARTS("Martial arts", ActivityKind.SPORT, listOf(QUADRICEPS, SHOULDERS, ABDOMINALS), listOf(GLUTES, HAMSTRINGS), 7),
    BOXING("Boxing", ActivityKind.SPORT, listOf(SHOULDERS, ABDOMINALS), listOf(TRICEPS, CHEST, CALVES), 8),
    CLIMBING("Climbing / bouldering", ActivityKind.SPORT, listOf(LATS, FOREARMS, BICEPS), listOf(MIDDLE_BACK, SHOULDERS, ABDOMINALS), 7),
    SKATEBOARDING("Skateboarding", ActivityKind.SPORT, listOf(QUADRICEPS, CALVES), listOf(GLUTES), 5),
    DANCE("Dance", ActivityKind.SPORT, listOf(CALVES, QUADRICEPS), listOf(GLUTES, ABDOMINALS), 5),
    SURFING("Surfing", ActivityKind.SPORT, listOf(SHOULDERS, LATS), listOf(MIDDLE_BACK, TRICEPS), 6),
    GOLF("Golf", ActivityKind.SPORT, emptyList(), listOf(ABDOMINALS, SHOULDERS), 3),
    RUNNING("Running", ActivityKind.CARDIO, listOf(QUADRICEPS, CALVES, HAMSTRINGS), listOf(GLUTES), 6, hasDistance = true),
    CYCLING("Cycling", ActivityKind.CARDIO, listOf(QUADRICEPS), listOf(GLUTES, CALVES, HAMSTRINGS), 5, hasDistance = true),
    SWIMMING("Swimming", ActivityKind.CARDIO, listOf(LATS, SHOULDERS), listOf(CHEST, TRICEPS, MIDDLE_BACK), 6, hasDistance = true),
    WALKING("Walking", ActivityKind.CARDIO, emptyList(), listOf(CALVES), 3, hasDistance = true),
    HIKING("Hiking", ActivityKind.CARDIO, listOf(QUADRICEPS, GLUTES, CALVES), emptyList(), 5, hasDistance = true),
    ROWING("Rowing", ActivityKind.CARDIO, listOf(MIDDLE_BACK, LATS, QUADRICEPS), listOf(BICEPS, GLUTES, HAMSTRINGS), 6, hasDistance = true),
    HIIT("HIIT / circuits", ActivityKind.CARDIO, listOf(QUADRICEPS, GLUTES), listOf(SHOULDERS, CHEST, ABDOMINALS, CALVES), 8),
    STRETCHING("Stretching", ActivityKind.MOBILITY, emptyList(), emptyList(), 2),
    YOGA("Yoga", ActivityKind.MOBILITY, emptyList(), listOf(ABDOMINALS, SHOULDERS), 3),
    PILATES("Pilates", ActivityKind.MOBILITY, listOf(ABDOMINALS), listOf(GLUTES), 4),
    OTHER("Other activity", ActivityKind.SPORT, emptyList(), emptyList(), 5);

    companion object {
        fun fromKey(key: String?): Sport = entries.firstOrNull { it.name == key } ?: OTHER
    }
}

/** Turns an activity into "hard sets" for the recovery model. */
object ActivityFatigue {
    const val MAX_SETS = 8.0

    /**
     * Every 10 minutes at full effort counts like one hard set for the muscles the activity
     * works; easier efforts count proportionally less. A 60-minute football game at 7/10
     * ≈ 4 sets of leg work. Capped at [MAX_SETS] so a long hike doesn't wipe your legs out
     * for a week.
     */
    fun equivalentSets(minutes: Int, intensity: Int): Double =
        min(MAX_SETS, minutes.coerceAtLeast(0) / 10.0 * intensity.coerceIn(1, 10) / 10.0)

    /** Null for activities that don't meaningfully tire any muscle (stretching, walking). */
    fun muscleWork(sport: Sport, startMillis: Long, minutes: Int, intensity: Int): MuscleWork? {
        if (sport.primary.isEmpty() && sport.secondary.isEmpty()) return null
        if (sport.kind == ActivityKind.MOBILITY && intensity < 4) return null
        val sets = equivalentSets(minutes, intensity)
        if (sets <= 0) return null
        // Fatigue lands at the end of the activity.
        return MuscleWork(startMillis + minutes * 60_000L, sport.primary, sport.secondary, sets)
    }
}

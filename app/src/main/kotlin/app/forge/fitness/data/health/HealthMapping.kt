package app.forge.fitness.data.health

import androidx.health.connect.client.records.ExerciseSessionRecord as E
import app.forge.domain.activity.Sport
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Plain-Kotlin shapes for Health Connect data, so the maths is testable without a phone. */
data class SleepSpan(val start: Instant, val end: Instant, val stages: List<StageSpan> = emptyList())

data class StageSpan(val start: Instant, val end: Instant, val awake: Boolean)

data class TimedValue(val time: Instant, val value: Double)

object HealthMapping {

    /** Strength sessions are matched to Forge workouts rather than imported as activities. */
    private val STRENGTH = setOf(E.EXERCISE_TYPE_STRENGTH_TRAINING, E.EXERCISE_TYPE_WEIGHTLIFTING, E.EXERCISE_TYPE_CALISTHENICS)

    fun isStrength(exerciseType: Int): Boolean = exerciseType in STRENGTH

    /** The closest Forge sport for a Health Connect exercise type (null = strength training). */
    fun sportFor(exerciseType: Int): Sport? = when (exerciseType) {
        in STRENGTH -> null
        E.EXERCISE_TYPE_SOCCER -> Sport.FOOTBALL
        E.EXERCISE_TYPE_FOOTBALL_AUSTRALIAN -> Sport.AUSTRALIAN_FOOTBALL
        E.EXERCISE_TYPE_FOOTBALL_AMERICAN, E.EXERCISE_TYPE_RUGBY -> Sport.RUGBY
        E.EXERCISE_TYPE_BASKETBALL -> Sport.BASKETBALL
        E.EXERCISE_TYPE_CRICKET -> Sport.CRICKET
        E.EXERCISE_TYPE_TENNIS, E.EXERCISE_TYPE_SQUASH -> Sport.TENNIS
        E.EXERCISE_TYPE_BADMINTON -> Sport.BADMINTON
        E.EXERCISE_TYPE_VOLLEYBALL -> Sport.VOLLEYBALL
        E.EXERCISE_TYPE_TABLE_TENNIS -> Sport.TABLE_TENNIS
        E.EXERCISE_TYPE_MARTIAL_ARTS -> Sport.MARTIAL_ARTS
        E.EXERCISE_TYPE_BOXING -> Sport.BOXING
        E.EXERCISE_TYPE_ROCK_CLIMBING -> Sport.CLIMBING
        E.EXERCISE_TYPE_SKATING -> Sport.SKATEBOARDING
        E.EXERCISE_TYPE_DANCING -> Sport.DANCE
        E.EXERCISE_TYPE_SURFING -> Sport.SURFING
        E.EXERCISE_TYPE_GOLF -> Sport.GOLF
        E.EXERCISE_TYPE_RUNNING, E.EXERCISE_TYPE_RUNNING_TREADMILL -> Sport.RUNNING
        E.EXERCISE_TYPE_BIKING, E.EXERCISE_TYPE_BIKING_STATIONARY -> Sport.CYCLING
        E.EXERCISE_TYPE_SWIMMING_POOL, E.EXERCISE_TYPE_SWIMMING_OPEN_WATER -> Sport.SWIMMING
        E.EXERCISE_TYPE_WALKING -> Sport.WALKING
        E.EXERCISE_TYPE_HIKING, E.EXERCISE_TYPE_STAIR_CLIMBING -> Sport.HIKING
        E.EXERCISE_TYPE_ROWING, E.EXERCISE_TYPE_ROWING_MACHINE -> Sport.ROWING
        E.EXERCISE_TYPE_HIGH_INTENSITY_INTERVAL_TRAINING, E.EXERCISE_TYPE_BOOT_CAMP -> Sport.HIIT
        E.EXERCISE_TYPE_ELLIPTICAL -> Sport.CYCLING
        E.EXERCISE_TYPE_STRETCHING -> Sport.STRETCHING
        E.EXERCISE_TYPE_YOGA -> Sport.YOGA
        E.EXERCISE_TYPE_PILATES -> Sport.PILATES
        else -> Sport.OTHER
    }

    /**
     * Which morning a night-time reading belongs to: anything from 6 pm onwards counts
     * towards the next day, so last night's sleep and HRV show up on today.
     */
    fun morningOf(time: Instant, zone: ZoneId): LocalDate {
        val local = time.atZone(zone)
        return if (local.hour >= EVENING_HOUR) local.toLocalDate().plusDays(1) else local.toLocalDate()
    }

    /** Minutes actually asleep per morning (awake stages are left out). */
    fun sleepByDay(sessions: List<SleepSpan>, zone: ZoneId): Map<LocalDate, Int> =
        sessions.groupBy { morningOf(it.end, zone) }.mapValues { (_, nights) -> nights.sumOf(::asleepMinutes) }

    fun asleepMinutes(session: SleepSpan): Int {
        val total = minutes(session.start, session.end)
        if (session.stages.isEmpty()) return total
        val awake = session.stages.filter { it.awake }.sumOf { minutes(it.start, it.end) }
        return (total - awake).coerceAtLeast(0)
    }

    /** Average of the night's readings for each morning (e.g. HRV). */
    fun averageByMorning(values: List<TimedValue>, zone: ZoneId): Map<LocalDate, Double> =
        values.groupBy { morningOf(it.time, zone) }.mapValues { (_, v) -> v.map { it.value }.average() }

    /** Average per calendar day (e.g. resting heart rate). */
    fun averageByDay(values: List<TimedValue>, zone: ZoneId): Map<LocalDate, Double> =
        values.groupBy { it.time.atZone(zone).toLocalDate() }.mapValues { (_, v) -> v.map { it.value }.average() }

    private fun minutes(start: Instant, end: Instant) = ((end.toEpochMilli() - start.toEpochMilli()) / 60_000L).toInt()

    private const val EVENING_HOUR = 18
}

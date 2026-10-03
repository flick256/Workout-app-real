package app.forge.fitness.data.health

import androidx.health.connect.client.records.ExerciseSessionRecord
import app.forge.domain.activity.Sport
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HealthMappingTest {
    private val zone = ZoneId.of("Australia/Melbourne")
    private fun at(day: Int, hour: Int, minute: Int = 0) = LocalDateTime.of(2026, 10, day, hour, minute).atZone(zone).toInstant()

    @Test
    fun exerciseTypesMapToSports() {
        assertEquals(Sport.FOOTBALL, HealthMapping.sportFor(ExerciseSessionRecord.EXERCISE_TYPE_SOCCER))
        assertEquals(Sport.RUNNING, HealthMapping.sportFor(ExerciseSessionRecord.EXERCISE_TYPE_RUNNING_TREADMILL))
        assertEquals(Sport.OTHER, HealthMapping.sportFor(ExerciseSessionRecord.EXERCISE_TYPE_OTHER_WORKOUT))
        assertNull("strength is matched to workouts", HealthMapping.sportFor(ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING))
    }

    @Test
    fun lastNightsSleepCountsForThisMorningWithoutAwakeTime() {
        val night = SleepSpan(
            at(2, 22, 30),
            at(3, 7, 0),
            listOf(StageSpan(at(3, 3, 0), at(3, 3, 30), awake = true), StageSpan(at(2, 22, 30), at(3, 3, 0), awake = false)),
        )
        val nap = SleepSpan(at(3, 14, 0), at(3, 14, 40))
        // The phone also logged (most of) the same night: it must not count twice.
        val phoneCopy = SleepSpan(at(2, 23, 0), at(3, 6, 30))
        // A nap that runs past 6 pm still counts for the day it started.
        val lateNap = SleepSpan(at(3, 17, 30), at(3, 18, 10))
        val byDay = HealthMapping.sleepByDay(listOf(night, nap, phoneCopy, lateNap), zone)
        assertEquals(mapOf(LocalDate.of(2026, 10, 3) to 8 * 60 + 30 - 30 + 40 + 40), byDay)
    }

    @Test
    fun eveningReadingsBelongToTheNextMorning() {
        val values = listOf(TimedValue(at(2, 23), 60.0), TimedValue(at(3, 4), 70.0), TimedValue(at(3, 12), 50.0))
        assertEquals(mapOf(LocalDate.of(2026, 10, 3) to 60.0), HealthMapping.averageByMorning(values, zone))
        assertEquals(
            mapOf(LocalDate.of(2026, 10, 2) to 60.0, LocalDate.of(2026, 10, 3) to 60.0),
            HealthMapping.averageByDay(values, zone),
        )
    }
}

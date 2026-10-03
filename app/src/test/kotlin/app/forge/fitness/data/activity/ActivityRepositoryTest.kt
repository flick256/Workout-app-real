package app.forge.fitness.data.activity

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.forge.domain.activity.ReadinessLevel
import app.forge.domain.activity.Sport
import app.forge.fitness.data.FakeTime
import app.forge.fitness.data.TestDb
import app.forge.fitness.data.db.ActivitySource
import app.forge.fitness.data.db.DailyHealthEntity
import app.forge.fitness.data.db.ForgeDatabase
import app.forge.fitness.data.workout.WorkoutRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = android.app.Application::class)
class ActivityRepositoryTest {
    private lateinit var db: ForgeDatabase
    private val time = FakeTime()
    private lateinit var repo: ActivityRepository
    private val minute = 60_000L

    @Before
    fun setUp() {
        db = TestDb.inMemory()
        repo = ActivityRepository(db.activityDao(), db.workoutDao(), time)
    }

    @After
    fun tearDown() = db.close()

    private fun football(id: String = "hc-1", start: Long = time.millis - 120 * minute) =
        ImportedSession(id, Sport.FOOTBALL, null, start, start + 60 * minute, avgHeartRate = 150, maxHeartRate = 182)

    @Test
    fun syncingTwiceDoesNotDuplicate() = runTest {
        assertEquals(ImportResult(added = 1), repo.importSessions(listOf(football())))
        assertEquals(ImportResult(), repo.importSessions(listOf(football())))
        val all = repo.observeActivities().first()
        assertEquals(1, all.size)
        assertEquals(ActivitySource.HEALTH_CONNECT.name, all.single().source)
        assertEquals("effort comes from heart rate", 6, all.single().intensity)
        assertEquals(60, all.single().durationMinutes)
    }

    @Test
    fun deletedImportsStayDeleted() = runTest {
        repo.importSessions(listOf(football()))
        val id = repo.observeActivities().first().single().id
        repo.delete(id)
        repo.importSessions(listOf(football()))
        assertEquals(0, repo.observeActivities().first().size)
    }

    @Test
    fun strapSessionMergesIntoOneLoggedByHand() = runTest {
        val start = time.millis - 120 * minute
        repo.save(null, Sport.FOOTBALL, "Training", start + 5 * minute, 60, 8, null, "felt sharp")
        assertEquals(ImportResult(updated = 1), repo.importSessions(listOf(football(start = start))))
        val merged = repo.observeActivities().first().single()
        assertEquals("your details are kept", "Training", merged.title)
        assertEquals(8, merged.intensity)
        assertEquals(150, merged.avgHeartRate)
        assertEquals("hc-1", merged.externalId)
    }

    @Test
    fun aDifferentSportAtTheSameTimeIsNotMerged() = runTest {
        val start = time.millis - 120 * minute
        repo.save(null, Sport.BASKETBALL, null, start, 90, 7, null, null)
        val walk = ImportedSession("hc-walk", Sport.WALKING, null, start + 5 * minute, start + 50 * minute)
        assertEquals(ImportResult(added = 1), repo.importSessions(listOf(walk)))
        assertEquals(2, repo.observeActivities().first().size)
    }

    @Test
    fun theSameSessionFromTwoAppsIsImportedOnce() = runTest {
        val a = football("zepp-1")
        val b = football("samsung-7").copy(start = a.start + 2 * minute, end = a.end + minute, avgHeartRate = null, distanceMeters = 7_100.0)
        repo.importSessions(listOf(a, b))
        val only = repo.observeActivities().first().single()
        assertEquals(150, only.avgHeartRate)
        assertEquals(7_100.0, only.distanceMeters!!, 0.0)
    }

    @Test
    fun strengthSessionAddsHeartRateToTheForgeWorkout() = runTest {
        val workouts = WorkoutRepository(db, db.workoutDao(), db.bodyMetricDao(), time)
        db.exerciseDao().insert(TestDb.exercise("e1"))
        val start = time.millis
        val id = workouts.startOrResume()
        workouts.addExercises(id, listOf("e1"))
        time.advance(45 * minute)
        workouts.finish(id)

        val strap = ImportedSession("hc-9", null, null, start - 2 * minute, start + 44 * minute, avgHeartRate = 121, maxHeartRate = 160)
        assertEquals(ImportResult(workoutsWithHeartRate = 1), repo.importSessions(listOf(strap)))
        assertEquals(121, db.workoutDao().getSession(id)!!.avgHeartRate)
        assertEquals("not imported as a separate activity", 0, repo.observeActivities().first().size)
        assertEquals("unchanged on the next sync", ImportResult(), repo.importSessions(listOf(strap)))
    }

    @Test
    fun strengthWithoutAForgeWorkoutIsKept() = runTest {
        val start = time.millis - 600 * minute
        repo.importSessions(listOf(ImportedSession("hc-2", null, null, start, start + 40 * minute)))
        val a = repo.observeActivities().first().single()
        assertEquals(Sport.OTHER.name, a.sport)
        assertEquals("Strength training", a.title)
        assertEquals(Sport.OTHER.defaultIntensity, a.intensity)
    }

    @Test
    fun tinySessionsAreSkipped() = runTest {
        val start = time.millis - 60 * minute
        repo.importSessions(listOf(ImportedSession("hc-3", Sport.WALKING, null, start, start + 2 * minute)))
        assertEquals(0, repo.observeActivities().first().size)
    }

    @Test
    fun readinessComparesAgainstYourBaseline() {
        val today = 20_000L
        val history = (1..10).map { DailyHealthEntity(today - it, sleepMinutes = 480, restingHr = 55.0, hrvMs = 70.0, updatedAt = 0) }
        val bad = DailyHealthEntity(today, sleepMinutes = 330, restingHr = 61.0, hrvMs = 52.0, updatedAt = 0)
        assertEquals(ReadinessLevel.LOW, ActivityRepository.readiness(history + bad, today).level)

        val good = bad.copy(sleepMinutes = 500, restingHr = 54.0, hrvMs = 78.0)
        assertEquals(ReadinessLevel.GOOD, ActivityRepository.readiness(history + good, today).level)

        val noHistory = ActivityRepository.readiness(listOf(bad), today)
        assertEquals("only sleep counts without a baseline", ReadinessLevel.OK, noHistory.level)
        assertEquals(ReadinessLevel.UNKNOWN, ActivityRepository.readiness(emptyList(), today).level)
    }

    @Test
    fun editingAndDeletingAManualActivity() = runTest {
        val id = repo.save(null, Sport.RUNNING, null, time.millis, 30, 6, 5_000.0, "easy")
        repo.save(id, Sport.RUNNING, "Park run", time.millis, 32, 7, 5_100.0, null)
        val a = repo.get(id)!!
        assertEquals("Park run", a.title)
        assertEquals(32, a.durationMinutes)
        assertNull(a.notes)
        repo.delete(id)
        assertEquals(0, repo.observeActivities().first().size)
        repo.restore(id)
        assertEquals(1, repo.observeActivities().first().size)
    }
}

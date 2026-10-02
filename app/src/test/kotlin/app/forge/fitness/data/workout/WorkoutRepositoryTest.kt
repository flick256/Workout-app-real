package app.forge.fitness.data.workout

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.forge.domain.model.LogType
import app.forge.domain.model.SessionStatus
import app.forge.domain.model.SetType
import app.forge.domain.workout.WarmupSet
import app.forge.fitness.data.FakeTime
import app.forge.fitness.data.TestDb
import app.forge.fitness.data.db.ForgeDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = android.app.Application::class)
class WorkoutRepositoryTest {

    private lateinit var db: ForgeDatabase
    private lateinit var repo: WorkoutRepository
    private val time = FakeTime()

    @Before
    fun setUp() = runTest {
        db = TestDb.inMemory()
        db.exerciseDao().insertAll(listOf(TestDb.exercise("press"), TestDb.exercise("row")))
        repo = WorkoutRepository(db, db.workoutDao(), db.bodyMetricDao(), time)
    }

    @After
    fun tearDown() = db.close()

    private suspend fun setsOf(sessionId: String) = db.workoutDao().getSetsForSession(sessionId)

    private suspend fun exercisesOf(sessionId: String) = db.workoutDao().getSessionExercises(sessionId)

    @Test
    fun startingTwiceResumesTheSameWorkout() = runTest {
        val first = repo.startOrResume()
        val second = repo.startOrResume()
        assertEquals(first, second)
        assertEquals(first, repo.observeActiveSession().first()?.id)
    }

    @Test
    fun newExerciseGetsThreeEmptySets() = runTest {
        val id = repo.startOrResume()
        repo.addExercises(id, listOf("press"))
        val sets = setsOf(id)
        assertEquals(3, sets.size)
        assertTrue(sets.all { it.completedAt == null && it.weightKg == null })
    }

    @Test
    fun finishRemovesUnfinishedSetsAndEmptyExercises() = runTest {
        val id = repo.startOrResume()
        repo.addExercises(id, listOf("press", "row"))
        val pressId = exercisesOf(id).first { it.exerciseId == "press" }.id
        val pressSets = db.workoutDao().getSets(pressId)
        repo.completeSet(pressSets[0].copy(weightKg = 20.0, reps = 10))
        repo.completeSet(pressSets[1].copy(weightKg = 20.0, reps = 8))

        val summary = repo.finish(id)

        assertEquals(2, summary.completedSets)
        assertEquals(20.0 * 10 + 20.0 * 8, summary.volumeKg, 0.001)
        assertEquals(2, setsOf(id).size)
        assertEquals(listOf("press"), exercisesOf(id).map { it.exerciseId })
        assertEquals(SessionStatus.FINISHED, db.workoutDao().getSession(id)?.status)
        assertNull(repo.observeActiveSession().first())
    }

    @Test
    fun lastTimeComesFromTheMostRecentFinishedWorkout() = runTest {
        // Workout 1: 4 sets at 20 kg.
        val w1 = repo.startOrResume()
        repo.addExercises(w1, listOf("press"))
        val se1 = exercisesOf(w1).single().id
        repo.addSet(se1)
        db.workoutDao().getSets(se1).forEach { repo.completeSet(it.copy(weightKg = 20.0, reps = 10)) }
        repo.finish(w1)
        time.advance(86_400_000)

        // Workout 2 starts with 4 rows, matching last time.
        val w2 = repo.startOrResume()
        repo.addExercises(w2, listOf("press"))
        assertEquals(4, setsOf(w2).size)
        val previous = repo.previousSets("press", w2)
        assertEquals(4, previous.size)
        assertTrue(previous.all { it.weightKg == 20.0 && it.reps == 10 })
    }

    @Test
    fun anUnfinishedWorkoutSurvivesTheAppBeingKilled() = runTest {
        val name = "kill-test.db"
        TestDb.context.deleteDatabase(name)
        var disk = TestDb.onDisk(name)
        disk.exerciseDao().insertAll(listOf(TestDb.exercise("press")))
        var diskRepo = WorkoutRepository(disk, disk.workoutDao(), disk.bodyMetricDao(), time)
        val id = diskRepo.startOrResume()
        diskRepo.addExercises(id, listOf("press"))
        val first = disk.workoutDao().getSetsForSession(id).first()
        diskRepo.completeSet(first.copy(weightKg = 30.0, reps = 5))
        disk.close() // the process dies here

        disk = TestDb.onDisk(name)
        diskRepo = WorkoutRepository(disk, disk.workoutDao(), disk.bodyMetricDao(), time)
        val resumed = diskRepo.observeActiveSession().first()
        assertNotNull(resumed)
        assertEquals(id, resumed!!.id)
        val saved = disk.workoutDao().getSetsForSession(id).single { it.completedAt != null }
        assertEquals(30.0, saved.weightKg!!, 0.0)
        assertEquals(5, saved.reps)
        disk.close()
        TestDb.context.deleteDatabase(name)
    }

    @Test
    fun warmupsGoBeforeWorkingSets() = runTest {
        val id = repo.startOrResume()
        repo.addExercises(id, listOf("press"))
        val se = exercisesOf(id).single().id
        repo.addWarmups(se, listOf(WarmupSet(10.0, 8), WarmupSet(15.0, 4)))
        val types = db.workoutDao().getSets(se).map { it.type }
        assertEquals(
            listOf(SetType.WARMUP, SetType.WARMUP, SetType.WORKING, SetType.WORKING, SetType.WORKING),
            types,
        )
        // Doing it again replaces the empty warm-ups instead of stacking more.
        repo.addWarmups(se, listOf(WarmupSet(12.0, 6)))
        assertEquals(4, db.workoutDao().getSets(se).size)
    }

    @Test
    fun supersetsSurviveOnlyWhileAdjacent() = runTest {
        db.exerciseDao().insertAll(listOf(TestDb.exercise("curl")))
        val id = repo.startOrResume()
        repo.addExercises(id, listOf("press", "row", "curl"))
        val (press, row, _) = exercisesOf(id)
        repo.supersetWithNext(press.id)
        assertEquals(2, exercisesOf(id).count { it.supersetGroup != null })

        // Move "row" to the bottom: press and row are no longer neighbours.
        repo.moveExercise(row.id, +1)
        assertTrue(exercisesOf(id).all { it.supersetGroup == null })
    }

    @Test
    fun deletedSetsAndWorkoutsCanBeRestored() = runTest {
        val id = repo.startOrResume()
        repo.addExercises(id, listOf("press"))
        val set = setsOf(id).first()
        repo.deleteSet(set.id)
        assertEquals(2, setsOf(id).size)
        repo.restoreSet(set.id)
        assertEquals(3, setsOf(id).size)

        repo.completeSet(setsOf(id).first().copy(weightKg = 10.0, reps = 10))
        repo.finish(id)
        assertEquals(1, repo.observeHistory().first().size)
        repo.deleteSession(id)
        assertEquals(0, repo.observeHistory().first().size)
        repo.restoreSession(id)
        assertEquals(1, repo.observeHistory().first().size)
    }

    @Test
    fun bodyweightIsSnapshottedAndLoadsFollowIt() = runTest {
        db.exerciseDao().insertAll(
            listOf(TestDb.exercise("pushup", logType = LogType.REPS).copy(bodyweightProfile = "PUSH_UP")),
        )
        repo.logBodyweight(70.0)
        val id = repo.startOrResume()
        assertEquals(70.0, db.workoutDao().getSession(id)!!.bodyweightKg!!, 0.0)

        repo.addExercises(id, listOf("pushup"))
        val first = setsOf(id).first()
        repo.completeSet(first.copy(reps = 10, loadKg = 70.0 * 0.64))

        // Weighed in heavier mid-workout: completed push-ups now count 64% of 80 kg.
        repo.setSessionBodyweight(id, 80.0, heightCm = null)
        val done = setsOf(id).single { it.completedAt != null }
        assertEquals(80.0 * 0.64, done.loadKg!!, 0.01)
        assertEquals(80.0, db.workoutDao().getSession(id)!!.bodyweightKg!!, 0.0)
        assertEquals(80.0, db.bodyMetricDao().latest(app.forge.domain.model.BodyMetricKind.WEIGHT)!!.value, 0.0)
    }

    @Test
    fun workoutStartedBeforeBodyweightWasEnteredPicksItUp() = runTest {
        db.exerciseDao().insertAll(
            listOf(TestDb.exercise("pullup", logType = LogType.REPS).copy(bodyweightProfile = "PULL_UP")),
        )
        val id = repo.startOrResume() // no bodyweight logged yet
        assertNull(db.workoutDao().getSession(id)!!.bodyweightKg)
        repo.addExercises(id, listOf("pullup"))
        repo.completeSet(setsOf(id).first().copy(reps = 8))

        repo.logBodyweight(70.0) // entered later in Settings
        repo.adoptBodyweightIfMissing(id, 70.0, heightCm = null)

        assertEquals(70.0, db.workoutDao().getSession(id)!!.bodyweightKg!!, 0.0)
        assertEquals(70.0 * 0.95, setsOf(id).single { it.completedAt != null }.loadKg!!, 0.01)

        // A workout that already has a bodyweight keeps it.
        repo.adoptBodyweightIfMissing(id, 90.0, heightCm = null)
        assertEquals(70.0, db.workoutDao().getSession(id)!!.bodyweightKg!!, 0.0)
    }
}

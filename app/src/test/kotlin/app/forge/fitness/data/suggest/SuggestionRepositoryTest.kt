package app.forge.fitness.data.suggest

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.forge.domain.model.Muscle
import app.forge.domain.suggest.Recovery
import app.forge.domain.suggest.SuggestionKind
import app.forge.domain.suggest.TrainToday
import app.forge.fitness.data.FakeTime
import app.forge.fitness.data.TestDb
import app.forge.fitness.data.db.ForgeDatabase
import app.forge.fitness.data.exercise.ExerciseSeeder
import app.forge.fitness.data.prefs.UserPreferences
import app.forge.fitness.data.prefs.UserPreferencesRepository
import app.forge.fitness.data.workout.WorkoutRepository
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = android.app.Application::class)
class SuggestionRepositoryTest {
    private lateinit var db: ForgeDatabase
    private val time = FakeTime(System.currentTimeMillis())

    @After
    fun tearDown() = db.close()

    private suspend fun setUp(scope: TestScope): Triple<WorkoutRepository, SuggestionRepository, UserPreferencesRepository> {
        db = TestDb.inMemory()
        ExerciseSeeder(TestDb.context, db, time).seedIfNeeded()
        val file = File(TestDb.context.filesDir, "suggest-test.preferences_pb").apply { delete() }
        val prefs = UserPreferencesRepository(PreferenceDataStoreFactory.create(scope = TestScope(scope.testScheduler)) { file })
        val workouts = WorkoutRepository(db, db.workoutDao(), db.bodyMetricDao(), time)
        return Triple(workouts, SuggestionRepository(db.workoutDao(), db.exerciseDao(), workouts, prefs, time), prefs)
    }

    @Test
    fun hittingTheTopOfTheRangeSuggestsMoreWeight() = runTest {
        val (workouts, suggestions, _) = setUp(this)
        val curl = db.exerciseDao().getBySourceIds(listOf("Dumbbell_Bicep_Curl")).single()
        val id = workouts.startOrResume()
        workouts.addExercises(id, listOf(curl.id))
        db.workoutDao().getSetsForSession(id).forEach { workouts.completeSet(it.copy(weightKg = 10.0, reps = 12)) }
        workouts.finish(id)

        val s = suggestions.suggestionFor(curl, 8, 12, null, UserPreferences())!!
        assertEquals(SuggestionKind.ADD_WEIGHT, s.kind)
        assertEquals(12.0, s.weightKg!!, 0.0)
    }

    @Test
    fun bodyweightLadderSuggestsTheNextVariation() = runTest {
        val (workouts, suggestions, _) = setUp(this)
        val pushups = db.exerciseDao().getBySourceIds(listOf("Pushups")).single()
        val id = workouts.startOrResume()
        workouts.addExercises(id, listOf(pushups.id))
        db.workoutDao().getSetsForSession(id).forEach { workouts.completeSet(it.copy(reps = 15)) }
        workouts.finish(id)

        val s = suggestions.suggestionFor(pushups, 8, 15, null, UserPreferences())!!
        assertEquals(SuggestionKind.HARDER_VARIATION, s.kind)
        assertTrue(s.headline.contains("Diamond Push-Up"))
    }

    @Test
    fun trainedMusclesShowUpAsFatiguedAndQuickWorkoutsStart() = runTest {
        val (workouts, suggestions, _) = setUp(this)
        val squat = db.exerciseDao().getBySourceIds(listOf("Bodyweight_Squat")).single()
        val id = workouts.startOrResume()
        workouts.addExercises(id, listOf(squat.id, squat.id))
        db.workoutDao().getSetsForSession(id).forEach { workouts.completeSet(it.copy(reps = 20)) }
        workouts.finish(id)

        val status = suggestions.observeMuscleStatus().first().associateBy { it.muscle }
        assertTrue(status.getValue(Muscle.QUADRICEPS).readiness < 0.1)
        assertEquals(1.0, status.getValue(Muscle.CHEST).readiness, 0.0)

        val plan = TrainToday.quickPlan(status.values.toList(), 30)!!
        val sessionId = suggestions.startQuickWorkout(plan, 30)!!
        val exercises = db.workoutDao().getSessionExercises(sessionId)
        assertEquals(plan.slots.size, exercises.size)
        assertTrue(exercises.all { it.targetSets == 3 })
        assertTrue("quads were just trained", Muscle.QUADRICEPS !in plan.slots.map { it.muscle })
        assertTrue(Recovery.FATIGUED_SETS > 0)
    }
}

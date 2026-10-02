package app.forge.fitness.feature.routines

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.forge.domain.program.ProgramTemplates
import app.forge.fitness.data.FakeTime
import app.forge.fitness.data.TestDb
import app.forge.fitness.data.exercise.ExerciseSeeder
import app.forge.fitness.data.prefs.UserPreferencesRepository
import app.forge.fitness.data.routine.RoutineRepository
import app.forge.fitness.data.workout.WorkoutRepository
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = android.app.Application::class)
class PlanFlowsTest {

    @Test
    fun rotationSurvivesDeletingARoutineFromTheProgram() = runTest {
        val db = TestDb.inMemory()
        val time = FakeTime()
        ExerciseSeeder(TestDb.context, db, time).seedIfNeeded()
        val routines = RoutineRepository(db, db.routineDao(), db.exerciseDao(), db.workoutDao(), time)
        val workouts = WorkoutRepository(db, db.workoutDao(), db.bodyMetricDao(), time)
        val prefsFile = File(TestDb.context.filesDir, "plan-test.preferences_pb").apply { delete() }
        val prefs = UserPreferencesRepository(
            PreferenceDataStoreFactory.create(scope = TestScope(testScheduler)) { prefsFile },
        )

        val programId = routines.installTemplate(ProgramTemplates.byKey("home_ppl")!!)
        val (push, pull, legs) = db.routineDao().getProgramRoutines(programId)
        routines.delete(pull.id) // program is now Push, Legs

        // Do Legs: next must be Push (not Legs again).
        val sessionId = workouts.startFromRoutine(routines.getRoutineWithExercises(legs.id)!!)!!
        val set = db.workoutDao().getSetsForSession(sessionId).first()
        workouts.completeSet(set.copy(reps = 10, weightKg = 20.0))
        workouts.finish(sessionId)

        val plan = planFlows(routines, prefs).first { it.active != null && !it.loading }.active!!
        assertEquals(listOf(push.id, legs.id), plan.routines.map { it.id })
        assertEquals(push.id, plan.next!!.id)
        db.close()
    }
}

package app.forge.fitness.data.routine

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.forge.domain.program.ProgramTemplates
import app.forge.fitness.data.FakeTime
import app.forge.fitness.data.TestDb
import app.forge.fitness.data.db.ForgeDatabase
import app.forge.fitness.data.exercise.ExerciseSeeder
import app.forge.fitness.data.workout.WorkoutRepository
import java.time.DayOfWeek
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = android.app.Application::class)
class RoutineRepositoryTest {

    private lateinit var db: ForgeDatabase
    private lateinit var routines: RoutineRepository
    private lateinit var workouts: WorkoutRepository
    private val time = FakeTime()

    @Before
    fun setUp() = runTest {
        db = TestDb.inMemory()
        ExerciseSeeder(TestDb.context, db, time).seedIfNeeded()
        routines = RoutineRepository(db, db.routineDao(), db.exerciseDao(), db.workoutDao(), time)
        workouts = WorkoutRepository(db, db.workoutDao(), db.bodyMetricDao(), time)
    }

    @After
    fun tearDown() = db.close()

    private suspend fun someExerciseIds(n: Int) = db.exerciseDao().observeAll().first().take(n).map { it.id }

    @Test
    fun createAddAndDuplicate() = runTest {
        val id = routines.createRoutine("Upper")
        routines.addExercises(id, someExerciseIds(3))
        val items = db.routineDao().getItems(id)
        assertEquals(3, items.size)
        assertTrue(items.all { it.targetSets == 3 && it.targetMin == 8 && it.targetMax == 12 })

        val copy = routines.duplicate(id)!!
        val copied = db.routineDao().getItems(copy)
        assertEquals(items.map { it.exerciseId }, copied.map { it.exerciseId })
        assertTrue(copied.none { c -> items.any { it.id == c.id } })
        assertEquals("Upper (copy)", db.routineDao().getRoutine(copy)!!.name)
    }

    @Test
    fun reorderAndSupersetsStayConsistent() = runTest {
        val id = routines.createRoutine("Test")
        routines.addExercises(id, someExerciseIds(3))
        val (a, b, c) = db.routineDao().getItems(id)
        routines.supersetWithNext(a.id)
        routines.reorderItems(id, listOf(a.id, c.id, b.id))
        val after = db.routineDao().getItems(id)
        assertEquals(listOf(a.id, c.id, b.id), after.map { it.id })
        assertTrue("a and b no longer adjacent", after.all { it.supersetGroup == null })
    }

    @Test
    fun installingATemplateCreatesAnActiveProgram() = runTest {
        val template = ProgramTemplates.byKey("full_body_home")!!
        val programId = routines.installTemplate(template)
        val program = db.routineDao().getProgram(programId)!!
        assertTrue(program.isActive)
        assertEquals(setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY), DayBits.toDays(program.trainingDays))
        val programRoutines = db.routineDao().getProgramRoutines(programId)
        assertEquals(listOf("Full Body A", "Full Body B"), programRoutines.map { it.name })
        programRoutines.forEachIndexed { i, r ->
            assertEquals(i, r.programPosition)
            assertEquals(template.routines[i].slots.size, db.routineDao().getItems(r.id).size)
        }

        // Only one program is active at a time.
        val second = routines.installTemplate(ProgramTemplates.byKey("express_15")!!)
        assertFalse(db.routineDao().getProgram(programId)!!.isActive)
        assertEquals(second, db.routineDao().observeActiveProgram().first()!!.id)
    }

    @Test
    fun startingFromARoutineCopiesTargetsAndRecordsTheRun() = runTest {
        val programId = routines.installTemplate(ProgramTemplates.byKey("express_15")!!)
        val routine = db.routineDao().getProgramRoutines(programId).single()
        val full = routines.getRoutineWithExercises(routine.id)!!

        val sessionId = workouts.startFromRoutine(full)!!
        val exercises = db.workoutDao().getSessionExercises(sessionId)
        assertEquals(full.active.map { it.exercise.id }, exercises.map { it.exerciseId })
        assertEquals(full.active.map { it.item.supersetGroup }, exercises.map { it.supersetGroup })
        assertEquals(3, exercises.first().targetSets)
        assertEquals(15, exercises.first().targetMin)
        assertEquals(3, db.workoutDao().getSets(exercises.first().id).size)

        // Can't start a second workout while one is running.
        assertNull(workouts.startFromRoutine(full))

        val set = db.workoutDao().getSets(exercises.first().id).first()
        workouts.completeSet(set.copy(reps = 15))
        workouts.finish(sessionId)
        assertEquals(routine.id, db.routineDao().observeLastProgramRun(programId).first()!!.routineId)
    }

    @Test
    fun aFinishedWorkoutCanBecomeARoutine() = runTest {
        val sessionId = workouts.startOrResume()
        workouts.addExercises(sessionId, someExerciseIds(2))
        val first = db.workoutDao().getSessionExercises(sessionId).first()
        val sets = db.workoutDao().getSets(first.id)
        workouts.completeSet(sets[0].copy(weightKg = 20.0, reps = 8))
        workouts.completeSet(sets[1].copy(weightKg = 20.0, reps = 10))
        workouts.finish(sessionId)

        val routineId = routines.saveSessionAsRoutine(sessionId, "Copied")
        val items = db.routineDao().getItems(routineId)
        assertEquals(1, items.size) // the exercise with nothing done was dropped at finish
        assertEquals(2, items.single().targetSets)
        assertEquals(8, items.single().targetMin)
        assertEquals(10, items.single().targetMax)
    }

    @Test
    fun deletingAProgramKeepsItsRoutines() = runTest {
        val programId = routines.installTemplate(ProgramTemplates.byKey("full_body_home")!!)
        val ids = db.routineDao().getProgramRoutines(programId).map { it.id }
        routines.deleteProgram(programId)
        assertNull(db.routineDao().observeActiveProgram().first())
        ids.forEach { id ->
            val r = db.routineDao().getRoutine(id)!!
            assertNull(r.deletedAt)
            assertNull(r.programId)
        }
        assertNotEquals(0, db.routineDao().getRoutines().size)
    }
}

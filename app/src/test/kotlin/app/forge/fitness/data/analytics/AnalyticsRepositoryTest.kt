package app.forge.fitness.data.analytics

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.forge.domain.analytics.RecordKind
import app.forge.fitness.data.FakeTime
import app.forge.fitness.data.TestDb
import app.forge.fitness.data.exercise.ExerciseSeeder
import app.forge.fitness.data.prefs.UserPreferencesRepository
import app.forge.fitness.data.suggest.SuggestionRepository
import app.forge.fitness.data.workout.WorkoutRepository
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = android.app.Application::class)
class AnalyticsRepositoryTest {

    @Test
    fun demoDataLoadsChartsAndRemovesCleanly() = runTest {
        val db = TestDb.inMemory()
        val time = FakeTime(System.currentTimeMillis())
        ExerciseSeeder(TestDb.context, db, time).seedIfNeeded()
        val file = File(TestDb.context.filesDir, "analytics-test.preferences_pb").apply { delete() }
        val prefs = UserPreferencesRepository(PreferenceDataStoreFactory.create(scope = TestScope(testScheduler)) { file })
        val workouts = WorkoutRepository(db, db.workoutDao(), db.bodyMetricDao(), time)
        val suggestions = SuggestionRepository(db.workoutDao(), db.exerciseDao(), workouts, prefs, time)
        val analytics = AnalyticsRepository(db.workoutDao(), db.exerciseDao(), db.bodyMetricDao(), suggestions, time)
        val demo = DemoDataLoader(db)

        // One real workout that must survive removing the demo data.
        val pushups = db.exerciseDao().getBySourceIds(listOf("Pushups")).single()
        val real = workouts.startOrResume()
        workouts.addExercises(real, listOf(pushups.id))
        db.workoutDao().getSetsForSession(real).forEach { workouts.completeSet(it.copy(reps = 12)) }
        workouts.finish(real)

        val added = demo.load()
        assertTrue("demo workouts: $added", added > 20)
        assertEquals(0, demo.load()) // loading twice does nothing

        val overview = analytics.observeOverview().first()
        assertTrue(overview.workouts30 > 5)
        assertEquals(17 * 7, overview.heatmap.size)
        assertTrue(overview.weekStreak >= 1)
        assertTrue(overview.topExercises.isNotEmpty())

        val squat = db.exerciseDao().getBySourceIds(listOf("forge:bag_bear_hug_squat")).single()
        val progress = analytics.observeExerciseProgress(squat.id).first()!!
        assertTrue(progress.series.size > 5)
        assertTrue("strength goes up", progress.series.last().second > progress.series.first().second)
        assertTrue(RecordKind.HEAVIEST in progress.records)

        demo.remove()
        assertEquals(1, db.workoutDao().observeHistory().first().size)
        assertEquals(real, db.workoutDao().observeHistory().first().single().id)
        assertTrue(db.bodyMetricDao().observeEverything().first().isEmpty())
        db.close()
    }

    @Test
    fun aHeavierSetIsANewRecordInThatWorkout() = runTest {
        val db = TestDb.inMemory()
        val time = FakeTime()
        ExerciseSeeder(TestDb.context, db, time).seedIfNeeded()
        val file = File(TestDb.context.filesDir, "analytics-test2.preferences_pb").apply { delete() }
        val prefs = UserPreferencesRepository(PreferenceDataStoreFactory.create(scope = TestScope(testScheduler)) { file })
        val workouts = WorkoutRepository(db, db.workoutDao(), db.bodyMetricDao(), time)
        val analytics = AnalyticsRepository(
            db.workoutDao(), db.exerciseDao(), db.bodyMetricDao(),
            SuggestionRepository(db.workoutDao(), db.exerciseDao(), workouts, prefs, time), time,
        )
        val curl = db.exerciseDao().getBySourceIds(listOf("Dumbbell_Bicep_Curl")).single()
        suspend fun workout(weight: Double): String {
            val id = workouts.startOrResume()
            workouts.addExercises(id, listOf(curl.id))
            db.workoutDao().getSetsForSession(id).forEach { workouts.completeSet(it.copy(weightKg = weight, reps = 10)) }
            workouts.finish(id)
            time.advance(86_400_000)
            return id
        }
        val first = workout(10.0)
        val second = workout(12.0)
        assertTrue(analytics.prsInSession(first).isEmpty())
        val prs = analytics.prsInSession(second).map { it.record.kind }
        assertTrue(RecordKind.HEAVIEST in prs)
        assertTrue(RecordKind.E1RM in prs)
        db.close()
    }
}

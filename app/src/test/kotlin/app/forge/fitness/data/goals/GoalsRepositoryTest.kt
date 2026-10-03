package app.forge.fitness.data.goals

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.forge.domain.goals.DayMask
import app.forge.domain.goals.GoalKind
import app.forge.domain.goals.HabitKind
import app.forge.domain.nutrition.Nutrients
import app.forge.fitness.data.FakeTime
import app.forge.fitness.data.TestDb
import app.forge.fitness.data.db.ForgeDatabase
import app.forge.fitness.data.nutrition.FoodCatalog
import app.forge.fitness.data.nutrition.FoodRepository
import app.forge.fitness.data.nutrition.LookupResult
import app.forge.fitness.data.nutrition.SearchResult
import app.forge.fitness.data.prefs.CustomTargets
import app.forge.fitness.data.prefs.UserPreferencesRepository
import app.forge.fitness.data.workout.WorkoutRepository
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = android.app.Application::class)
class GoalsRepositoryTest {
    private lateinit var db: ForgeDatabase
    // Noon today, so "today" is the same in every time zone the test might run in.
    private val time = FakeTime(LocalDate.now().atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())
    private val today: LocalDate get() = Instant.ofEpochMilli(time.millis).atZone(ZoneId.systemDefault()).toLocalDate()

    private object NoCatalog : FoodCatalog {
        override suspend fun product(barcode: String) = LookupResult.NotFound
        override suspend fun search(query: String) = SearchResult.Results(emptyList())
    }

    @After
    fun tearDown() {
        if (::db.isInitialized) db.close()
    }

    private suspend fun setUp(scope: TestScope): Triple<GoalsRepository, WorkoutRepository, FoodRepository> {
        db = TestDb.inMemory()
        val file = File(TestDb.context.filesDir, "goals-test.preferences_pb").apply { delete() }
        val prefs = UserPreferencesRepository(PreferenceDataStoreFactory.create(scope = TestScope(scope.testScheduler)) { file })
        prefs.setCustomTargets(CustomTargets(2500, 120, 300, 80))
        val foods = FoodRepository(db.foodDao(), db.bodyMetricDao(), prefs, NoCatalog, time)
        val goals = GoalsRepository(
            db.goalDao(), db.workoutDao(), db.exerciseDao(), db.activityDao(), db.foodDao(), db.bodyMetricDao(), foods, time,
        )
        return Triple(goals, WorkoutRepository(db, db.workoutDao(), db.bodyMetricDao(), time), foods)
    }

    @Test
    fun customHabitTicksAndCountsAStreak() = runTest {
        val (goals, _, _) = setUp(this)
        // Created a few days ago, so earlier days can count.
        time.advance(-3 * DAY)
        val id = goals.saveHabit(null, HabitKind.CUSTOM, "Stretch", null, DayMask.ALL, null)
        goals.setChecked(id, today, true)
        time.advance(DAY)
        goals.setChecked(id, today, true)
        time.advance(2 * DAY)
        goals.toggleToday(id)
        val habit = goals.observeOverview().first().habits.single()
        assertEquals(true, habit.doneToday)
        assertEquals(1, habit.streak)
        assertEquals(2, habit.bestStreak)
        // Yesterday was missed; today is done.
        assertEquals(listOf(false, true), habit.week.takeLast(2))
        goals.toggleToday(id)
        assertEquals(false, goals.observeOverview().first().habits.single().doneToday)
    }

    @Test
    fun autoHabitsTickFromYourData() = runTest {
        val (goals, workouts, foods) = setUp(this)
        goals.saveHabit(null, HabitKind.TRAIN, "", null, DayMask.ALL, null)
        goals.saveHabit(null, HabitKind.PROTEIN, "", null, DayMask.ALL, null)
        goals.saveHabit(null, HabitKind.STEPS, "", 8_000.0, DayMask.ALL, null)
        val id = workouts.startOrResume()
        time.advance(60_000)
        workouts.finish(id)
        foods.quickAdd("Shake", Nutrients(kcal = 600.0, proteinG = 118.0), app.forge.domain.nutrition.Meal.SNACKS, today)
        val byKind = goals.observeOverview().first().habits.associateBy { it.kind }
        assertEquals(true, byKind[HabitKind.TRAIN]!!.doneToday)
        assertEquals("118 g is within 5% of 120 g", true, byKind[HabitKind.PROTEIN]!!.doneToday)
        assertNull("no step data yet", byKind[HabitKind.STEPS]!!.doneToday)
        assertEquals("Train or do an activity", byKind[HabitKind.TRAIN]!!.habit.name)
    }

    @Test
    fun goalsTrackProgressAndAchievements() = runTest {
        val (goals, workouts, _) = setUp(this)
        goals.addGoal(GoalKind.WORKOUTS_PER_WEEK, 3.0, null, null)
        val id = workouts.startOrResume()
        time.advance(60_000)
        workouts.finish(id)
        val overview = goals.observeOverview().first()
        val weekly = overview.goals.single()
        assertEquals(1.0, weekly.progress.current!!, 0.0)
        assertTrue(weekly.detail.startsWith("1 / 3"))
        assertEquals(1, overview.stats.workouts)
        assertTrue(app.forge.domain.goals.Achievement.FIRST_WORKOUT.unlocked(overview.stats))
    }

    private companion object {
        const val DAY = 86_400_000L
    }
}

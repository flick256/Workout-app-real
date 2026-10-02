package app.forge.fitness.data.exercise

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.forge.fitness.data.FakeTime
import app.forge.fitness.data.TestDb
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = android.app.Application::class)
class ExerciseSeederTest {

    @Test
    fun seedsTheBundledLibraryOnceAndKeepsCustomExercises() = runTest {
        val db = TestDb.inMemory()
        db.exerciseDao().insertAll(listOf(TestDb.exercise("my-own", name = "Backpack Row")))
        val seeder = ExerciseSeeder(TestDb.context, db, FakeTime())

        seeder.seedIfNeeded()
        val afterFirst = db.exerciseDao().observeAll().first()
        assertTrue("expected 800+ exercises, got ${afterFirst.size}", afterFirst.size > 800)
        assertTrue(afterFirst.any { it.name == "Backpack Row" })

        seeder.seedIfNeeded() // second launch: nothing changes
        assertEquals(afterFirst.size, db.exerciseDao().observeAll().first().size)
        db.close()
    }
}

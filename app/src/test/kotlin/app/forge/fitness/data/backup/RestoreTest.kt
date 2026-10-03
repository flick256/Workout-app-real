package app.forge.fitness.data.backup

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.forge.fitness.data.FakeTime
import app.forge.fitness.data.TestDb
import app.forge.fitness.data.db.ForgeDatabase
import app.forge.fitness.data.db.HeartRateSampleEntity
import app.forge.fitness.data.photos.PhotoRepository
import app.forge.fitness.data.prefs.UserPreferencesRepository
import app.forge.fitness.data.workout.WorkoutRepository
import java.io.File
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
class RestoreTest {
    private val dbs = mutableListOf<ForgeDatabase>()
    private val time = FakeTime()

    @After
    fun tearDown() = dbs.forEach { it.close() }

    private class Phone(val db: ForgeDatabase, val exporter: JsonExporter, val restorer: BackupRestorer, val workouts: WorkoutRepository)

    private fun phone(name: String, scope: TestScope): Phone {
        val db = TestDb.inMemory().also { dbs += it }
        val file = File(TestDb.context.filesDir, "restore-$name.preferences_pb").apply { delete() }
        val prefs = UserPreferencesRepository(PreferenceDataStoreFactory.create(scope = TestScope(scope.testScheduler)) { file })
        val exporter = JsonExporter(
            TestDb.context, db.exerciseDao(), db.workoutDao(), db.bodyMetricDao(), db.routineDao(), db.photoDao(),
            db.activityDao(), db.foodDao(), db.goalDao(), db.heartRateDao(), prefs, time,
        )
        val snapshots = SnapshotStore(TestDb.context, exporter)
        val restorer = BackupRestorer(TestDb.context, db, exporter, snapshots, PhotoRepository(TestDb.context, db.photoDao(), time), prefs)
        return Phone(db, exporter, restorer, WorkoutRepository(db, db.workoutDao(), db.bodyMetricDao(), time))
    }

    @Test
    fun aBackupRestoresOntoAnotherPhoneAndTwiceChangesNothing() = runTest {
        val old = phone("old", this)
        old.db.exerciseDao().insert(TestDb.exercise("e1", "Bag Squat"))
        val id = old.workouts.startOrResume("Legs")
        old.workouts.addExercises(id, listOf("e1"))
        old.db.workoutDao().getSetsForSession(id).forEach { old.workouts.completeSet(it.copy(weightKg = 20.0, reps = 10)) }
        old.db.heartRateDao().insert(listOf(HeartRateSampleEntity(id, 1_000, 120), HeartRateSampleEntity(id, 3_000, 150)))
        time.advance(60_000)
        old.workouts.finish(id)
        val text = old.exporter.encode(old.exporter.build()).decodeToString()

        val new = phone("new", this)
        // The bundled library is the same on every install; this test adds the one exercise by hand.
        new.db.exerciseDao().insert(TestDb.exercise("e1", "Bag Squat"))
        val export = new.restorer.decode(text)
        val first = new.restorer.restore(export, includeSettings = false)
        assertTrue(first.added >= 1 + 1 + 3)
        assertEquals("Legs", new.db.workoutDao().getSession(id)!!.name)
        assertEquals(3, new.db.workoutDao().getSetsForSession(id).count { it.completedAt != null })
        assertEquals(2, new.db.heartRateDao().count(id))

        val second = new.restorer.restore(export, includeSettings = false)
        assertEquals(0, second.added + second.updated)
    }

    @Test
    fun newerChangesOnThePhoneAreKept() = runTest {
        val p = phone("keep", this)
        val id = p.workouts.startOrResume("Original")
        val backup = p.restorer.decode(p.exporter.encode(p.exporter.build()).decodeToString())
        time.advance(1_000)
        p.workouts.renameSession(id, "Renamed later")
        val report = p.restorer.restore(backup, includeSettings = false)
        assertEquals("Renamed later", p.db.workoutDao().getSession(id)!!.name)
        assertEquals(0, report.updated)
        assertTrue(report.snapshotName != null)
    }

    @Test
    fun notAForgeBackupIsRejected() = runTest {
        val p = phone("bad", this)
        val error = runCatching { p.restorer.decode("""{"hello": 1}""") }.exceptionOrNull()
        assertTrue(error != null)
    }
}

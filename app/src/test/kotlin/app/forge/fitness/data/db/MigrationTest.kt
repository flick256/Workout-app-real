package app.forge.fitness.data.db

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.forge.domain.model.BodyMetricKind
import app.forge.fitness.data.TestDb
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Builds a database exactly as version 1 of the app created it (from the committed
 * schema in app/schemas), fills it with a workout, then opens it with the current app.
 * If any migration loses or breaks data, this fails before you ever install it.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = android.app.Application::class)
class MigrationTest {

    private val name = "migration-test.db"

    @Test
    fun version1WorkoutSurvivesUpgrade() = runTest {
        TestDb.context.deleteDatabase(name)
        createVersion1Database()

        val db = Room.databaseBuilder(TestDb.context, ForgeDatabase::class.java, name)
            .allowMainThreadQueries()
            .build()

        val session = db.workoutDao().getSession("s1")!!
        assertEquals("Old workout", session.name)
        val set = db.workoutDao().getSet("x1")!!
        assertEquals(20.0, set.weightKg!!, 0.0)
        assertEquals(10, set.reps)
        assertNull("new column starts empty", set.loadKg)
        val exercise = db.exerciseDao().getById("e1")!!
        assertNull(exercise.bodyweightProfile)
        assertEquals(1, db.workoutDao().observeHistory().first().size)
        // Tables added in later versions work.
        assertNull(db.bodyMetricDao().latest(BodyMetricKind.WEIGHT))
        assertEquals(0, db.routineDao().getRoutines().size)
        assertNull(db.workoutDao().getSessionExercise("se1")!!.targetSets)
        assertEquals(false, session.isDemo)
        assertEquals(0, db.photoDao().observeAll().first().size)
        assertNull(session.avgHeartRate)
        assertEquals(0, db.activityDao().observeAll().first().size)
        assertEquals(0, db.activityDao().observeDaily(0).first().size)
        assertEquals(0, db.foodDao().observeDay(0).first().size)
        assertNull(db.foodDao().getByBarcode("9300633603205"))
        db.close()
        TestDb.context.deleteDatabase(name)
    }

    private fun createVersion1Database() {
        val schema = Json.parseToJsonElement(schemaFile(1).readText()).jsonObject["database"]!!.jsonObject
        val file = TestDb.context.getDatabasePath(name).apply { parentFile?.mkdirs() }
        val sql = SQLiteDatabase.openOrCreateDatabase(file, null)
        schema["entities"]!!.jsonArray.forEach { entity ->
            val e = entity.jsonObject
            val table = e["tableName"]!!.jsonPrimitive.content
            sql.execSQL(e["createSql"]!!.jsonPrimitive.content.replace("\${TABLE_NAME}", table))
            e["indices"]?.jsonArray?.forEach { index ->
                sql.execSQL(index.jsonObject["createSql"]!!.jsonPrimitive.content.replace("\${TABLE_NAME}", table))
            }
        }
        schema["setupQueries"]!!.jsonArray.forEach { sql.execSQL(it.jsonPrimitive.content) }

        sql.execSQL(
            """INSERT INTO exercise (id, name, primaryMuscles, secondaryMuscles, equipment, category, logType,
               mechanic, force, level, instructions, images, isCustom, sourceId, archived, createdAt, updatedAt, deletedAt)
               VALUES ('e1', 'Bag Squat', '["QUADRICEPS"]', '[]', 'DUMBBELL', 'STRENGTH', 'WEIGHT_REPS',
               NULL, NULL, NULL, '[]', '[]', 1, NULL, 0, 1, 1, NULL)""",
        )
        sql.execSQL(
            """INSERT INTO workout_session VALUES ('s1', 'Old workout', NULL, 1000, 2000, 'FINISHED', NULL, NULL, 1, 1, NULL)""",
        )
        sql.execSQL("""INSERT INTO session_exercise VALUES ('se1', 's1', 'e1', 0, NULL, NULL, NULL, 1, 1, NULL)""")
        sql.execSQL(
            """INSERT INTO set_entry VALUES ('x1', 'se1', 0, 'WORKING', 20.0, 10, NULL, NULL, NULL, 1500, 1, 1, NULL)""",
        )
        sql.version = 1
        sql.close()
    }

    private fun schemaFile(version: Int): File =
        generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
            .map { File(it, "app/schemas/app.forge.fitness.data.db.ForgeDatabase/$version.json") }
            .plus(sequenceOf(File("schemas/app.forge.fitness.data.db.ForgeDatabase/$version.json")))
            .first { it.exists() }
}

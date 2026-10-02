package app.forge.domain.dataset

import app.forge.domain.model.Equipment
import app.forge.domain.model.ExerciseCategory
import app.forge.domain.model.LogType
import app.forge.domain.model.Muscle
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class ExerciseDatasetTest {

    private val sample = """
        [{"name":"Pushups","force":"push","level":"beginner","mechanic":"compound",
          "equipment":"body only","primaryMuscles":["chest"],
          "secondaryMuscles":["shoulders","triceps"],"instructions":["Get down.","Push up."],
          "category":"strength","images":["Pushups/0.jpg"],"id":"Pushups","extra":"ignored"},
         {"name":"Plank","force":"static","equipment":"body only",
          "primaryMuscles":["abdominals"],"category":"strength","id":"Plank"},
         {"name":"Dumbbell Bench Press","force":"push","equipment":"dumbbell",
          "primaryMuscles":["chest"],"category":"strength","id":"Dumbbell_Bench_Press"},
         {"name":"Jogging","equipment":null,"primaryMuscles":["quadriceps"],
          "category":"cardio","id":"Jogging"}]
    """.trimIndent()

    @Test
    fun `maps fields into app types`() {
        val pushups = ExerciseDataset.parse(sample).first()
        assertEquals("Pushups", pushups.sourceId)
        assertEquals(listOf(Muscle.CHEST), pushups.primaryMuscles)
        assertEquals(listOf(Muscle.SHOULDERS, Muscle.TRICEPS), pushups.secondaryMuscles)
        assertEquals(Equipment.BODY_ONLY, pushups.equipment)
        assertEquals(ExerciseCategory.STRENGTH, pushups.category)
        assertEquals(2, pushups.instructions.size)
    }

    @Test
    fun `infers how each exercise is logged`() {
        val byId = ExerciseDataset.parse(sample).associateBy { it.sourceId }
        assertEquals(LogType.REPS, byId.getValue("Pushups").logType)
        assertEquals(LogType.DURATION, byId.getValue("Plank").logType)
        assertEquals(LogType.WEIGHT_REPS, byId.getValue("Dumbbell_Bench_Press").logType)
        assertEquals(LogType.DISTANCE_DURATION, byId.getValue("Jogging").logType)
    }

    @Test
    fun `ids are stable and unique`() {
        assertEquals(ExerciseDataset.stableId("Pushups"), ExerciseDataset.stableId("Pushups"))
        assertNotEquals(ExerciseDataset.stableId("Pushups"), ExerciseDataset.stableId("Plank"))
    }

    @Test
    fun `the bundled dataset parses completely`() {
        val file = findBundledDataset()
        val seeds = ExerciseDataset.parse(file.readText())
        assertTrue(seeds.size >= 800, "expected 800+ exercises, got ${seeds.size}")
        assertEquals(seeds.size, seeds.map { it.id }.toSet().size, "duplicate ids")
        // Every exercise should have at least one recognised primary muscle.
        val unmapped = seeds.filter { it.primaryMuscles.isEmpty() }.map { it.name }
        assertTrue(unmapped.isEmpty(), "no primary muscle for: $unmapped")
    }

    private fun findBundledDataset(): File {
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
        while (dir != null) {
            val candidate = File(dir, "app/src/main/assets/exercises.json")
            if (candidate.exists()) return candidate
            dir = dir.parentFile
        }
        error("app/src/main/assets/exercises.json not found above ${System.getProperty("user.dir")}")
    }
}

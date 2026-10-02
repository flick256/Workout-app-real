package app.forge.domain.dataset

import app.forge.domain.model.LogType
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HomePackTest {

    private val dataset by lazy {
        val file = generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
            .map { File(it, "app/src/main/assets/exercises.json") }
            .first { it.exists() }
        ExerciseDataset.parse(file.readText())
    }

    @Test
    fun `ids and names are unique and don't clash with the dataset`() {
        val pack = HomePack.exercises
        assertEquals(pack.size, pack.map { it.id }.toSet().size)
        assertEquals(pack.size, pack.map { it.sourceId }.toSet().size)
        val datasetIds = dataset.map { it.id }.toSet()
        assertTrue(pack.none { it.id in datasetIds })
        val datasetNames = dataset.map { it.name.lowercase() }.toSet()
        val clashes = pack.filter { it.name.lowercase() in datasetNames }.map { it.name }
        assertTrue(clashes.isEmpty(), "pack names duplicate dataset names: $clashes")
    }

    @Test
    fun `every progression step exists`() {
        val known = (dataset.map { it.sourceId } + HomePack.exercises.map { it.sourceId }).toSet()
        HomePack.Chain.entries.forEach { chain ->
            val missing = chain.steps.filter { it !in known }
            assertTrue(missing.isEmpty(), "${chain.name} has unknown steps: $missing")
            assertTrue(chain.steps.size >= 2, "${chain.name} needs at least two steps")
        }
    }

    @Test
    fun `an exercise belongs to at most one progression`() {
        val all = HomePack.Chain.entries.flatMap { it.steps }
        assertEquals(all.size, all.toSet().size)
    }

    @Test
    fun `bodyweight profiles only on rep exercises and every pack exercise has instructions`() {
        HomePack.exercises.forEach {
            if (it.bodyweightProfile != null) assertEquals(LogType.REPS, it.logType, it.name)
            assertTrue(it.instructions.isNotEmpty(), "${it.name} has no instructions")
            assertTrue(it.primaryMuscles.isNotEmpty(), "${it.name} has no muscles")
        }
    }
}

package app.forge.domain.program

import app.forge.domain.dataset.ExerciseDataset
import app.forge.domain.dataset.HomePack
import app.forge.domain.model.LogType
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProgramTemplatesTest {

    private val library by lazy {
        val file = generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
            .map { File(it, "app/src/main/assets/exercises.json") }
            .first { it.exists() }
        (ExerciseDataset.parse(file.readText()) + HomePack.exercises).associateBy { it.sourceId }
    }

    @Test
    fun `every template exercise exists in the library`() {
        ProgramTemplates.all.forEach { program ->
            program.routines.flatMap { it.slots }.forEach { slot ->
                assertTrue(slot.sourceId in library, "${program.key}: unknown exercise ${slot.sourceId}")
            }
        }
    }

    @Test
    fun `templates only use equipment they declare`() {
        ProgramTemplates.all.forEach { program ->
            program.routines.flatMap { it.slots }.forEach { slot ->
                val equipment = library.getValue(slot.sourceId).equipment
                assertTrue(
                    equipment == null || equipment in program.equipment ||
                        equipment == app.forge.domain.model.Equipment.BODY_ONLY,
                    "${program.key}: ${slot.sourceId} needs $equipment",
                )
            }
        }
    }

    @Test
    fun `targets and supersets are well formed`() {
        ProgramTemplates.all.forEach { program ->
            assertEquals(program.key, ProgramTemplates.byKey(program.key)?.key)
            program.routines.forEach { routine ->
                routine.slots.forEach { slot ->
                    assertTrue(slot.sets in 1..6, "${routine.name} ${slot.sourceId} sets")
                    if (slot.targetMin != null && slot.targetMax != null) {
                        assertTrue(slot.targetMin <= slot.targetMax, "${routine.name} ${slot.sourceId} range")
                    }
                }
                // Superset members must sit next to each other.
                val groups = routine.slots.mapNotNull { it.supersetGroup }.distinct()
                groups.forEach { g ->
                    val idx = routine.slots.indices.filter { routine.slots[it].supersetGroup == g }
                    assertEquals((idx.first()..idx.last()).toList(), idx, "${routine.name} superset $g not adjacent")
                    assertTrue(idx.size >= 2)
                }
            }
        }
    }

    @Test
    fun `advertised durations are roughly right`() {
        ProgramTemplates.all.forEach { program ->
            program.routines.forEach { routine ->
                val minutes = WorkoutEstimate.minutes(
                    routine.slots.map { slot ->
                        val timed = library.getValue(slot.sourceId).logType == LogType.DURATION
                        WorkoutEstimate.Slot(slot.sets, slot.restSeconds, slot.supersetGroup, slot.targetMax.takeIf { timed })
                    },
                )
                assertTrue(
                    minutes in (program.minutes - 10)..(program.minutes + 10),
                    "${routine.name}: estimated $minutes min, advertised ${program.minutes}",
                )
            }
        }
    }
}

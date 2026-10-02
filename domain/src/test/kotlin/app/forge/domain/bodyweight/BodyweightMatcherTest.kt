package app.forge.domain.bodyweight

import app.forge.domain.dataset.ExerciseDataset
import app.forge.domain.model.Equipment
import app.forge.domain.model.ExerciseCategory
import app.forge.domain.model.LogType
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BodyweightMatcherTest {

    private fun m(name: String, equipment: Equipment? = Equipment.BODY_ONLY, static: Boolean = false) =
        BodyweightMatcher.match(name, equipment, ExerciseCategory.STRENGTH, static)

    @Test
    fun `push-up family`() {
        assertEquals(BodyweightProfile.PUSH_UP, m("Pushups"))
        assertEquals(BodyweightProfile.PUSH_UP, m("Push-Up Wide"))
        assertEquals(BodyweightProfile.PUSH_UP, m("Push-Ups - Close Triceps Position"))
        assertEquals(BodyweightProfile.INCLINE_PUSH_UP, m("Incline Push-Up Medium"))
        assertEquals(BodyweightProfile.DECLINE_PUSH_UP, m("Decline Push-Up", null))
        assertEquals(BodyweightProfile.DECLINE_PUSH_UP, m("Push-Ups With Feet Elevated"))
        assertEquals(BodyweightProfile.ONE_ARM_PUSH_UP, m("Single-Arm Push-Up"))
        assertEquals(BodyweightProfile.HANDSTAND_PUSH_UP, m("Handstand Push-Ups"))
    }

    @Test
    fun `pull, dip, legs and core`() {
        assertEquals(BodyweightProfile.PULL_UP, m("Pullups"))
        assertEquals(BodyweightProfile.PULL_UP, m("Chin-Up"))
        assertEquals(BodyweightProfile.ONE_ARM_PULL_UP, m("One Arm Chin-Up", Equipment.OTHER))
        assertEquals(BodyweightProfile.MUSCLE_UP, m("Kipping Muscle Up", Equipment.OTHER))
        assertEquals(BodyweightProfile.INVERTED_ROW, m("Inverted Row", null))
        assertEquals(BodyweightProfile.DIP, m("Dips - Triceps Version"))
        assertEquals(BodyweightProfile.BENCH_DIP, m("Bench Dips"))
        assertEquals(BodyweightProfile.SQUAT, m("Bodyweight Squat"))
        assertEquals(BodyweightProfile.SPLIT_SQUAT, m("Bodyweight Walking Lunge", null))
        assertEquals(BodyweightProfile.HANGING_LEG_RAISE, m("Hanging Leg Raise"))
        assertEquals(BodyweightProfile.SIT_UP, m("3/4 Sit-Up"))
    }

    @Test
    fun `things that are not bodyweight reps are left alone`() {
        assertNull(m("Plank", static = true))
        assertNull(m("Incline Push-Up Depth Jump", Equipment.OTHER))
        assertNull(m("Band Assisted Pull-Up", Equipment.OTHER))
        assertNull(m("Wide-Grip Lat Pulldown", Equipment.CABLE))
        assertNull(m("Leg Pull-In"))
        assertNull(m("Barbell Squat", Equipment.BARBELL))
        assertNull(BodyweightMatcher.match("Split Squats", null, ExerciseCategory.STRETCHING, false))
    }

    @Test
    fun `the bundled dataset gets profiles and rep logging`() {
        val file = generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
            .map { File(it, "app/src/main/assets/exercises.json") }
            .first { it.exists() }
        val seeds = ExerciseDataset.parse(file.readText())
        val profiled = seeds.filter { it.bodyweightProfile != null }
        assertTrue(profiled.size >= 50, "expected 50+ bodyweight exercises, got ${profiled.size}")
        assertTrue(profiled.all { it.logType == LogType.REPS })
        assertEquals(BodyweightProfile.PUSH_UP, seeds.first { it.name == "Pushups" }.bodyweightProfile)
    }
}

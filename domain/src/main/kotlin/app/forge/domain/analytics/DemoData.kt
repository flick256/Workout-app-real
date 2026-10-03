package app.forge.domain.analytics

import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.math.roundToInt
import kotlin.random.Random

data class DemoSet(val weightKg: Double?, val reps: Int?, val seconds: Int? = null, val rpe: Double? = null, val warmup: Boolean = false)

data class DemoExercise(val sourceId: String, val sets: List<DemoSet>)

data class DemoSession(val date: LocalDate, val hour: Int, val minutes: Int, val name: String, val exercises: List<DemoExercise>)

data class DemoBodyweight(val date: LocalDate, val kg: Double)

data class DemoPlan(val sessions: List<DemoSession>, val bodyweight: List<DemoBodyweight>)

/**
 * A believable 12 weeks of home training (Full Body A/B on Mon/Wed/Fri, the odd
 * missed day, slow steady progress) so charts and suggestions have something to show
 * before you've logged much. Same seed = same data.
 */
object DemoData {

    fun generate(today: LocalDate, weeks: Int = 12, seed: Int = 42): DemoPlan {
        val rnd = Random(seed)
        val days = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)
        val start = today.minusWeeks(weeks.toLong())
        val sessions = mutableListOf<DemoSession>()
        var useA = true
        var date = start
        while (date.isBefore(today)) {
            if (date.dayOfWeek in days && rnd.nextDouble() > 0.15) {
                val progress = (date.toEpochDay() - start.toEpochDay()) / (weeks * 7.0) // 0 → 1 over the period
                sessions += if (useA) sessionA(date, progress, rnd) else sessionB(date, progress, rnd)
                useA = !useA
            }
            date = date.plusDays(1)
        }
        val bodyweight = generateSequence(start) { it.plusDays(3) }
            .takeWhile { it.isBefore(today) }
            .map { d ->
                val trend = 68.0 + 1.2 * (d.toEpochDay() - start.toEpochDay()) / (weeks * 7.0)
                DemoBodyweight(d, ((trend + rnd.nextDouble(-0.4, 0.4)) * 10).roundToInt() / 10.0)
            }.toList()
        return DemoPlan(sessions, bodyweight)
    }

    private fun reps(base: Int, gain: Int, progress: Double, rnd: Random, sets: Int, weight: Double? = null) =
        List(sets) { i ->
            val r = (base + gain * progress).roundToInt() - i / 2 + rnd.nextInt(-1, 2)
            DemoSet(weight, r.coerceAtLeast(1), rpe = listOf(7.0, 7.5, 8.0, 8.5).random(rnd))
        }

    private fun stepped(start: Double, step: Double, every: Double, progress: Double) =
        start + step * (progress / every).toInt()

    private fun sessionA(date: LocalDate, p: Double, rnd: Random): DemoSession {
        val squat = stepped(15.0, 2.5, 0.2, p)
        val rdl = stepped(15.0, 2.5, 0.25, p)
        return DemoSession(
            date, 17 + rnd.nextInt(0, 3), 30 + rnd.nextInt(-3, 6), "Full Body A",
            listOf(
                DemoExercise("forge:bag_bear_hug_squat", listOf(DemoSet(squat / 2, 8, warmup = true)) + reps(9, 3, p % 0.2 * 5, rnd, 3, squat)),
                DemoExercise("Pushups", reps(9, 6, p, rnd, 3)),
                DemoExercise("forge:table_row", reps(7, 5, p, rnd, 3)),
                DemoExercise("forge:bag_romanian_deadlift", reps(9, 3, p % 0.25 * 4, rnd, 3, rdl)),
                DemoExercise("Plank", List(3) { DemoSet(null, null, seconds = (30 + 25 * p).roundToInt() + rnd.nextInt(-5, 6)) }),
            ),
        )
    }

    private fun sessionB(date: LocalDate, p: Double, rnd: Random) = DemoSession(
        date, 17 + rnd.nextInt(0, 3), 28 + rnd.nextInt(-3, 6), "Full Body B",
        listOf(
            DemoExercise("forge:split_squat", reps(8, 4, p, rnd, 3)),
            DemoExercise("forge:pike_push_up", reps(6, 4, p, rnd, 3)),
            DemoExercise("forge:doorway_row", reps(10, 5, p, rnd, 3)),
            DemoExercise("Butt_Lift_Bridge", reps(12, 6, p, rnd, 3)),
            DemoExercise("Dead_Bug", reps(8, 4, p, rnd, 3)),
        ),
    )
}

package app.forge.fitness.data.analytics

import androidx.room.withTransaction
import app.forge.domain.analytics.DemoData
import app.forge.domain.model.BodyMetricKind
import app.forge.domain.model.SessionStatus
import app.forge.domain.model.SetType
import app.forge.fitness.data.db.BodyMetricEntity
import app.forge.fitness.data.db.ForgeDatabase
import app.forge.fitness.data.db.SessionExerciseEntity
import app.forge.fitness.data.db.SetEntryEntity
import app.forge.fitness.data.db.WorkoutSessionEntity
import app.forge.fitness.data.workout.Loads
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fills the app with 12 weeks of sample training so you can explore the charts and
 * suggestions. Everything it adds is flagged, and [remove] deletes exactly that.
 */
@Singleton
class DemoDataLoader @Inject constructor(private val db: ForgeDatabase) {

    suspend fun hasDemoData(): Boolean = db.demoDao().demoSessionCount() > 0

    /** Returns the number of workouts added (0 if demo data is already loaded). */
    suspend fun load(today: LocalDate = LocalDate.now()): Int = db.withTransaction {
        if (hasDemoData()) return@withTransaction 0
        val plan = DemoData.generate(today)
        val zone = ZoneId.systemDefault()
        val sourceIds = plan.sessions.flatMap { s -> s.exercises.map { it.sourceId } }.distinct()
        val exercises = db.exerciseDao().getBySourceIds(sourceIds).associateBy { it.sourceId }
        val workouts = db.workoutDao()

        plan.bodyweight.forEach { bw ->
            val at = bw.date.atTime(7, 30).atZone(zone).toInstant().toEpochMilli()
            db.bodyMetricDao().insert(
                BodyMetricEntity(newId(), BodyMetricKind.WEIGHT, bw.kg, at, createdAt = at, updatedAt = at, isDemo = true),
            )
        }

        plan.sessions.forEach { demo ->
            val start = demo.date.atTime(demo.hour, 0).atZone(zone).toInstant().toEpochMilli()
            val end = start + demo.minutes * 60_000L
            val bodyweight = plan.bodyweight.lastOrNull { !it.date.isAfter(demo.date) }?.kg
            val sessionId = newId()
            workouts.insertSession(
                WorkoutSessionEntity(
                    id = sessionId, name = demo.name, routineId = null, startedAt = start, endedAt = end,
                    status = SessionStatus.FINISHED, notes = null, bodyweightKg = bodyweight,
                    createdAt = start, updatedAt = end, isDemo = true,
                ),
            )
            demo.exercises.forEachIndexed { position, ex ->
                val exercise = exercises[ex.sourceId] ?: return@forEachIndexed
                val itemId = newId()
                workouts.insertSessionExercises(
                    listOf(
                        SessionExerciseEntity(
                            id = itemId, sessionId = sessionId, exerciseId = exercise.id, position = position,
                            supersetGroup = null, notes = null, restSeconds = null, createdAt = start, updatedAt = start,
                        ),
                    ),
                )
                workouts.insertSets(
                    ex.sets.mapIndexed { i, s ->
                        val done = start + (position * 6 + i) * 90_000L
                        SetEntryEntity(
                            id = newId(), sessionExerciseId = itemId, position = i,
                            type = if (s.warmup) SetType.WARMUP else SetType.WORKING,
                            weightKg = s.weightKg, reps = s.reps, rpe = s.rpe, durationSeconds = s.seconds,
                            distanceMeters = null, completedAt = done, createdAt = done, updatedAt = done,
                        ).let { set -> set.copy(loadKg = Loads.loadFor(set, exercise, bodyweight, null)) }
                    },
                )
            }
        }
        plan.sessions.size
    }

    suspend fun remove() = db.demoDao().deleteAllDemo()

    private fun newId() = UUID.randomUUID().toString()
}

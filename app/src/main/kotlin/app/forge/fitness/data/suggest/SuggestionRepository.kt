package app.forge.fitness.data.suggest

import app.forge.domain.activity.ActivityFatigue
import app.forge.domain.activity.Sport
import app.forge.domain.calc.OneRepMax
import app.forge.domain.model.Equipment
import app.forge.domain.model.LogType
import app.forge.domain.suggest.DeloadAdvisor
import app.forge.domain.suggest.DeloadHint
import app.forge.domain.suggest.DeloadInput
import app.forge.domain.suggest.ExerciseTrend
import app.forge.domain.suggest.MuscleStatus
import app.forge.domain.suggest.MuscleWork
import app.forge.domain.suggest.PastSession
import app.forge.domain.suggest.ProgressionEngine
import app.forge.domain.suggest.ProgressionInput
import app.forge.domain.suggest.QuickPlan
import app.forge.domain.suggest.Recovery
import app.forge.domain.suggest.SessionPoint
import app.forge.domain.suggest.Suggestion
import app.forge.domain.suggest.TrainToday
import app.forge.domain.suggest.WorkSet
import app.forge.fitness.data.db.ActivityDao
import app.forge.fitness.data.db.ExerciseDao
import app.forge.fitness.data.db.ExerciseEntity
import app.forge.fitness.data.db.WorkoutDao
import app.forge.fitness.data.prefs.UserPreferences
import app.forge.fitness.data.prefs.UserPreferencesRepository
import app.forge.fitness.data.workout.PlannedExercise
import app.forge.fitness.data.workout.WorkoutRepository
import app.forge.fitness.di.TimeSource
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** Turns your logged history into the inputs the suggestion engines need. */
@Singleton
class SuggestionRepository @Inject constructor(
    private val workouts: WorkoutDao,
    private val exercises: ExerciseDao,
    private val workoutRepository: WorkoutRepository,
    private val preferences: UserPreferencesRepository,
    private val time: TimeSource,
    private val activities: ActivityDao,
) {
    private val day = 24 * 3_600_000L

    // ---- Per-exercise progression -------------------------------------------------------

    /** What to aim for on [exercise] next, based on its finished workouts. */
    suspend fun suggestionFor(
        exercise: ExerciseEntity,
        targetMin: Int?,
        targetMax: Int?,
        targetRpe: Double?,
        prefs: UserPreferences,
    ): Suggestion? {
        val history = workouts.exerciseHistory(exercise.id)
            .groupBy { it.sessionId }
            .values
            .sortedByDescending { it.first().startedAt }
            .take(HISTORY_SESSIONS)
            .map { rows -> PastSession(rows.map { WorkSet(it.set.weightKg, it.set.reps, it.set.durationSeconds, it.set.rpe) }) }
        val harder = exercise.progressionChain?.let { chain ->
            exercise.progressionStep?.let { step -> exercises.getChainStep(chain, step + 1)?.name }
        }
        val bodyweight = exercise.bodyweightProfile != null || exercise.logType == LogType.REPS
        val owned = if (bodyweight) {
            (prefs.weightsFor(Equipment.WEIGHTED_VEST) + prefs.weightsFor(Equipment.WEIGHTED_BAG)).distinct()
        } else {
            prefs.weightsFor(exercise.equipment)
        }
        return ProgressionEngine.suggest(
            ProgressionInput(
                logType = exercise.logType,
                history = history,
                targetMin = targetMin,
                targetMax = targetMax,
                targetRpe = targetRpe,
                equipment = exercise.equipment,
                ownedWeights = owned,
                harderVariation = harder,
            ),
        )
    }

    // ---- Recovery ---------------------------------------------------------------------

    /**
     * How recovered each muscle is now, and its sets this week. Updates as you log.
     * Sports and cardio count too (a football game tires your legs like a few sets).
     */
    fun observeMuscleStatus(): Flow<List<MuscleStatus>> {
        val since = time.now() - RECOVERY_WINDOW_DAYS * day
        return combine(workouts.observeRecentWorkSets(since), activities.observeSince(since)) { rows, sessions ->
            val lifting = rows.map { MuscleWork(it.completedAt, it.primaryMuscles, it.secondaryMuscles) }
            val other = sessions.mapNotNull {
                ActivityFatigue.muscleWork(Sport.fromKey(it.sport), it.startedAt, it.durationMinutes, it.intensity)
            }
            Recovery.status(lifting + other, time.now())
        }
    }

    // ---- Quick workout ------------------------------------------------------------------

    /**
     * Turns a [QuickPlan] into a real workout: for each muscle, the exercise you use
     * most for it (with equipment you own), else Forge's go-to home exercise.
     */
    suspend fun startQuickWorkout(plan: QuickPlan, minutes: Int): String? {
        val prefs = preferences.preferences.first()
        val owned = prefs.equipment + Equipment.BODY_ONLY
        val all = exercises.observeAll().first().filter {
            (it.equipment == null || it.equipment in owned) &&
                (it.logType == LogType.WEIGHT_REPS || it.logType == LogType.REPS || it.logType == LogType.DURATION)
        }
        val usage = exercises.observeUsage().first().associate { it.exerciseId to it.timesUsed }
        val bySource = all.associateBy { it.sourceId }
        val used = mutableSetOf<String>()
        val items = plan.slots.mapNotNull { slot ->
            val favourite = all.filter { slot.muscle in it.primaryMuscles && it.id !in used && (usage[it.id] ?: 0) > 0 }
                .maxByOrNull { usage[it.id] ?: 0 }
            val default = TrainToday.DEFAULT_EXERCISES[slot.muscle].orEmpty()
                .mapNotNull { bySource[it] }.firstOrNull { it.id !in used }
            val exercise = favourite ?: default ?: return@mapNotNull null
            used += exercise.id
            val timed = exercise.logType == LogType.DURATION
            PlannedExercise(
                exerciseId = exercise.id,
                sets = slot.sets,
                targetMin = if (timed) 30 else 8,
                targetMax = if (timed) 45 else 12,
                restSeconds = slot.restSeconds,
                supersetGroup = slot.supersetGroup,
            )
        }
        if (items.isEmpty()) return null
        return workoutRepository.startPlanned("Quick $minutes-min workout", fixSupersets(items))
    }

    /** If an exercise was skipped, a superset can be left with one member: unlink it. */
    private fun fixSupersets(items: List<PlannedExercise>): List<PlannedExercise> {
        val counts = items.mapNotNull { it.supersetGroup }.groupingBy { it }.eachCount()
        return items.map { if (it.supersetGroup != null && counts[it.supersetGroup] == 1) it.copy(supersetGroup = null) else it }
    }

    // ---- Deload -----------------------------------------------------------------------

    fun observeDeloadHint(): Flow<DeloadHint?> {
        val since = time.now() - TREND_WINDOW_DAYS * day
        val zone = ZoneId.systemDefault()
        fun date(ms: Long) = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()
        return combine(
            workouts.observeSessionPoints(since),
            workouts.observeTrendRows(since),
            preferences.preferences,
        ) { points, rows, prefs ->
            val trends = rows.groupBy { it.exerciseId }.mapNotNull { (_, sets) ->
                val perSession = sets.groupBy { it.sessionId }.values
                    .sortedByDescending { it.first().startedAt }
                    .map { s -> s.mapNotNull { r -> (r.loadKg ?: r.weightKg)?.let { w -> OneRepMax.estimate(w, r.reps!!, r.rpe) } }.maxOrNull() }
                    .filterNotNull()
                if (perSession.size < 6) null else ExerciseTrend(sets.first().exerciseName, perSession)
            }
            DeloadAdvisor.check(
                DeloadInput(
                    sessions = points.map { SessionPoint(date(it.startedAt), it.avgRpe) },
                    trends = trends,
                    today = LocalDate.now(),
                    dismissedUntil = prefs.deloadDismissedUntilEpochDay?.let(LocalDate::ofEpochDay),
                ),
            )
        }
    }

    suspend fun dismissDeload() = preferences.dismissDeloadUntil(LocalDate.now().plusDays(DISMISS_DAYS).toEpochDay())

    private companion object {
        const val HISTORY_SESSIONS = 4
        const val RECOVERY_WINDOW_DAYS = 14L
        const val TREND_WINDOW_DAYS = 84L
        const val DISMISS_DAYS = 7L
    }
}

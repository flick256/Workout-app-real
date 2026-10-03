package app.forge.fitness.data.analytics

import app.forge.domain.analytics.Activity
import app.forge.domain.analytics.HeatCell
import app.forge.domain.analytics.PersonalRecords
import app.forge.domain.analytics.Record
import app.forge.domain.analytics.SetPoint
import app.forge.domain.model.BodyMetricKind
import app.forge.domain.model.LogType
import app.forge.domain.suggest.MuscleStatus
import app.forge.fitness.data.db.ActivityDao
import app.forge.fitness.data.db.BodyMetricDao
import app.forge.fitness.data.db.BodyMetricEntity
import app.forge.fitness.data.db.ExerciseDao
import app.forge.fitness.data.db.ExerciseEntity
import app.forge.fitness.data.db.ExerciseHistorySet
import app.forge.fitness.data.db.TrendRow
import app.forge.fitness.data.db.WorkoutDao
import app.forge.fitness.data.suggest.SuggestionRepository
import app.forge.fitness.di.TimeSource
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

data class PrItem(val exerciseId: String, val exerciseName: String, val record: Record)

data class TopExercise(val id: String, val name: String, val timesUsed: Int)

data class Overview(
    val loading: Boolean = true,
    val workouts30: Int = 0,
    val weekStreak: Int = 0,
    val volumeThisWeek: Double = 0.0,
    val volumeLastWeek: Double = 0.0,
    val heatmap: List<HeatCell> = emptyList(),
    val muscles: List<MuscleStatus> = emptyList(),
    val recentPrs: List<PrItem> = emptyList(),
    val topExercises: List<TopExercise> = emptyList(),
)

/** What a strength chart plots for an exercise. */
enum class StrengthMetric(val label: String, val unit: String) {
    E1RM("Estimated 1-rep max", "kg"),
    REPS("Best set (reps)", "reps"),
    HOLD("Longest hold", "s"),
}

data class ExerciseProgress(
    val exercise: ExerciseEntity,
    val metric: StrengthMetric,
    /** (time, value) oldest first, one point per workout. */
    val series: List<Pair<Long, Double>>,
    val records: Map<app.forge.domain.analytics.RecordKind, Record>,
    val sessions: List<Pair<Long, List<app.forge.fitness.data.db.SetEntryEntity>>>,
)

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class AnalyticsRepository @Inject constructor(
    private val workouts: WorkoutDao,
    private val exercises: ExerciseDao,
    private val bodyMetrics: BodyMetricDao,
    private val suggestions: SuggestionRepository,
    private val activities: ActivityDao,
    private val time: TimeSource,
) {
    private val zone: ZoneId get() = ZoneId.systemDefault()
    private fun date(ms: Long): LocalDate = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()

    fun observeOverview(): Flow<Overview> {
        val heatWeeks = HEATMAP_WEEKS
        return combine(
            combine(workouts.observeHistory(), activities.observeAll()) { h, a -> h to a },
            workouts.observeTrendRows(0),
            suggestions.observeMuscleStatus(),
            exercises.observeUsage(),
            exercises.observeAll(),
        ) { (history, sports), trendRows, muscles, usage, all ->
            val today = date(time.now())
            val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            val perDay = history.groupBy { date(it.startedAt) }.mapValues { (_, s) -> s.size to s.sumOf { it.volumeKg } }
            // Sports, runs and cardio count as training days too.
            val sportDays = sports.groupingBy { date(it.startedAt) }.eachCount()
            val names = all.associate { it.id to it.name }
            val recentPrs = recentPrs(trendRows, today.minusDays(RECENT_PR_DAYS))
            Overview(
                loading = false,
                workouts30 = history.count { !date(it.startedAt).isBefore(today.minusDays(29)) },
                weekStreak = Activity.weekStreak(perDay.keys + sportDays.keys, today),
                volumeThisWeek = history.filter { !date(it.startedAt).isBefore(monday) }.sumOf { it.volumeKg },
                volumeLastWeek = history.filter { date(it.startedAt).let { d -> !d.isBefore(monday.minusWeeks(1)) && d.isBefore(monday) } }
                    .sumOf { it.volumeKg },
                heatmap = Activity.heatmap(perDay, today, heatWeeks, sportDays),
                muscles = muscles,
                recentPrs = recentPrs,
                topExercises = usage.sortedByDescending { it.timesUsed }.take(TOP_EXERCISES)
                    .mapNotNull { u -> names[u.exerciseId]?.let { TopExercise(u.exerciseId, it, u.timesUsed) } },
            )
        }
    }

    /** PRs set in workouts since [since], newest first (only "beat an earlier workout" ones). */
    private fun recentPrs(rows: List<TrendRow>, since: LocalDate): List<PrItem> =
        rows.groupBy { it.exerciseId }.flatMap { (exerciseId, sets) ->
            val points = sets.map { SetPoint(it.sessionId, it.startedAt, it.loadKg ?: it.weightKg, it.reps, it.rpe) }
            val name = sets.first().exerciseName
            sets.filter { !date(it.startedAt).isBefore(since) }.map { it.sessionId }.distinct().flatMap { sessionId ->
                PersonalRecords.newInSession(points, sessionId)
                    .filter { it.kind == app.forge.domain.analytics.RecordKind.E1RM || it.kind == app.forge.domain.analytics.RecordKind.HEAVIEST || it.kind == app.forge.domain.analytics.RecordKind.MOST_REPS }
                    .map { PrItem(exerciseId, name, it) }
            }
        }.sortedByDescending { it.record.atMillis }

    /** New records set in one workout (for the "workout complete" screen). */
    suspend fun prsInSession(sessionId: String): List<PrItem> {
        val exerciseIds = workouts.getSessionExercises(sessionId).map { it.exerciseId }.distinct()
        return exerciseIds.flatMap { id ->
            val exercise = exercises.getById(id) ?: return@flatMap emptyList()
            val points = workouts.exerciseHistory(id).map(::toPoint)
            PersonalRecords.newInSession(points, sessionId).map { PrItem(id, exercise.name, it) }
        }
    }

    fun observeExerciseProgress(exerciseId: String): Flow<ExerciseProgress?> =
        exercises.observeById(exerciseId).flatMapLatest { exercise ->
            if (exercise == null) flowOf(null)
            else workouts.observeExerciseHistory(exerciseId).map { rows -> progressOf(exercise, rows) }
        }

    private fun progressOf(exercise: ExerciseEntity, rows: List<ExerciseHistorySet>): ExerciseProgress {
        val points = rows.map(::toPoint)
        val hasLoad = points.any { (it.loadKg ?: 0.0) > 0 && it.reps != null }
        val metric = when {
            exercise.logType == LogType.DURATION -> StrengthMetric.HOLD
            hasLoad -> StrengthMetric.E1RM
            else -> StrengthMetric.REPS
        }
        val bySession = rows.groupBy { it.sessionId }
        val series = when (metric) {
            StrengthMetric.E1RM -> PersonalRecords.e1rmSeries(points)
            StrengthMetric.REPS -> bySession.values.mapNotNull { s -> s.mapNotNull { it.set.reps }.maxOrNull()?.let { s.first().startedAt to it.toDouble() } }
            StrengthMetric.HOLD -> bySession.values.mapNotNull { s -> s.mapNotNull { it.set.durationSeconds }.maxOrNull()?.let { s.first().startedAt to it.toDouble() } }
        }.sortedBy { it.first }
        return ExerciseProgress(
            exercise = exercise,
            metric = metric,
            series = series,
            records = PersonalRecords.best(points),
            sessions = bySession.values.map { s -> s.first().startedAt to s.map { it.set } }.sortedByDescending { it.first },
        )
    }

    private fun toPoint(row: ExerciseHistorySet) = SetPoint(
        sessionId = row.sessionId,
        atMillis = row.startedAt,
        loadKg = row.set.loadKg ?: row.set.weightKg,
        reps = row.set.reps,
        rpe = row.set.rpe,
        seconds = row.set.durationSeconds,
    )

    // ---- Body ---------------------------------------------------------------------------

    fun observeBodyMetrics(): Flow<List<BodyMetricEntity>> = bodyMetrics.observeEverything()

    suspend fun logMeasurement(kind: BodyMetricKind, value: Double) {
        val now = time.now()
        bodyMetrics.insert(BodyMetricEntity(UUID.randomUUID().toString(), kind, value, now, createdAt = now, updatedAt = now))
    }

    suspend fun deleteMeasurement(id: String) {
        val m = bodyMetrics.get(id) ?: return
        bodyMetrics.update(m.copy(deletedAt = time.now(), updatedAt = time.now()))
    }

    suspend fun restoreMeasurement(id: String) {
        val m = bodyMetrics.get(id) ?: return
        bodyMetrics.update(m.copy(deletedAt = null, updatedAt = time.now()))
    }

    private companion object {
        const val HEATMAP_WEEKS = 17
        const val RECENT_PR_DAYS = 30L
        const val TOP_EXERCISES = 8
    }
}

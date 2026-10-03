package app.forge.fitness.data.insights

import app.forge.domain.analytics.PersonalRecords
import app.forge.domain.analytics.RecordKind
import app.forge.domain.analytics.SetPoint
import app.forge.domain.calc.OneRepMax
import app.forge.domain.calc.Units
import app.forge.domain.insights.LiftSession
import app.forge.domain.insights.PrLine
import app.forge.domain.insights.StallAnalyzer
import app.forge.domain.insights.StallInput
import app.forge.domain.insights.StallReport
import app.forge.domain.insights.WeeklyFacts
import app.forge.domain.suggest.TrainToday
import app.forge.fitness.data.db.ActivityDao
import app.forge.fitness.data.db.ExerciseDao
import app.forge.fitness.data.db.FoodDao
import app.forge.fitness.data.db.WorkoutDao
import app.forge.fitness.data.goals.GoalsRepository
import app.forge.fitness.data.nutrition.FoodRepository
import app.forge.fitness.data.suggest.SuggestionRepository
import app.forge.fitness.di.TimeSource
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.first

/** Gathers the facts that summaries and plateau checks are built from. */
@Singleton
class InsightsRepository @Inject constructor(
    private val workouts: WorkoutDao,
    private val exercises: ExerciseDao,
    private val activities: ActivityDao,
    private val foodDao: FoodDao,
    private val foods: FoodRepository,
    private val goals: GoalsRepository,
    private val suggestions: SuggestionRepository,
    private val time: TimeSource,
) {
    private val zone: ZoneId get() = ZoneId.systemDefault()
    private fun date(ms: Long): LocalDate = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()

    /** The last 7 days (today included) against the 7 before. */
    suspend fun weeklyFacts(): WeeklyFacts {
        val today = date(time.now())
        val from = today.minusDays(6)
        val prevFrom = from.minusDays(7)
        fun inWeek(d: LocalDate) = !d.isBefore(from) && !d.isAfter(today)
        fun inPrev(d: LocalDate) = !d.isBefore(prevFrom) && d.isBefore(from)

        val history = workouts.observeHistory().first()
        val week = history.filter { inWeek(date(it.startedAt)) }
        val prev = history.filter { inPrev(date(it.startedAt)) }

        val trend = workouts.observeTrendRows(prevFrom.minusDays(365).atStartOfDay(zone).toInstant().toEpochMilli()).first()
        val prs = trend.groupBy { it.exerciseId }.flatMap { (_, rows) ->
            val points = rows.map { SetPoint(it.sessionId, it.startedAt, it.loadKg ?: it.weightKg, it.reps, it.rpe) }
            rows.filter { inWeek(date(it.startedAt)) }.map { it.sessionId }.distinct().flatMap { session ->
                PersonalRecords.newInSession(points, session).filter { it.kind == RecordKind.E1RM || it.kind == RecordKind.MOST_REPS }
                    .map { r ->
                        PrLine(
                            rows.first().exerciseName,
                            if (r.kind == RecordKind.MOST_REPS) "${r.value.roundToInt()} reps" else "estimated 1RM ${Units.format(r.value)} kg",
                        )
                    }
            }
        }.distinctBy { it.exercise }

        val acts = activities.observeAll().first().filter { inWeek(date(it.startedAt)) }
        val muscles = suggestions.observeMuscleStatus().first()
        val undertrained = if (week.isEmpty()) emptyList() else muscles
            .filter { it.muscle in TrainToday.TRAINABLE && it.weeklySets < it.weeklyTarget * 0.5 }
            .map { it.muscle.label }

        val daily = activities.observeDaily(from.toEpochDay()).first()
        val sleep = daily.mapNotNull { it.sleepMinutes }.takeIf { it.isNotEmpty() }?.average()?.div(60)
        val steps = daily.mapNotNull { it.steps }.takeIf { it.isNotEmpty() }?.average()?.roundToInt()

        val food = foodDao.observeRange(from.toEpochDay(), today.toEpochDay()).first().groupBy { it.epochDay }
        val targets = foods.observeTargets().first().targetsOrNull

        val overview = goals.observeOverview().first()
        val habitDays = overview.habits.flatMap { it.week }.filterNotNull()
        val habitPercent = if (habitDays.isEmpty()) null else (habitDays.count { it } * 100.0 / habitDays.size).roundToInt()

        return WeeklyFacts(
            workouts = week.size,
            workoutsLastWeek = prev.size,
            sets = week.sumOf { it.setCount },
            volumeKg = week.sumOf { it.volumeKg },
            volumeLastWeekKg = prev.sumOf { it.volumeKg },
            prs = prs,
            activities = acts.size,
            activityMinutes = acts.sumOf { it.durationMinutes },
            undertrained = undertrained,
            avgSleepHours = sleep,
            avgSteps = steps,
            foodDaysLogged = food.size,
            avgKcal = food.values.map { day -> day.sumOf { it.kcal } }.takeIf { it.isNotEmpty() }?.average()?.roundToInt(),
            kcalTarget = targets?.kcal,
            avgProteinG = food.values.map { day -> day.sumOf { it.proteinG } }.takeIf { it.isNotEmpty() }?.average()?.roundToInt(),
            proteinTargetG = targets?.proteinG,
            habitPercent = habitPercent,
            goalLines = overview.goals.map { "${it.title}: ${it.detail}" },
        )
    }

    /** "Why has this stalled?" for one exercise, from its history and your recovery data. */
    suspend fun stallReport(exerciseId: String): StallReport? {
        val exercise = exercises.getById(exerciseId) ?: return null
        val today = date(time.now())
        val sessions = workouts.exerciseHistory(exerciseId).groupBy { it.sessionId }.values.mapNotNull { rows ->
            val e1rm = rows.mapNotNull { r ->
                (r.set.loadKg ?: r.set.weightKg)?.takeIf { it > 0 }?.let { w -> r.set.reps?.let { OneRepMax.estimate(w, it, r.set.rpe) } }
            }.maxOrNull() ?: return@mapNotNull null
            LiftSession(date(rows.first().startedAt).toEpochDay(), e1rm, rows.mapNotNull { it.set.rpe }.takeIf { it.isNotEmpty() }?.average())
        }.sortedBy { it.epochDay }

        val muscle = exercise.primaryMuscles.firstOrNull()
        val status = muscle?.let { m -> suggestions.observeMuscleStatus().first().firstOrNull { it.muscle == m } }
        val daily = activities.observeDaily(today.minusDays(13).toEpochDay()).first()
        val food = foodDao.observeRange(today.minusDays(13).toEpochDay(), today.toEpochDay()).first().groupBy { it.epochDay }
        return StallAnalyzer.analyze(
            StallInput(
                exerciseName = exercise.name,
                sessions = sessions,
                todayEpochDay = today.toEpochDay(),
                muscleName = muscle?.label,
                weeklySets = status?.weeklySets,
                weeklyTarget = status?.weeklyTarget,
                avgSleepHours = daily.mapNotNull { it.sleepMinutes }.takeIf { it.size >= 3 }?.average()?.div(60),
                avgProteinG = food.values.map { d -> d.sumOf { it.proteinG } }.takeIf { it.size >= 3 }?.average(),
                proteinTargetG = foods.observeTargets().first().targetsOrNull?.proteinG,
            ),
        )
    }
}

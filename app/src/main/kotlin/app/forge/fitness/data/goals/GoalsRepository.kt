package app.forge.fitness.data.goals

import app.forge.domain.analytics.Activity
import app.forge.domain.analytics.PersonalRecords
import app.forge.domain.analytics.RecordKind
import app.forge.domain.analytics.SetPoint
import app.forge.domain.calc.OneRepMax
import app.forge.domain.goals.AchievementStats
import app.forge.domain.goals.AutoHabits
import app.forge.domain.goals.DayFacts
import app.forge.domain.goals.DayMask
import app.forge.domain.goals.GoalKind
import app.forge.domain.goals.GoalMath
import app.forge.domain.goals.GoalProgress
import app.forge.domain.goals.HabitKind
import app.forge.domain.goals.HabitMath
import app.forge.domain.model.BodyMetricKind
import app.forge.domain.nutrition.DailyTargets
import app.forge.fitness.data.db.ActivityDao
import app.forge.fitness.data.db.ActivitySessionEntity
import app.forge.fitness.data.db.BodyMetricDao
import app.forge.fitness.data.db.DailyHealthEntity
import app.forge.fitness.data.db.ExerciseDao
import app.forge.fitness.data.db.FoodDao
import app.forge.fitness.data.db.FoodLogEntity
import app.forge.fitness.data.db.GoalDao
import app.forge.fitness.data.db.GoalEntity
import app.forge.fitness.data.db.HabitCheckEntity
import app.forge.fitness.data.db.HabitEntity
import app.forge.fitness.data.db.SessionSummaryRow
import app.forge.fitness.data.db.TrendRow
import app.forge.fitness.data.db.WorkoutDao
import app.forge.fitness.data.nutrition.FoodRepository
import app.forge.fitness.data.ticker
import app.forge.fitness.di.TimeSource
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

data class GoalView(
    val goal: GoalEntity,
    val kind: GoalKind,
    val title: String,
    val progress: GoalProgress,
    /** "3 / 4 this week", "72.4 → 70 kg", "e1RM 82 / 100 kg". */
    val detail: String,
)

data class HabitView(
    val habit: HabitEntity,
    val kind: HabitKind,
    val due: DayMask,
    val isDueToday: Boolean,
    /** null = auto habit with no data yet today. */
    val doneToday: Boolean?,
    val streak: Int,
    val bestStreak: Int,
    /** Last 7 days, oldest first: true done, false missed, null not due / no data. */
    val week: List<Boolean?>,
    val completion30: Double?,
)

data class GoalsOverview(
    val loaded: Boolean = false,
    val goals: List<GoalView> = emptyList(),
    val habits: List<HabitView> = emptyList(),
    val stats: AchievementStats = AchievementStats(),
)

/** Everything a habit or achievement can be judged on. */
private data class Inputs(
    val sessions: List<SessionSummaryRow>,
    val activities: List<ActivitySessionEntity>,
    val food: List<FoodLogEntity>,
    val daily: List<DailyHealthEntity>,
    val targets: DailyTargets?,
)

@Singleton
class GoalsRepository @Inject constructor(
    private val dao: GoalDao,
    private val workouts: WorkoutDao,
    private val exercises: ExerciseDao,
    private val activities: ActivityDao,
    private val foodDao: FoodDao,
    private val bodyMetrics: BodyMetricDao,
    private val foods: FoodRepository,
    private val time: TimeSource,
) {
    private val zone: ZoneId get() = ZoneId.systemDefault()
    private fun date(ms: Long): LocalDate = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()
    private fun today() = date(time.now())

    fun observeOverview(): Flow<GoalsOverview> {
        val from = today().minusDays(HISTORY_DAYS)
        val inputs = combine(
            workouts.observeHistory(),
            activities.observeAll(),
            foodDao.observeRange(from.toEpochDay(), Long.MAX_VALUE / 2),
            activities.observeDaily(from.toEpochDay()),
            foods.observeTargets().map { it.targetsOrNull },
        ) { s, a, f, d, t -> Inputs(s, a, f, d, t) }
        val habitData = combine(dao.observeHabits(), dao.observeChecks(from.toEpochDay()), ::Pair)
        val goalData = combine(
            dao.observeGoals(),
            bodyMetrics.observeLatest(BodyMetricKind.WEIGHT),
            workouts.observeTrendRows(0),
            exercises.observeAll().map { all -> all.associate { it.id to it.name } },
            foodDao.observeDaysLogged(),
        ) { goals, weight, trend, names, foodDays -> GoalData(goals, weight?.value, trend, names, foodDays) }
        // The ticker rolls "today" over at midnight while the screen is open.
        return combine(inputs, habitData, goalData, ticker().map { today() }.distinctUntilChanged()) { input, (habits, checks), g, today ->
            val facts = dayFacts(input, today)
            val habitViews = habits.map { habitView(it, checks, facts, today) }
            val goalViews = g.goals.map { goalView(it, input, g, today) }
            GoalsOverview(
                loaded = true,
                goals = goalViews,
                habits = habitViews,
                stats = stats(input, g, habitViews),
            )
        }
    }

    private data class GoalData(
        val goals: List<GoalEntity>,
        val weightKg: Double?,
        val trend: List<TrendRow>,
        val names: Map<String, String>,
        val foodDays: Int,
    )

    // ---- Facts per day -----------------------------------------------------------------

    private fun dayFacts(input: Inputs, today: LocalDate): Map<LocalDate, DayFacts> {
        val trained = input.sessions.map { date(it.startedAt) }.toSet() + input.activities.map { date(it.startedAt) }
        val foodByDay = input.food.groupBy { LocalDate.ofEpochDay(it.epochDay) }
        val dailyByDay = input.daily.associateBy { LocalDate.ofEpochDay(it.epochDay) }
        val out = mutableMapOf<LocalDate, DayFacts>()
        var day = today.minusDays(HISTORY_DAYS)
        while (!day.isAfter(today)) {
            val food = foodByDay[day].orEmpty()
            val health = dailyByDay[day]
            out[day] = DayFacts(
                trained = day in trained,
                proteinG = if (food.isEmpty()) null else food.sumOf { it.proteinG },
                proteinTargetG = input.targets?.proteinG,
                foodEntries = food.size,
                steps = health?.steps,
                sleepMinutes = health?.sleepMinutes,
            )
            day = day.plusDays(1)
        }
        return out
    }

    // ---- Habits ------------------------------------------------------------------------

    private fun habitView(habit: HabitEntity, checks: List<HabitCheckEntity>, facts: Map<LocalDate, DayFacts>, today: LocalDate): HabitView {
        val kind = HabitKind.entries.firstOrNull { it.name == habit.kind } ?: HabitKind.CUSTOM
        val due = DayMask(habit.dayMask)
        val status: (LocalDate) -> Boolean? = if (kind == HabitKind.CUSTOM) {
            val ticked = checks.filter { it.habitId == habit.id }.map { LocalDate.ofEpochDay(it.epochDay) }.toSet()
            { d -> d in ticked }
        } else {
            { d -> facts[d]?.let { AutoHabits.isDone(kind, habit.target, it) } }
        }
        val done = facts.keys.filter { status(it) == true }.toSet()
        // A habit can't have been missed before it existed.
        val created = date(habit.createdAt)
        val week = (6 downTo 0).map { back ->
            val d = today.minusDays(back.toLong())
            // Not done *yet* today isn't a miss; auto habits without data stay unknown.
            if (!due.isDue(d) || d.isBefore(created)) null else status(d).takeUnless { it == false && d == today }
        }
        val since = maxOf(today.minusDays(29), created)
        return HabitView(
            habit = habit,
            kind = kind,
            due = due,
            isDueToday = due.isDue(today),
            doneToday = status(today),
            streak = HabitMath.currentStreak(done, today, due),
            bestStreak = HabitMath.bestStreak(done, due),
            week = week,
            completion30 = HabitMath.completion(done, since, today, due),
        )
    }

    suspend fun setChecked(habitId: String, day: LocalDate, checked: Boolean) {
        if (checked) dao.check(HabitCheckEntity(habitId, day.toEpochDay(), time.now()))
        else dao.uncheck(habitId, day.toEpochDay())
    }

    suspend fun isCheckedToday(habitId: String): Boolean = dao.isChecked(habitId, today().toEpochDay()) > 0

    suspend fun toggleToday(habitId: String) {
        val day = today().toEpochDay()
        setChecked(habitId, today(), dao.isChecked(habitId, day) == 0)
    }

    suspend fun saveHabit(id: String?, kind: HabitKind, name: String, target: Double?, due: DayMask, reminderMinutes: Int?): String {
        val now = time.now()
        val existing = id?.let { dao.getHabit(it) }
        if (existing != null) {
            dao.updateHabit(
                existing.copy(
                    kind = kind.name, name = name.ifBlank { kind.label }, target = target, dayMask = due.bits,
                    reminderMinutes = reminderMinutes, updatedAt = now,
                ),
            )
            return existing.id
        }
        val position = dao.getHabits().maxOfOrNull { it.position + 1 } ?: 0
        val habit = HabitEntity(
            id = UUID.randomUUID().toString(), kind = kind.name, name = name.ifBlank { kind.label }, target = target,
            dayMask = due.bits, reminderMinutes = reminderMinutes, position = position, createdAt = now, updatedAt = now,
        )
        dao.insertHabit(habit)
        return habit.id
    }

    suspend fun getHabit(id: String): HabitEntity? = dao.getHabit(id)

    suspend fun deleteHabit(id: String) {
        val h = dao.getHabit(id) ?: return
        dao.updateHabit(h.copy(deletedAt = time.now(), updatedAt = time.now()))
    }

    suspend fun restoreHabit(id: String) {
        val h = dao.getHabit(id) ?: return
        dao.updateHabit(h.copy(deletedAt = null, updatedAt = time.now()))
    }

    suspend fun habitsWithReminders(): List<HabitEntity> = dao.getHabits().filter { it.reminderMinutes != null }

    // ---- Goals -------------------------------------------------------------------------

    private fun goalView(goal: GoalEntity, input: Inputs, g: GoalData, today: LocalDate): GoalView {
        val kind = GoalKind.entries.firstOrNull { it.name == goal.kind } ?: GoalKind.WORKOUTS_PER_WEEK
        val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val exerciseName = goal.exerciseId?.let { g.names[it] } ?: "exercise"
        fun fmt(v: Double) = app.forge.domain.calc.Units.format(v)
        return when (kind) {
            GoalKind.WORKOUTS_PER_WEEK -> {
                val count = input.sessions.count { !date(it.startedAt).isBefore(monday) }
                val p = GoalMath.weekly(count, goal.target.toInt())
                GoalView(goal, kind, "Train ${goal.target.toInt()}× a week", p, "$count / ${goal.target.toInt()} this week")
            }
            GoalKind.PROTEIN_DAYS -> {
                val target = input.targets?.proteinG
                val days = if (target == null) 0 else input.food.filter { !LocalDate.ofEpochDay(it.epochDay).isBefore(monday) }
                    .groupBy { it.epochDay }.count { (_, e) -> e.sumOf { it.proteinG } >= target * 0.95 }
                val p = GoalMath.weekly(days, goal.target.toInt())
                GoalView(
                    goal, kind, "Hit protein ${goal.target.toInt()} days a week", p,
                    if (target == null) "Set your nutrition targets first" else "$days / ${goal.target.toInt()} this week",
                )
            }
            GoalKind.BODYWEIGHT -> {
                val p = GoalMath.bodyweight(goal.startValue ?: g.weightKg ?: goal.target, g.weightKg, goal.target)
                GoalView(
                    goal, kind, "Reach ${fmt(goal.target)} kg bodyweight", p,
                    g.weightKg?.let { "Now ${fmt(it)} kg (started ${fmt(goal.startValue ?: it)} kg)" } ?: "Log your weight to track this",
                )
            }
            GoalKind.LIFT -> {
                val best = g.trend.filter { it.exerciseId == goal.exerciseId }
                    .mapNotNull { r -> (r.loadKg ?: r.weightKg)?.takeIf { it > 0 }?.let { w -> r.reps?.let { OneRepMax.estimate(w, it, r.rpe) } } }
                    .maxOrNull()
                val p = GoalMath.best(best, goal.target)
                GoalView(goal, kind, "$exerciseName: ${fmt(goal.target)} kg", p, "Best estimated 1RM ${best?.let { fmt(it) } ?: "–"} kg")
            }
            GoalKind.REPS -> {
                val best = g.trend.filter { it.exerciseId == goal.exerciseId }.mapNotNull { it.reps }.maxOrNull()?.toDouble()
                val p = GoalMath.best(best, goal.target)
                GoalView(goal, kind, "$exerciseName: ${goal.target.toInt()} reps", p, "Best set ${best?.toInt() ?: "–"} reps")
            }
        }
    }

    suspend fun addGoal(kind: GoalKind, target: Double, exerciseId: String?, startValue: Double?): String {
        val now = time.now()
        val goal = GoalEntity(
            id = UUID.randomUUID().toString(), kind = kind.name, target = target, exerciseId = exerciseId,
            startValue = startValue, createdAt = now, updatedAt = now,
        )
        dao.insertGoal(goal)
        return goal.id
    }

    /** Records the moment a one-off goal is first reached (for the achievement). */
    suspend fun markReached(goalId: String) {
        val g = dao.getGoal(goalId) ?: return
        if (g.reachedAt == null) dao.updateGoal(g.copy(reachedAt = time.now(), updatedAt = time.now()))
    }

    suspend fun deleteGoal(id: String) {
        val g = dao.getGoal(id) ?: return
        dao.updateGoal(g.copy(deletedAt = time.now(), updatedAt = time.now()))
    }

    suspend fun restoreGoal(id: String) {
        val g = dao.getGoal(id) ?: return
        dao.updateGoal(g.copy(deletedAt = null, updatedAt = time.now()))
    }

    suspend fun latestWeightKg(): Double? = bodyMetrics.latest(BodyMetricKind.WEIGHT)?.value

    // ---- Achievements ------------------------------------------------------------------

    private fun stats(input: Inputs, g: GoalData, habits: List<HabitView>): AchievementStats {
        val points = g.trend.groupBy { it.exerciseId }.mapValues { (_, rows) ->
            rows.map { SetPoint(it.sessionId, it.startedAt, it.loadKg ?: it.weightKg, it.reps, it.rpe) }
        }
        val prs = points.values.sumOf { sets ->
            sets.map { it.sessionId }.distinct().sumOf { session ->
                PersonalRecords.newInSession(sets, session).count {
                    it.kind == RecordKind.E1RM || it.kind == RecordKind.HEAVIEST || it.kind == RecordKind.MOST_REPS
                }
            }
        }
        return AchievementStats(
            workouts = input.sessions.size,
            weekStreak = Activity.weekStreak(input.sessions.map { date(it.startedAt) }.toSet(), today()),
            prs = prs,
            bestSessionVolumeKg = input.sessions.maxOfOrNull { it.volumeKg } ?: 0.0,
            activities = input.activities.size,
            foodDaysLogged = g.foodDays,
            bestHabitStreak = habits.maxOfOrNull { it.bestStreak } ?: 0,
            goalsReached = g.goals.count { it.reachedAt != null },
        )
    }

    companion object {
        const val HISTORY_DAYS = 400L
    }
}

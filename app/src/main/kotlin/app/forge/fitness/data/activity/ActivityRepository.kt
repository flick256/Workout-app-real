package app.forge.fitness.data.activity

import app.forge.domain.activity.ActivityFatigue
import app.forge.domain.activity.Readiness
import app.forge.domain.activity.ReadinessCheck
import app.forge.domain.activity.ReadinessInput
import app.forge.domain.activity.Sport
import app.forge.fitness.data.db.ActivityDao
import app.forge.fitness.data.db.ActivitySessionEntity
import app.forge.fitness.data.db.ActivitySource
import app.forge.fitness.data.db.DailyHealthEntity
import app.forge.fitness.data.db.WorkoutDao
import app.forge.fitness.data.db.endedAt
import app.forge.fitness.di.TimeSource
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import app.forge.fitness.data.ticker

/** A session read from Health Connect, already reduced to what Forge needs. */
data class ImportedSession(
    /** Health Connect record ID. */
    val externalId: String,
    /** Null for strength training, which is matched to your Forge workouts instead. */
    val sport: Sport?,
    val title: String?,
    val start: Long,
    val end: Long,
    val avgHeartRate: Int? = null,
    val maxHeartRate: Int? = null,
    val distanceMeters: Double? = null,
    val calories: Double? = null,
)

data class ImportResult(val added: Int = 0, val updated: Int = 0, val workoutsWithHeartRate: Int = 0) {
    operator fun plus(other: ImportResult) =
        ImportResult(added + other.added, updated + other.updated, workoutsWithHeartRate + other.workoutsWithHeartRate)
}

/** Sports, cardio and mobility sessions, plus daily health data from your watch/strap. */
@Singleton
class ActivityRepository @Inject constructor(
    private val activities: ActivityDao,
    private val workouts: WorkoutDao,
    private val time: TimeSource,
) {
    fun observeActivities(): Flow<List<ActivitySessionEntity>> = activities.observeAll()

    fun observeSince(since: Long): Flow<List<ActivitySessionEntity>> = activities.observeSince(since)

    fun observe(id: String): Flow<ActivitySessionEntity?> = activities.observe(id)

    suspend fun get(id: String): ActivitySessionEntity? = activities.get(id)

    /** Saves a hand-logged activity (new when [id] is null) and returns its id. */
    suspend fun save(
        id: String?,
        sport: Sport,
        title: String?,
        startedAt: Long,
        durationMinutes: Int,
        intensity: Int,
        distanceMeters: Double?,
        notes: String?,
    ): String {
        val now = time.now()
        val existing = id?.let { activities.get(it) }
        return if (existing != null) {
            activities.update(
                existing.copy(
                    sport = sport.name,
                    title = title?.ifBlank { null },
                    startedAt = startedAt,
                    durationMinutes = durationMinutes.coerceAtLeast(1),
                    intensity = intensity.coerceIn(1, 10),
                    distanceMeters = distanceMeters,
                    notes = notes?.ifBlank { null },
                    updatedAt = now,
                ),
            )
            existing.id
        } else {
            val newId = UUID.randomUUID().toString()
            activities.insert(
                ActivitySessionEntity(
                    id = newId,
                    sport = sport.name,
                    title = title?.ifBlank { null },
                    startedAt = startedAt,
                    durationMinutes = durationMinutes.coerceAtLeast(1),
                    intensity = intensity.coerceIn(1, 10),
                    distanceMeters = distanceMeters,
                    notes = notes?.ifBlank { null },
                    createdAt = now,
                    updatedAt = now,
                ),
            )
            newId
        }
    }

    /** Hides the activity (undo-able). Imported ones stay hidden on later syncs. */
    suspend fun delete(id: String) = edit(id) { it.copy(deletedAt = time.now()) }

    suspend fun restore(id: String) = edit(id) { it.copy(deletedAt = null) }

    private suspend fun edit(id: String, change: (ActivitySessionEntity) -> ActivitySessionEntity) {
        val current = activities.get(id) ?: return
        activities.update(change(current).copy(updatedAt = time.now()))
    }

    // ---- Health Connect import ---------------------------------------------------------

    /**
     * Brings strap/watch sessions in without duplicates:
     *  - strength sessions that overlap a Forge workout just add their heart rate to it;
     *  - sessions already imported are refreshed (unless you deleted them);
     *  - a session that overlaps one you logged by hand is merged into yours;
     *  - everything else becomes a new activity.
     */
    suspend fun importSessions(sessions: List<ImportedSession>): ImportResult {
        var result = ImportResult()
        val now = time.now()
        for (s in sessions) {
            val minutes = ((s.end - s.start) / 60_000L).toInt()
            if (minutes < MIN_MINUTES) continue

            if (s.sport == null) {
                val workout = workouts.sessionsOverlapping(s.start - SLACK_MS, s.end + SLACK_MS)
                    .maxByOrNull { overlap(it.startedAt, it.endedAt ?: now, s.start, s.end) }
                if (workout != null) {
                    // Live heart rate recorded by Forge itself is more detailed: keep it.
                    if (s.avgHeartRate != null && workouts.liveHeartRateCount(workout.id) == 0 &&
                        (workout.avgHeartRate != s.avgHeartRate || workout.maxHeartRate != s.maxHeartRate)
                    ) {
                        workouts.setHeartRate(workout.id, s.avgHeartRate, s.maxHeartRate, now)
                        result += ImportResult(workoutsWithHeartRate = 1)
                    }
                    continue
                }
            }

            val existing = activities.getByExternalId(s.externalId)
            if (existing != null) {
                if (existing.deletedAt != null) continue
                val refreshed = existing.withStrapData(s)
                if (refreshed != existing) {
                    activities.update(refreshed.copy(updatedAt = now))
                    result += ImportResult(updated = 1)
                }
                continue
            }

            // Same sport (or an untyped strap session), mostly overlapping: the same thing.
            fun sameThing(a: ActivitySessionEntity) =
                (s.sport == null || a.sport == s.sport.name || a.sport == Sport.OTHER.name) &&
                    overlap(a.startedAt, a.endedAt, s.start, s.end) >= 0.5 * min(a.endedAt - a.startedAt, s.end - s.start)

            // Another app (e.g. a phone's auto-detect) may have recorded the same session.
            val twin = activities.importedOverlapping(s.start, s.end).filter(::sameThing)
                .maxByOrNull { overlap(it.startedAt, it.endedAt, s.start, s.end) }
            if (twin != null) {
                val merged = twin.withStrapData(s)
                if (merged != twin) {
                    activities.update(merged.copy(updatedAt = now))
                    result += ImportResult(updated = 1)
                }
                continue
            }

            val manual = activities.manualOverlapping(s.start, s.end).filter(::sameThing)
                .maxByOrNull { overlap(it.startedAt, it.endedAt, s.start, s.end) }
            if (manual != null) {
                activities.update(manual.withStrapData(s).copy(externalId = s.externalId, updatedAt = now))
                result += ImportResult(updated = 1)
                continue
            }

            val sport = s.sport ?: Sport.OTHER
            activities.insert(
                ActivitySessionEntity(
                    id = UUID.randomUUID().toString(),
                    sport = sport.name,
                    title = s.title ?: if (s.sport == null) "Strength training" else null,
                    startedAt = s.start,
                    durationMinutes = minutes,
                    intensity = s.avgHeartRate?.let(ActivityFatigue::intensityFromHeartRate) ?: sport.defaultIntensity,
                    distanceMeters = s.distanceMeters,
                    calories = s.calories,
                    avgHeartRate = s.avgHeartRate,
                    maxHeartRate = s.maxHeartRate,
                    source = ActivitySource.HEALTH_CONNECT.name,
                    externalId = s.externalId,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
            result += ImportResult(added = 1)
        }
        return result
    }

    /** Fills in strap numbers, keeping what's already there when a second source has gaps. */
    private fun ActivitySessionEntity.withStrapData(s: ImportedSession) = copy(
        avgHeartRate = s.avgHeartRate ?: avgHeartRate,
        maxHeartRate = s.maxHeartRate ?: maxHeartRate,
        distanceMeters = s.distanceMeters ?: distanceMeters,
        calories = s.calories ?: calories,
    )

    private fun overlap(aStart: Long, aEnd: Long, bStart: Long, bEnd: Long): Long =
        max(0L, min(aEnd, bEnd) - max(aStart, bStart))

    // ---- Daily health & readiness ------------------------------------------------------

    suspend fun saveDaily(days: List<DailyHealthEntity>) = activities.upsertDaily(days)

    fun observeDaily(fromDay: LocalDate): Flow<List<DailyHealthEntity>> = activities.observeDaily(fromDay.toEpochDay())

    /** Today's readiness from sleep, HRV and resting heart rate vs. your last few weeks. */
    fun observeReadiness(today: () -> LocalDate = LocalDate::now): Flow<Readiness> {
        val from = today().minusDays(BASELINE_DAYS)
        return combine(observeDaily(from), ticker()) { rows, _ -> readiness(rows, today().toEpochDay()) }
    }

    companion object {
        const val MIN_MINUTES = 3
        const val SLACK_MS = 15 * 60_000L
        const val BASELINE_DAYS = 28L

        /** Needs a few days of history before comparing today against "your normal". */
        private const val MIN_BASELINE_DAYS = 3

        fun readiness(rows: List<DailyHealthEntity>, todayEpochDay: Long): Readiness {
            val today = rows.firstOrNull { it.epochDay == todayEpochDay }
            val yesterday = rows.firstOrNull { it.epochDay == todayEpochDay - 1 }
            val history = rows.filter { it.epochDay < todayEpochDay }
            fun baseline(values: List<Double>) = values.takeIf { it.size >= MIN_BASELINE_DAYS }?.average()
            return ReadinessCheck.assess(
                ReadinessInput(
                    sleepMinutes = today?.sleepMinutes,
                    hrvMs = today?.hrvMs,
                    hrvBaselineMs = baseline(history.mapNotNull { it.hrvMs }),
                    // Some apps write resting HR at the end of the day, so fall back to yesterday's.
                    restingHr = today?.restingHr ?: yesterday?.restingHr,
                    restingHrBaseline = baseline(
                        history.filter { today?.restingHr != null || it.epochDay < todayEpochDay - 1 }
                            .mapNotNull { it.restingHr },
                    ),
                ),
            )
        }
    }
}

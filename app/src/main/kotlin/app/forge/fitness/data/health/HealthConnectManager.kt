package app.forge.fitness.data.health

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.result.contract.ActivityResultContract
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.aggregate.AggregateMetric
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import app.forge.fitness.data.activity.ActivityRepository
import app.forge.fitness.data.activity.ImportResult
import app.forge.fitness.data.activity.ImportedSession
import app.forge.fitness.data.db.DailyHealthEntity
import app.forge.fitness.data.db.HeartRateDao
import app.forge.fitness.data.db.WorkoutDao
import androidx.health.connect.client.records.metadata.Device
import androidx.health.connect.client.records.metadata.Metadata
import app.forge.fitness.data.prefs.UserPreferencesRepository
import app.forge.fitness.di.TimeSource
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt
import kotlin.reflect.KClass
import app.forge.fitness.di.ApplicationScope
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class HealthAvailability { AVAILABLE, NEEDS_UPDATE, NOT_SUPPORTED }

sealed interface SyncState {
    data object Idle : SyncState
    data object Syncing : SyncState
    data class Done(val result: ImportResult, val days: Int) : SyncState
    data class Failed(val message: String) : SyncState
}

/**
 * Reads what your watch or strap recorded (via its app, e.g. Zepp for Amazfit) from
 * Health Connect. Read-only: Forge never writes to or deletes from Health Connect, and
 * the data stays on your phone.
 */
@Singleton
class HealthConnectManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val activities: ActivityRepository,
    private val preferences: UserPreferencesRepository,
    private val time: TimeSource,
    @param:ApplicationScope private val appScope: CoroutineScope,
    private val workoutDao: WorkoutDao,
    private val heartRates: HeartRateDao,
) {
    private val client by lazy { HealthConnectClient.getOrCreate(context) }
    private val mutex = Mutex()
    private val _state = MutableStateFlow<SyncState>(SyncState.Idle)
    val state: StateFlow<SyncState> = _state.asStateFlow()

    fun availability(): HealthAvailability = when (HealthConnectClient.getSdkStatus(context)) {
        HealthConnectClient.SDK_AVAILABLE -> HealthAvailability.AVAILABLE
        HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> HealthAvailability.NEEDS_UPDATE
        else -> HealthAvailability.NOT_SUPPORTED
    }

    suspend fun grantedPermissions(): Set<String> =
        if (availability() == HealthAvailability.AVAILABLE) {
            runCatching { client.permissionController.getGrantedPermissions() }.getOrDefault(emptySet())
        } else {
            emptySet()
        }

    fun permissionContract(): ActivityResultContract<Set<String>, Set<String>> =
        PermissionController.createRequestPermissionResultContract()

    /** Opens Health Connect's own settings (to change permissions or connected apps). */
    fun settingsIntent(): Intent = Intent(
        if (Build.VERSION.SDK_INT >= 34) "android.health.connect.action.HEALTH_HOME_SETTINGS"
        else "androidx.health.ACTION_HEALTH_CONNECT_SETTINGS",
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Play Store page to install or update Health Connect (Android 13 and older). */
    fun installIntent(): Intent = Intent(
        Intent.ACTION_VIEW,
        Uri.parse("market://details?id=$PROVIDER&url=healthconnect%3A%2F%2Fonboarding"),
    ).setPackage("com.android.vending").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Starts a sync that keeps going even if you leave the screen that asked for it. */
    fun syncInBackground() {
        appScope.launch { sync() }
    }

    /**
     * Writes a finished Forge workout (and any live heart rate) to Health Connect, so
     * apps like Samsung Health can see it. Uses the workout's id as the record id, so
     * writing it again updates it rather than duplicating it. Does nothing if you
     * haven't allowed Forge to write.
     */
    fun exportWorkoutInBackground(sessionId: String) {
        appScope.launch { runCatching { exportWorkout(sessionId) } }
    }

    suspend fun exportWorkout(sessionId: String): Boolean {
        if (availability() != HealthAvailability.AVAILABLE) return false
        if (!preferences.preferences.first().healthConnectEnabled) return false
        val granted = grantedPermissions()
        if (HealthPermission.getWritePermission(ExerciseSessionRecord::class) !in granted) return false
        val session = workoutDao.getSession(sessionId) ?: return false
        val end = session.endedAt ?: return false
        val zone = ZoneId.systemDefault()
        val start = Instant.ofEpochMilli(session.startedAt)
        val finish = Instant.ofEpochMilli(end)
        val offsetStart = zone.rules.getOffset(start)
        val offsetEnd = zone.rules.getOffset(finish)
        val samples = heartRates.forSession(sessionId).filter { it.atMillis in session.startedAt..end }
        val strap = Device(type = Device.TYPE_FITNESS_BAND, manufacturer = null, model = preferences.preferences.first().hrDeviceName)
        val records = mutableListOf<Record>(
            ExerciseSessionRecord(
                startTime = start,
                startZoneOffset = offsetStart,
                endTime = finish,
                endZoneOffset = offsetEnd,
                metadata = if (samples.isEmpty()) Metadata.manualEntry("forge-$sessionId", session.updatedAt)
                else Metadata.activelyRecorded(strap, "forge-$sessionId", session.updatedAt),
                exerciseType = ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING,
                title = session.name,
                notes = session.notes,
            ),
        )
        if (samples.isNotEmpty() && HealthPermission.getWritePermission(HeartRateRecord::class) in granted) {
            records += HeartRateRecord(
                startTime = Instant.ofEpochMilli(samples.first().atMillis),
                startZoneOffset = offsetStart,
                endTime = Instant.ofEpochMilli(samples.last().atMillis + 1),
                endZoneOffset = offsetEnd,
                samples = samples.map { HeartRateRecord.Sample(Instant.ofEpochMilli(it.atMillis), it.bpm.toLong()) },
                metadata = Metadata.activelyRecorded(strap, "forge-hr-$sessionId", session.updatedAt),
            )
        }
        client.insertRecords(records)
        return true
    }

    /** Next sync re-reads the full 30 days (after reconnecting or allowing more data). */
    suspend fun resetSyncWindow() = preferences.setLastHealthSync(null)

    /** Syncs when switched on and the last sync was a while ago; cheap to call on every resume. */
    suspend fun syncIfDue() {
        val prefs = preferences.preferences.first()
        if (!prefs.healthConnectEnabled) return
        val last = prefs.lastHealthSyncMillis
        if (last != null && time.now() - last < AUTO_SYNC_INTERVAL_MS) return
        sync()
    }

    /**
     * Imports everything since the last sync plus a week of overlap (to catch late
     * edits), and 30 days the first time, which is as far back as Health Connect lets a
     * newly connected app read. Never throws (except to cancel); the outcome is in [state].
     */
    suspend fun sync(): SyncState = mutex.withLock {
        if (availability() != HealthAvailability.AVAILABLE) {
            return@withLock SyncState.Failed("Health Connect isn't available on this phone").also { _state.value = it }
        }
        _state.value = SyncState.Syncing
        val outcome = try {
            val granted = grantedPermissions()
            if (granted.isEmpty()) error("No Health Connect permissions granted yet")
            val prefs = preferences.preferences.first()
            val last = prefs.lastHealthSyncMillis
            val days = if (last == null) {
                FIRST_SYNC_DAYS
            } else {
                // A gap (e.g. not opening Forge for 10 days) is filled in, up to 30 days.
                val daysSince = ((time.now() - last) / 86_400_000L).toInt()
                (daysSince + REGULAR_SYNC_DAYS).coerceIn(REGULAR_SYNC_DAYS, FIRST_SYNC_DAYS)
            }
            val zone = ZoneId.systemDefault()
            val today = Instant.ofEpochMilli(time.now()).atZone(zone).toLocalDate()
            val from = today.minusDays(days - 1L)
            val result = importSessions(granted, from.atStartOfDay(zone).toInstant(), Instant.ofEpochMilli(time.now()))
            activities.saveDaily(readDaily(granted, from, today, zone))
            preferences.setLastHealthSync(time.now())
            SyncState.Done(result, days)
        } catch (e: CancellationException) {
            _state.value = SyncState.Idle
            throw e
        } catch (e: Exception) {
            SyncState.Failed(e.message ?: e::class.simpleName.orEmpty())
        }
        _state.value = outcome
        outcome
    }

    // ---- Exercise sessions -------------------------------------------------------------

    private suspend fun importSessions(granted: Set<String>, start: Instant, end: Instant): ImportResult {
        if (!granted.has(ExerciseSessionRecord::class)) return ImportResult()
        val sessions = readAll(ExerciseSessionRecord::class, TimeRangeFilter.between(start, end))
            .filter { it.metadata.dataOrigin.packageName != context.packageName }
            .map { record ->
                val stats = sessionStats(granted, record.startTime, record.endTime)
                ImportedSession(
                    externalId = record.metadata.id,
                    sport = HealthMapping.sportFor(record.exerciseType),
                    title = record.title?.ifBlank { null },
                    start = record.startTime.toEpochMilli(),
                    end = record.endTime.toEpochMilli(),
                    avgHeartRate = stats.avgHr,
                    maxHeartRate = stats.maxHr,
                    distanceMeters = stats.distanceMeters,
                    calories = stats.calories,
                )
            }
        return activities.importSessions(sessions)
    }

    private data class SessionStats(val avgHr: Int?, val maxHr: Int?, val distanceMeters: Double?, val calories: Double?)

    /** Heart rate, distance and calories during one session, from whatever you allowed. */
    private suspend fun sessionStats(granted: Set<String>, start: Instant, end: Instant): SessionStats {
        val metrics = buildSet<AggregateMetric<*>> {
            if (granted.has(HeartRateRecord::class)) { add(HeartRateRecord.BPM_AVG); add(HeartRateRecord.BPM_MAX) }
            if (granted.has(DistanceRecord::class)) add(DistanceRecord.DISTANCE_TOTAL)
            if (granted.has(ActiveCaloriesBurnedRecord::class)) add(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL)
        }
        if (metrics.isEmpty()) return SessionStats(null, null, null, null)
        val result = client.aggregate(AggregateRequest(metrics, TimeRangeFilter.between(start, end)))
        return SessionStats(
            avgHr = result[HeartRateRecord.BPM_AVG]?.toInt(),
            maxHr = result[HeartRateRecord.BPM_MAX]?.toInt(),
            distanceMeters = result[DistanceRecord.DISTANCE_TOTAL]?.inMeters?.takeIf { it > 0 },
            calories = result[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]?.inKilocalories?.takeIf { it > 0 },
        )
    }

    // ---- Daily data --------------------------------------------------------------------

    private suspend fun readDaily(granted: Set<String>, from: LocalDate, to: LocalDate, zone: ZoneId): List<DailyHealthEntity> {
        // Night readings from the evening before the first day belong to that first morning.
        val nightStart = from.minusDays(1).atStartOfDay(zone).toInstant()
        val end = to.plusDays(1).atStartOfDay(zone).toInstant()

        val sleep = if (granted.has(SleepSessionRecord::class)) {
            val spans = readAll(SleepSessionRecord::class, TimeRangeFilter.between(nightStart, end)).map { s ->
                SleepSpan(
                    s.startTime,
                    s.endTime,
                    s.stages.map { StageSpan(it.startTime, it.endTime, it.stage in AWAKE_STAGES) },
                )
            }
            HealthMapping.sleepByDay(spans, zone)
        } else {
            emptyMap()
        }
        val hrv = if (granted.has(HeartRateVariabilityRmssdRecord::class)) {
            HealthMapping.averageByMorning(
                readAll(HeartRateVariabilityRmssdRecord::class, TimeRangeFilter.between(nightStart, end))
                    .map { TimedValue(it.time, it.heartRateVariabilityMillis) },
                zone,
            )
        } else {
            emptyMap()
        }
        val resting = if (granted.has(RestingHeartRateRecord::class)) {
            HealthMapping.averageByDay(
                readAll(RestingHeartRateRecord::class, TimeRangeFilter.between(from.atStartOfDay(zone).toInstant(), end))
                    .map { TimedValue(it.time, it.beatsPerMinute.toDouble()) },
                zone,
            )
        } else {
            emptyMap()
        }
        val now = time.now()
        val days = generateSequence(from) { it.plusDays(1) }.takeWhile { !it.isAfter(to) }.toList()
        return days.map { day ->
            val steps = if (granted.has(StepsRecord::class)) {
                // Aggregating (rather than adding records up) lets Health Connect remove
                // duplicates when both your phone and your strap count steps.
                client.aggregate(
                    AggregateRequest(
                        setOf(StepsRecord.COUNT_TOTAL),
                        TimeRangeFilter.between(day.atStartOfDay(zone).toInstant(), day.plusDays(1).atStartOfDay(zone).toInstant()),
                    ),
                )[StepsRecord.COUNT_TOTAL]
            } else {
                null
            }
            DailyHealthEntity(
                epochDay = day.toEpochDay(),
                steps = steps,
                sleepMinutes = sleep[day]?.takeIf { it > 0 },
                restingHr = resting[day]?.let { (it * 10).roundToInt() / 10.0 },
                hrvMs = hrv[day]?.let { (it * 10).roundToInt() / 10.0 },
                updatedAt = now,
            )
        }
    }

    private suspend fun <T : Record> readAll(type: KClass<T>, range: TimeRangeFilter): List<T> {
        val all = mutableListOf<T>()
        var token: String? = null
        do {
            val response = client.readRecords(ReadRecordsRequest(recordType = type, timeRangeFilter = range, pageToken = token))
            all += response.records
            token = response.pageToken
        } while (token != null && all.size < MAX_RECORDS)
        return all
    }

    private fun Set<String>.has(type: KClass<out Record>) = HealthPermission.getReadPermission(type) in this

    companion object {
        const val PROVIDER = "com.google.android.apps.healthdata"
        const val FIRST_SYNC_DAYS = 30
        const val REGULAR_SYNC_DAYS = 7
        const val AUTO_SYNC_INTERVAL_MS = 15 * 60_000L
        private const val MAX_RECORDS = 5_000

        private val AWAKE_STAGES = setOf(
            SleepSessionRecord.STAGE_TYPE_AWAKE,
            SleepSessionRecord.STAGE_TYPE_AWAKE_IN_BED,
            SleepSessionRecord.STAGE_TYPE_OUT_OF_BED,
        )

        /** Lets Forge add its own workouts (with live heart rate) to Health Connect. */
        val WRITE_PERMISSIONS: Set<String> = setOf(
            HealthPermission.getWritePermission(ExerciseSessionRecord::class),
            HealthPermission.getWritePermission(HeartRateRecord::class),
        )

        /** Everything Forge asks to read. You can allow any subset. */
        val PERMISSIONS: Set<String> = setOf(
            HealthPermission.getReadPermission(ExerciseSessionRecord::class),
            HealthPermission.getReadPermission(HeartRateRecord::class),
            HealthPermission.getReadPermission(RestingHeartRateRecord::class),
            HealthPermission.getReadPermission(HeartRateVariabilityRmssdRecord::class),
            HealthPermission.getReadPermission(SleepSessionRecord::class),
            HealthPermission.getReadPermission(StepsRecord::class),
            HealthPermission.getReadPermission(DistanceRecord::class),
            HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
        )
    }
}

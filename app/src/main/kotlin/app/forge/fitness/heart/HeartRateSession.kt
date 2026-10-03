package app.forge.fitness.heart

import android.content.Context
import app.forge.fitness.data.db.HeartRateDao
import app.forge.fitness.data.db.HeartRateSampleEntity
import app.forge.fitness.data.prefs.UserPreferencesRepository
import app.forge.fitness.data.workout.WorkoutRepository
import app.forge.fitness.di.ApplicationScope
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Ties live heart rate to workouts: when a workout is running and you've set up a
 * strap, it connects (via [HeartRateService]) and saves a reading every couple of
 * seconds to that workout; when the workout ends, it lets the strap go.
 */
@Singleton
class HeartRateSession @Inject constructor(
    @ApplicationContext private val context: Context,
    private val monitor: HeartRateMonitor,
    private val workouts: WorkoutRepository,
    private val preferences: UserPreferencesRepository,
    private val dao: HeartRateDao,
    @param:ApplicationScope private val scope: CoroutineScope,
) {
    private var activeSessionId: String? = null
    private var lastSavedAt = 0L
    private var running = false

    fun start() {
        // Save readings for whichever workout is running.
        scope.launch {
            monitor.readings.collect { reading ->
                val session = activeSessionId ?: return@collect
                val now = System.currentTimeMillis()
                if (now - lastSavedAt < SAMPLE_EVERY_MS) return@collect
                lastSavedAt = now
                dao.insert(listOf(HeartRateSampleEntity(session, now, reading.bpm)))
            }
        }
        // Connect while a workout runs and a strap is set up; let go when it ends.
        scope.launch {
            combine(workouts.observeActiveSession(), preferences.preferences) { session, prefs ->
                Triple(session?.id, prefs.hrDeviceAddress, prefs.hrDeviceName)
            }.distinctUntilChanged().collect { (session, address, name) ->
                activeSessionId = session
                if (session != null && address != null) ensureRunning(address, name) else stopRunning()
            }
        }
    }

    /** Called when Forge comes to the screen: Android only allows starting the service then. */
    fun onAppForeground() {
        scope.launch {
            val prefs = preferences.preferences.first()
            val address = prefs.hrDeviceAddress ?: return@launch
            if (activeSessionId != null && monitor.state.value !is StrapState.Live) ensureRunning(address, prefs.hrDeviceName)
        }
    }

    private fun ensureRunning(address: String, name: String?) {
        if (!monitor.hasPermission()) return
        if (running && monitor.state.value !is StrapState.Off) return
        running = HeartRateService.start(context, address, name)
    }

    private fun stopRunning() {
        if (running) HeartRateService.stop(context)
        running = false
    }

    private companion object {
        const val SAMPLE_EVERY_MS = 2_000L
    }
}

package app.forge.fitness.timer

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.edit
import app.forge.fitness.di.TimeSource
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** A running rest period. [endAt] is wall-clock time in epoch ms. */
data class RestState(
    val endAt: Long,
    val totalSeconds: Int,
    /** What you're resting before, e.g. "Next: Push-ups". */
    val label: String,
)

/**
 * The rest timer.
 *
 * It doesn't count down in the app. It records when rest ends and asks the system for
 * an exact alarm at that moment. That means:
 *  - the countdown keeps going with the screen off or the app closed;
 *  - nothing runs in the background meanwhile, so no battery drain;
 *  - when the alarm fires, the phone vibrates and shows "Rest over" even if the
 *    app was killed.
 * The state is also saved to disk, so the in-app timer bar comes back after a restart.
 */
@Singleton
class RestTimer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val notifications: RestNotifications,
    private val time: TimeSource,
) {
    private val prefs = context.getSharedPreferences("rest_timer", Context.MODE_PRIVATE)
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    private val _state = MutableStateFlow(load())
    val state: StateFlow<RestState?> = _state.asStateFlow()

    fun start(seconds: Int, label: String) {
        if (seconds <= 0) return
        set(RestState(time.now() + seconds * 1000L, seconds, label))
    }

    /** Adds (or with a negative value, removes) time from the running timer. */
    fun adjust(seconds: Int) {
        val current = _state.value ?: return
        val endAt = current.endAt + seconds * 1000L
        if (endAt <= time.now()) {
            stop()
        } else {
            set(current.copy(endAt = endAt, totalSeconds = (current.totalSeconds + seconds).coerceAtLeast(1)))
        }
    }

    /** Cancels the timer without the "rest over" alert. */
    fun stop() {
        alarmManager.cancel(alarmIntent())
        notifications.cancelRunning()
        save(null)
    }

    /** Called by the alarm when rest is over. */
    fun onFinished() {
        val finished = _state.value
        save(null)
        notifications.cancelRunning()
        notifications.showFinished(finished?.label)
    }

    private fun set(state: RestState) {
        notifications.cancelFinished()
        save(state)
        schedule(state.endAt)
        notifications.showRunning(state)
    }

    private fun schedule(endAt: Long) {
        val pending = alarmIntent()
        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
        if (canExact) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endAt, pending)
        } else {
            // Without exact-alarm permission the system may deliver this a little late.
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endAt, pending)
        }
    }

    private fun alarmIntent(): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_ALARM,
        Intent(context, RestTimerReceiver::class.java).setAction(RestTimerReceiver.ACTION_FINISHED),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun save(state: RestState?) {
        _state.value = state
        prefs.edit {
            if (state == null) {
                clear()
            } else {
                putLong(KEY_END, state.endAt)
                putInt(KEY_TOTAL, state.totalSeconds)
                putString(KEY_LABEL, state.label)
            }
        }
    }

    private fun load(): RestState? {
        val endAt = prefs.getLong(KEY_END, 0L)
        if (endAt <= time.now()) return null
        return RestState(endAt, prefs.getInt(KEY_TOTAL, 0), prefs.getString(KEY_LABEL, "").orEmpty())
    }

    private companion object {
        const val REQUEST_ALARM = 4001
        const val KEY_END = "end_at"
        const val KEY_TOTAL = "total_seconds"
        const val KEY_LABEL = "label"
    }
}

package app.forge.fitness.heart

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import app.forge.fitness.AppActions
import app.forge.fitness.R
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Keeps the strap connected while a workout runs, even with the screen off (Android
 * stops background Bluetooth otherwise). Shows a quiet notification with your heart rate.
 */
class HeartRateService : Service() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Deps {
        fun monitor(): HeartRateMonitor
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var updates: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "Live heart rate", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Shown while Forge records your heart rate during a workout"
                setShowBadge(false)
            },
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val address = intent?.getStringExtra(EXTRA_ADDRESS)
        val name = intent?.getStringExtra(EXTRA_NAME)
        val type = if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE else 0
        runCatching { ServiceCompat.startForeground(this, NOTIFICATION_ID, notification("Connecting to ${name ?: "your strap"}…"), type) }
            .onFailure { stopSelf(); return START_NOT_STICKY }
        isRunning = true
        val monitor = EntryPointAccessors.fromApplication(applicationContext, Deps::class.java).monitor()
        if (address != null) monitor.connect(address, name)
        updates?.cancel()
        updates = scope.launch {
            monitor.state.collectLatest { s ->
                val text = when (s) {
                    is StrapState.Live -> "♥ ${s.bpm} bpm" + if (s.contact == false) " (check the strap's contact)" else ""
                    is StrapState.Connecting -> "Connecting to ${s.name ?: "your strap"}…"
                    is StrapState.Reconnecting -> "Strap out of range, reconnecting…"
                    StrapState.BluetoothOff -> "Bluetooth is off"
                    StrapState.NoPermission -> "Allow Nearby devices for Forge"
                    is StrapState.Failed -> s.message
                    StrapState.Off -> "Heart rate off"
                }
                getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(text))
            }
        }
        return START_NOT_STICKY
    }

    private fun notification(text: String): Notification {
        val open = PendingIntent.getActivity(
            this, 1, AppActions.intent(this, AppActions.OPEN_WORKOUT), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_forge)
            .setContentTitle("Recording heart rate")
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .build()
    }

    override fun onDestroy() {
        isRunning = false
        scope.cancel()
        EntryPointAccessors.fromApplication(applicationContext, Deps::class.java).monitor().disconnect()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "live_heart_rate"
        private const val NOTIFICATION_ID = 4_201
        private const val EXTRA_ADDRESS = "address"
        private const val EXTRA_NAME = "name"

        /** True while the service is up and in the foreground (it alone keeps recording with the screen off). */
        @Volatile var isRunning = false
            private set

        /** Returns false if Android didn't allow it (e.g. Forge isn't on screen). */
        fun start(context: Context, address: String, name: String?): Boolean = runCatching {
            ContextCompat.startForegroundService(
                context,
                Intent(context, HeartRateService::class.java).putExtra(EXTRA_ADDRESS, address).putExtra(EXTRA_NAME, name),
            )
        }.isSuccess

        fun stop(context: Context) {
            context.stopService(Intent(context, HeartRateService::class.java))
        }
    }
}

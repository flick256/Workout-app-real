package app.forge.fitness

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.forge.domain.model.ThemeMode
import app.forge.fitness.data.health.HealthConnectManager
import app.forge.fitness.di.ApplicationScope
import app.forge.fitness.ui.navigation.ForgeApp
import app.forge.fitness.ui.theme.ForgeTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.MutableStateFlow

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val appViewModel: AppViewModel by viewModels()

    @Inject lateinit var health: HealthConnectManager

    @Inject lateinit var heartRate: app.forge.fitness.heart.HeartRateSession

    @Inject @field:ApplicationScope
    lateinit var appScope: CoroutineScope

    /** Set when the app is opened from a notification, widget or shortcut. */
    private val pendingAction = MutableStateFlow<PendingAction?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        // Hold the splash for the few ms it takes to read settings, so the first frame
        // already has the right theme (no flash of the wrong colours).
        splash.setKeepOnScreenCondition { appViewModel.themeMode.value == null }
        enableEdgeToEdge()
        if (savedInstanceState == null) handle(intent)

        setContent {
            val themeMode by appViewModel.themeMode.collectAsStateWithLifecycle()
            val activeWorkout by appViewModel.activeWorkout.collectAsStateWithLifecycle()
            val action by pendingAction.collectAsStateWithLifecycle()
            ForgeTheme(themeMode = themeMode ?: ThemeMode.DARK) {
                ForgeApp(
                    activeWorkout = activeWorkout.session,
                    activeWorkoutLoaded = activeWorkout.loaded,
                    pendingAction = action,
                    onActionHandled = { pendingAction.value = null },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Pulls in new strap/watch data (if Health Connect is switched on); at most every 15 min.
        appScope.launch { health.syncIfDue() }
        // Android only lets Forge start the heart-rate service while it's on screen.
        heartRate.onAppForeground()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        when (intent?.action) {
            AppActions.OPEN_WORKOUT -> pendingAction.value = PendingAction.OpenWorkout
            AppActions.OPEN_FOOD -> pendingAction.value = PendingAction.OpenFood
            AppActions.SCAN_FOOD -> pendingAction.value = PendingAction.ScanFood
            AppActions.LOG_ACTIVITY -> pendingAction.value = PendingAction.LogActivity
            AppActions.OPEN_HABITS -> pendingAction.value = PendingAction.OpenHabits
            AppActions.START_WORKOUT, AppActions.START_ROUTINE -> {
                val routineId = intent.getStringExtra(AppActions.EXTRA_ROUTINE)
                lifecycleScope.launch {
                    if (routineId != null) appViewModel.startRoutine(routineId) else appViewModel.startWorkout()
                    pendingAction.value = PendingAction.OpenWorkout
                }
            }
        }
        // Don't act on the same intent again after a rotation.
        intent?.action = null
    }

    companion object {
        const val ACTION_OPEN_WORKOUT = AppActions.OPEN_WORKOUT
    }
}

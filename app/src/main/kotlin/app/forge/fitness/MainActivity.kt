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
import kotlinx.coroutines.flow.MutableStateFlow

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val appViewModel: AppViewModel by viewModels()

    @Inject lateinit var health: HealthConnectManager

    @Inject @field:ApplicationScope
    lateinit var appScope: CoroutineScope

    /** Set when the app is opened from the rest-timer notification. */
    private val openWorkoutRequest = MutableStateFlow(false)

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
            val openWorkout by openWorkoutRequest.collectAsStateWithLifecycle()
            ForgeTheme(themeMode = themeMode ?: ThemeMode.DARK) {
                ForgeApp(
                    activeWorkout = activeWorkout.session,
                    activeWorkoutLoaded = activeWorkout.loaded,
                    openWorkoutRequested = openWorkout,
                    onOpenWorkoutHandled = { openWorkoutRequest.value = false },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Pulls in new strap/watch data (if Health Connect is switched on); at most every 15 min.
        appScope.launch { health.syncIfDue() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        if (intent?.action == ACTION_OPEN_WORKOUT) openWorkoutRequest.value = true
    }

    companion object {
        const val ACTION_OPEN_WORKOUT = "app.forge.fitness.OPEN_WORKOUT"
    }
}

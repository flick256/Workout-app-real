package app.forge.fitness

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.forge.domain.model.ThemeMode
import app.forge.fitness.ui.navigation.ForgeApp
import app.forge.fitness.ui.theme.ForgeTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val appViewModel: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        // Hold the splash for the few ms it takes to read settings, so the first frame
        // already has the right theme (no flash of the wrong colours).
        splash.setKeepOnScreenCondition { appViewModel.themeMode.value == null }
        enableEdgeToEdge()

        setContent {
            val themeMode by appViewModel.themeMode.collectAsStateWithLifecycle()
            ForgeTheme(themeMode = themeMode ?: ThemeMode.DARK) {
                ForgeApp()
            }
        }
    }
}

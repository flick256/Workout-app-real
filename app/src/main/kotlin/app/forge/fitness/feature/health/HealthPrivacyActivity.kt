package app.forge.fitness.feature.health

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import app.forge.domain.model.ThemeMode
import app.forge.fitness.ui.components.ForgeCard
import app.forge.fitness.ui.components.ScreenScaffold
import app.forge.fitness.ui.theme.ForgeTheme

/**
 * Health Connect shows this when you tap "Read privacy policy" for Forge. Required
 * for an app to be allowed to ask for health permissions.
 */
class HealthPrivacyActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ForgeTheme(themeMode = ThemeMode.SYSTEM) {
                ScreenScaffold(
                    title = "Your health data",
                    modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing),
                ) {
                    POINTS.forEach { (heading, body) ->
                        item {
                            ForgeCard {
                                Text(heading, style = MaterialTheme.typography.titleMedium)
                                Text(body, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        }
    }

    private companion object {
        val POINTS = listOf(
            "What Forge reads" to "Workouts and sports sessions, heart rate, resting heart rate, heart-rate " +
                "variability, sleep, steps, distance and active calories, as shared by apps like Zepp. " +
                "You choose which of these to allow.",
            "What it's used for" to "Showing your activities next to your workouts, heart rate on workouts, " +
                "how recovered your muscles are, and a daily readiness check from sleep and HRV.",
            "Where it goes" to "Nowhere. Forge has no servers and no internet features for health data. " +
                "Everything stays in Forge's private storage on this phone, and only ends up in a backup " +
                "if you make one yourself.",
            "Read-only" to "Forge never writes to, changes or deletes anything in Health Connect.",
            "Turning it off" to "Switch off Health Connect in Forge's Settings, or remove Forge's access in " +
                "Health Connect. Activities already imported stay in Forge until you delete them.",
        )
    }
}

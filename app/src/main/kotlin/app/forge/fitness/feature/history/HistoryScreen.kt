package app.forge.fitness.feature.history

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.History
import androidx.compose.runtime.Composable
import app.forge.fitness.ui.components.EmptyState
import app.forge.fitness.ui.components.ScreenScaffold

@Composable
fun HistoryScreen() {
    ScreenScaffold(title = "History") {
        item {
            EmptyState(
                icon = Icons.Rounded.History,
                title = "No workouts yet",
                body = "Finished workouts, sport sessions and cardio will show up here.",
            )
        }
    }
}

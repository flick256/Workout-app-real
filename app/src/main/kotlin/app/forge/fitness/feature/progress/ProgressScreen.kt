package app.forge.fitness.feature.progress

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.runtime.Composable
import app.forge.fitness.ui.components.EmptyState
import app.forge.fitness.ui.components.ScreenScaffold

@Composable
fun ProgressScreen() {
    ScreenScaffold(title = "Progress") {
        item {
            EmptyState(
                icon = Icons.Rounded.Insights,
                title = "Nothing to chart yet",
                body = "PRs, strength charts, weekly volume per muscle and your training " +
                    "calendar will live here once you've logged a few workouts.",
            )
        }
    }
}

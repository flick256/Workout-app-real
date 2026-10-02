package app.forge.fitness.feature.exercises

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.runtime.Composable
import app.forge.fitness.ui.components.EmptyState
import app.forge.fitness.ui.components.ScreenScaffold

@Composable
fun ExercisesScreen() {
    ScreenScaffold(title = "Exercises") {
        item {
            EmptyState(
                icon = Icons.Rounded.FitnessCenter,
                title = "Library loading soon",
                body = "870+ exercises with instructions, filtered to the equipment you own. " +
                    "Arrives with workout logging in M1.",
            )
        }
    }
}

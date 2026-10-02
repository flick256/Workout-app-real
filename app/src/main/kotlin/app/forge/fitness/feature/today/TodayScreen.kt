package app.forge.fitness.feature.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import app.forge.fitness.ui.components.BigButton
import app.forge.fitness.ui.components.ForgeCard
import app.forge.fitness.ui.components.MilestoneBadge
import app.forge.fitness.ui.components.ScreenScaffold
import app.forge.fitness.ui.theme.Spacing
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@Composable
fun TodayScreen() {
    val greeting = remember { greetingFor(LocalTime.now()) }
    val date = remember { LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE d MMMM")) }

    ScreenScaffold(title = greeting) {
        item {
            Text(
                date,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            ForgeCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Bolt, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(Spacing.sm))
                    Text("Quick start", style = MaterialTheme.typography.titleLarge)
                }
                Spacer(Modifier.height(Spacing.sm))
                Text(
                    "Start an empty workout and add exercises as you go.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(Spacing.lg))
                BigButton(
                    text = "Start workout",
                    icon = Icons.Rounded.PlayArrow,
                    onClick = {},
                    enabled = false,
                )
                Spacer(Modifier.height(Spacing.sm))
                MilestoneBadge("Workout logging arrives in M1")
            }
        }
        item {
            ForgeCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("What should I train?", style = MaterialTheme.typography.titleLarge)
                    Icon(Icons.Rounded.AutoAwesome, null, tint = MaterialTheme.colorScheme.secondary)
                }
                Spacer(Modifier.height(Spacing.sm))
                Text(
                    "Suggestions based on recovery, recent volume and the time you have.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(Spacing.md))
                MilestoneBadge("Arrives in M4")
            }
        }
    }
}

private fun greetingFor(time: LocalTime): String = when (time.hour) {
    in 5..11 -> "Good morning"
    in 12..17 -> "Good afternoon"
    else -> "Good evening"
}

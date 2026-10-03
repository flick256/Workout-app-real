package app.forge.fitness.feature.goals

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.unit.dp
import app.forge.fitness.data.goals.GoalsOverview
import app.forge.fitness.ui.components.ForgeCard
import app.forge.fitness.ui.theme.Sizes
import app.forge.fitness.ui.theme.Spacing

/** Today's habits (tap to tick) and the nearest goal, on the Today screen. */
@Composable
fun HabitsTodayCard(overview: GoalsOverview, onToggle: (String) -> Unit, onOpen: () -> Unit) {
    if (!overview.loaded) return
    val today = overview.habits.filter { it.isDueToday }
    ForgeCard {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .heightIn(min = Sizes.touch)
                .clickable(onClickLabel = "Open goals and habits", role = Role.Button, onClick = onOpen),
        ) {
            Icon(Icons.Rounded.Flag, null, tint = MaterialTheme.colorScheme.secondary)
            Text("  Goals & habits", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text("Open", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
        if (today.isEmpty() && overview.goals.isEmpty()) {
            Text(
                "Set a goal or a daily habit, like stretching or hitting protein.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.xs),
            )
        }
        today.take(MAX_HABITS).forEach { habit ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                HabitCheck(habit) { onToggle(habit.habit.id) }
                Text(habit.habit.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                if (habit.streak > 1) {
                    Icon(Icons.Rounded.LocalFireDepartment, "Streak", tint = MaterialTheme.colorScheme.tertiary)
                    Text("${habit.streak}", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
        overview.goals.filterNot { it.progress.done }.maxByOrNull { it.progress.fraction }?.let { goal ->
            Column(Modifier.padding(top = Spacing.sm)) {
                Text(goal.title, style = MaterialTheme.typography.bodyMedium)
                LinearProgressIndicator(progress = { goal.progress.fraction.toFloat() }, modifier = Modifier.fillMaxWidth().padding(top = 2.dp))
                Text(goal.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private const val MAX_HABITS = 6

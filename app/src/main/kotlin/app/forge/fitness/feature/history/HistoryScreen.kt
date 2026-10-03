package app.forge.fitness.feature.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.forge.fitness.feature.activity.ActivityCard
import app.forge.fitness.ui.theme.Sizes
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.forge.domain.model.WeightUnit
import app.forge.fitness.ui.components.EmptyState
import app.forge.fitness.ui.components.ScreenScaffold
import app.forge.fitness.ui.components.SectionHeader
import app.forge.fitness.ui.format.Format
import app.forge.fitness.ui.theme.Spacing
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun HistoryScreen(
    onOpen: (sessionId: String) -> Unit,
    onOpenActivity: (activityId: String) -> Unit,
    onLogActivity: () -> Unit,
    vm: HistoryViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    ScreenScaffold(title = "History") {
        item(key = "log-activity") {
            OutlinedButton(onClick = onLogActivity, modifier = Modifier.fillMaxWidth().heightIn(min = Sizes.touch)) {
                Icon(Icons.Rounded.Add, null)
                Text("  Log a sport or cardio session")
            }
        }
        if (!state.loading && state.months.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.Rounded.History,
                    title = "Nothing here yet",
                    body = "Finished workouts show up here, along with sports and cardio you log or " +
                        "import from your watch through Health Connect.",
                )
            }
        }
        state.months.forEach { (month, items) ->
            item(key = "m-$month") {
                val workouts = items.count { it is HistoryEntry.Workout }
                val activities = items.size - workouts
                SectionHeader(
                    "${month.month.getDisplayName(TextStyle.FULL, Locale.getDefault())} ${month.year} · " +
                        listOfNotNull(
                            plural(workouts, "workout").takeIf { workouts > 0 },
                            plural(activities, "activity", "activities").takeIf { activities > 0 },
                        ).joinToString(" · "),
                )
            }
            items.forEach { entry ->
                item(key = entry.key) {
                    when (entry) {
                        is HistoryEntry.Workout -> HistoryCard(entry.item, state.unit) { onOpen(entry.item.summary.id) }
                        is HistoryEntry.Activity -> ActivityCard(entry.activity) { onOpenActivity(entry.activity.id) }
                    }
                }
            }
        }
    }
}

private fun plural(n: Int, one: String, many: String = one + "s") = "$n ${if (n == 1) one else many}"

private val dateFormat = DateTimeFormatter.ofPattern("EEE d MMM · h:mm a")

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HistoryCard(item: HistoryItem, unit: WeightUnit, onClick: () -> Unit) {
    val s = item.summary
    Card(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(Modifier.padding(Spacing.lg)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(s.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (s.avgHeartRate != null) {
                    Icon(Icons.Rounded.Favorite, "Average heart rate", tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(16.dp))
                    Text(" ${s.avgHeartRate}", style = MaterialTheme.typography.labelLarge)
                }
            }
            Text(
                Instant.ofEpochMilli(s.startedAt).atZone(ZoneId.systemDefault()).format(dateFormat),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Spacing.sm))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Stat("Time", s.endedAt?.let { Format.durationWords((it - s.startedAt) / 1000) } ?: "–")
                Stat("Sets", s.setCount.toString())
                if (s.volumeKg > 0) Stat("Volume", Format.volume(s.volumeKg, unit))
            }
            if (item.lines.isNotEmpty()) {
                Spacer(Modifier.height(Spacing.sm))
                item.lines.take(MAX_LINES).forEach { line ->
                    Text(
                        "${line.sets} × ${line.name}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (item.lines.size > MAX_LINES) {
                    Text(
                        "+${item.lines.size - MAX_LINES} more",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
internal fun Stat(label: String, value: String) {
    Column {
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium)
    }
}

private const val MAX_LINES = 4

package app.forge.fitness.feature.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.History
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
    vm: HistoryViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    ScreenScaffold(title = "History") {
        if (!state.loading && state.months.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.Rounded.History,
                    title = "No workouts yet",
                    body = "Finished workouts show up here. Sport sessions and cardio join them in a later update.",
                )
            }
        }
        state.months.forEach { (month, items) ->
            item(key = "m-$month") {
                SectionHeader(
                    "${month.month.getDisplayName(TextStyle.FULL, Locale.getDefault())} ${month.year} · " +
                        "${items.size} workout${if (items.size == 1) "" else "s"}",
                )
            }
            items.forEach { entry ->
                item(key = entry.summary.id) {
                    HistoryCard(entry, state.unit) { onOpen(entry.summary.id) }
                }
            }
        }
    }
}

private val dateFormat = DateTimeFormatter.ofPattern("EEE d MMM · h:mm a")

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
            Text(s.name, style = MaterialTheme.typography.titleMedium)
            Text(
                Instant.ofEpochMilli(s.startedAt).atZone(ZoneId.systemDefault()).format(dateFormat),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Spacing.sm))
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.lg)) {
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

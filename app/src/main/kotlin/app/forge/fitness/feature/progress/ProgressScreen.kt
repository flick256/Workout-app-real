package app.forge.fitness.feature.progress

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ShowChart
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.MonitorWeight
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.clickable
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.forge.domain.analytics.HeatCell
import app.forge.domain.analytics.Record
import app.forge.domain.analytics.RecordKind
import app.forge.domain.calc.Units
import app.forge.domain.model.WeightUnit
import app.forge.domain.suggest.TrainToday
import app.forge.fitness.ui.charts.BarRow
import app.forge.fitness.ui.charts.StatTile
import app.forge.fitness.ui.charts.TargetBars
import app.forge.fitness.ui.charts.TrainingCalendar
import app.forge.fitness.ui.components.EmptyState
import app.forge.fitness.ui.components.ForgeCard
import app.forge.fitness.ui.components.LocalSnackbarHostState
import app.forge.fitness.ui.components.ScreenScaffold
import app.forge.fitness.ui.components.SectionHeader
import app.forge.fitness.ui.format.Format
import app.forge.fitness.ui.theme.Spacing
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

@Composable
fun ProgressScreen(
    onOpenExercise: (String) -> Unit,
    onOpenBody: () -> Unit,
    onOpenPhotos: () -> Unit,
    vm: ProgressViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val o = state.overview
    val unit = state.unit
    val scope = rememberCoroutineScope()
    val snackbar = LocalSnackbarHostState.current

    ScreenScaffold(title = "Progress") {
        if (!o.loading && o.heatmap.none { it.trained }) {
            item(key = "empty") {
                EmptyState(
                    icon = Icons.Rounded.Insights,
                    title = "Nothing to chart yet",
                    body = "Log a few workouts and your PRs, strength charts and training calendar appear here. " +
                        "Want a preview? Load 12 weeks of sample training (you can remove it in Settings).",
                    action = {
                        OutlinedButton(onClick = {
                            scope.launch {
                                val n = vm.loadDemo()
                                snackbar.showSnackbar("Added $n demo workouts")
                            }
                        }) { Text("Load demo data") }
                    },
                )
            }
        }

        item(key = "week") { app.forge.fitness.feature.ai.WeeklySummaryCard() }

        item(key = "kpis") {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                StatTile("Last 30 days", "${o.workouts30}", Modifier.weight(1f), delta = "workouts")
                StatTile("Streak", "${o.weekStreak} wk", Modifier.weight(1f), delta = "weeks in a row")
                val change = if (o.volumeLastWeek > 0) ((o.volumeThisWeek - o.volumeLastWeek) / o.volumeLastWeek * 100).roundToInt() else null
                StatTile(
                    "Volume this week",
                    Format.volume(o.volumeThisWeek, unit),
                    Modifier.weight(1.3f),
                    delta = change?.let { "${if (it >= 0) "↑" else "↓"} ${kotlin.math.abs(it)}% vs last week" },
                    deltaGood = change?.let { it >= 0 },
                )
            }
        }

        item(key = "calendar-h") { SectionHeader("Training calendar") }
        item(key = "calendar") {
            ForgeCard { TrainingCalendar(o.heatmap, describe = { describeDay(it, unit) }) }
        }

        item(key = "muscles-h") { SectionHeader("Sets per muscle, last 7 days") }
        item(key = "muscles") {
            ForgeCard {
                TargetBars(
                    o.muscles.filter { it.muscle in TrainToday.TRAINABLE }.map {
                        BarRow(it.muscle.label, it.weeklySets, it.weeklyTarget.toDouble(), "${it.weeklySets.roundToInt()}/${it.weeklyTarget}")
                    },
                )
                Spacer(Modifier.height(Spacing.sm))
                Text(
                    "The line marks a weekly target (~10 sets for big muscles, 6–8 for small). Secondary muscles count half.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (o.recentPrs.isNotEmpty()) {
            item(key = "prs-h") { SectionHeader("Recent personal records") }
            item(key = "prs") {
                ForgeCard {
                    o.recentPrs.take(MAX_PRS).forEach { pr ->
                        ListItem(
                            leadingContent = { Icon(Icons.Rounded.EmojiEvents, null, tint = MaterialTheme.colorScheme.primary) },
                            headlineContent = { Text(pr.exerciseName) },
                            supportingContent = { Text("${recordText(pr.record, unit)} · ${shortDate(pr.record.atMillis)}") },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            modifier = Modifier.clickable { onOpenExercise(pr.exerciseId) },
                        )
                    }
                }
            }
        }

        if (o.topExercises.isNotEmpty()) {
            item(key = "strength-h") { SectionHeader("Strength charts") }
            item(key = "strength") {
                ForgeCard {
                    o.topExercises.forEach { e ->
                        ListItem(
                            leadingContent = { Icon(Icons.AutoMirrored.Rounded.ShowChart, null) },
                            headlineContent = { Text(e.name) },
                            supportingContent = { Text("${e.timesUsed} workouts") },
                            trailingContent = { Icon(Icons.Rounded.ChevronRight, null) },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            modifier = Modifier.clickable { onOpenExercise(e.id) },
                        )
                    }
                }
            }
        }

        item(key = "body-h") { SectionHeader("Body") }
        item(key = "body") {
            ForgeCard {
                ListItem(
                    leadingContent = { Icon(Icons.Rounded.MonitorWeight, null) },
                    headlineContent = { Text("Bodyweight & measurements") },
                    supportingContent = {
                        Text(state.latestWeight?.let { "Latest: ${Format.weight(it.value, unit)} · ${shortDate(it.measuredAt)}" } ?: "Track weight, waist, arms…")
                    },
                    trailingContent = { Icon(Icons.Rounded.ChevronRight, null) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickable(onClick = onOpenBody),
                )
                ListItem(
                    leadingContent = { Icon(Icons.Rounded.PhotoLibrary, null) },
                    headlineContent = { Text("Progress photos") },
                    supportingContent = { Text("Private to this phone · compare side by side") },
                    trailingContent = { Icon(Icons.Rounded.ChevronRight, null) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickable(onClick = onOpenPhotos),
                )
            }
        }
    }
}

private const val MAX_PRS = 6

private val short = DateTimeFormatter.ofPattern("d MMM")
private val long = DateTimeFormatter.ofPattern("EEE d MMM")

fun shortDate(ms: Long): String = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).format(short)

private fun describeDay(cell: HeatCell, unit: WeightUnit): String =
    cell.date.format(long) + when (cell.workouts) {
        0 -> if (cell.activities == 0) " · rest" else ""
        1 -> " · 1 workout · ${Format.volume(cell.volumeKg, unit)}"
        else -> " · ${cell.workouts} workouts · ${Format.volume(cell.volumeKg, unit)}"
    } + when (cell.activities) {
        0 -> ""
        1 -> " · 1 sport/cardio session"
        else -> " · ${cell.activities} sport/cardio sessions"
    }

/** "Heaviest 25 kg × 8", "e1RM 62.5 kg", "15 reps", "Hold 1:05". */
fun recordText(r: Record, unit: WeightUnit): String = when (r.kind) {
    RecordKind.E1RM -> "e1RM ${Format.weight(Units.roundTo(r.value, 0.5), unit)}"
    RecordKind.HEAVIEST -> "Heaviest ${Format.weight(r.value, unit)}" + (r.reps?.let { " × $it" } ?: "")
    RecordKind.MOST_REPS -> "${r.value.roundToInt()} reps in a set"
    RecordKind.SESSION_VOLUME -> "Volume ${Format.volume(r.value, unit)}"
    RecordKind.LONGEST_HOLD -> "Hold ${Format.duration(r.value.toLong())}"
}

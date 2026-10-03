package app.forge.fitness.feature.health

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Watch
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.forge.fitness.data.db.DailyHealthEntity
import app.forge.fitness.data.health.HealthAvailability
import app.forge.fitness.data.health.HealthConnectManager
import app.forge.fitness.data.health.SyncState
import app.forge.fitness.ui.charts.LineChart
import app.forge.fitness.ui.charts.StatTile
import app.forge.fitness.ui.components.ConfirmDialog
import app.forge.fitness.ui.components.ForgeCard
import app.forge.fitness.ui.components.SectionHeader
import app.forge.fitness.ui.theme.Sizes
import app.forge.fitness.ui.theme.Spacing
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/** Connect your watch/strap through Health Connect, and see what it recorded. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HealthScreen(
    onBack: () -> Unit,
    vm: HealthViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val permissions = rememberLauncherForActivityResult(vm.health.permissionContract(), vm::onPermissionsResult)
    // Permissions may have changed in Health Connect's own settings while we were away.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refresh() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Health & watch") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        if (!state.loaded) return@Scaffold
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = Spacing.screen,
                end = Spacing.screen,
                top = padding.calculateTopPadding(),
                bottom = Spacing.xxl,
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            item(key = "status") {
                ConnectionCard(
                    state = state,
                    onConnect = { permissions.launch(HealthConnectManager.PERMISSIONS + HealthConnectManager.WRITE_PERMISSIONS) },
                    onSync = vm::syncNow,
                    onSettings = { runCatching { context.startActivity(vm.health.settingsIntent()) } },
                    onInstall = { runCatching { context.startActivity(vm.health.installIntent()) } },
                    onDisconnect = vm::disconnect,
                )
            }
            if (state.connected) {
                val readiness = state.readiness
                item(key = "readiness") { ReadinessCard(readiness) }
                item(key = "today") { TodayTiles(state.today) }
                charts(state.days)
            }
            item(key = "zepp") { ZeppHelp() }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ConnectionCard(
    state: HealthState,
    onConnect: () -> Unit,
    onSync: () -> Unit,
    onSettings: () -> Unit,
    onInstall: () -> Unit,
    onDisconnect: () -> Unit,
) {
    var confirmDisconnect by rememberSaveable { mutableStateOf(false) }
    if (confirmDisconnect) {
        ConfirmDialog(
            title = "Stop syncing?",
            message = "Forge stops reading from Health Connect. What's already imported stays, and turning it back " +
                "on catches up on the last 30 days.",
            confirmLabel = "Stop syncing",
            destructive = true,
            onConfirm = { confirmDisconnect = false; onDisconnect() },
            onDismiss = { confirmDisconnect = false },
        )
    }
    ForgeCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Watch, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(Spacing.sm))
            Text("Health Connect", style = MaterialTheme.typography.titleLarge)
        }
        Spacer(Modifier.height(Spacing.xs))
        when {
            state.availability == HealthAvailability.NOT_SUPPORTED -> Text(
                "Health Connect isn't available on this phone.",
                style = MaterialTheme.typography.bodyMedium,
            )
            state.availability == HealthAvailability.NEEDS_UPDATE -> {
                Text("Health Connect needs installing or updating first.", style = MaterialTheme.typography.bodyMedium)
                Button(onClick = onInstall, modifier = Modifier.padding(top = Spacing.sm)) { Text("Open Play Store") }
            }
            !state.connected -> {
                Text(
                    "Bring in what your watch or strap records: sports and runs, heart rate during workouts, " +
                        "sleep, HRV, resting heart rate and steps. Forge only reads (never writes), and the data " +
                        "stays on your phone.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                    onClick = onConnect,
                    modifier = Modifier.fillMaxWidth().heightIn(min = Sizes.touch).padding(top = Spacing.sm),
                ) { Text(if (state.granted.isEmpty()) "Connect" else "Turn on syncing") }
            }
            else -> {
                val sync = state.sync
                Text(
                    when (sync) {
                        SyncState.Syncing -> "Syncing…"
                        is SyncState.Failed -> "Last sync failed: ${sync.message}"
                        is SyncState.Done -> "Synced: " + listOfNotNull(
                            "${sync.result.added} new".takeIf { sync.result.added > 0 },
                            "${sync.result.updated} updated".takeIf { sync.result.updated > 0 },
                            "heart rate added to ${sync.result.workoutsWithHeartRate} workout(s)"
                                .takeIf { sync.result.workoutsWithHeartRate > 0 },
                        ).ifEmpty { listOf("nothing new") }.joinToString(", ")
                        SyncState.Idle -> state.lastSync?.let { "Last synced ${lastSyncText(it)}" } ?: "Not synced yet"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (sync is SyncState.Failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "Syncs by itself when you open Forge (at most every 15 minutes).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (state.missing > 0) {
                    Text(
                        "${state.missing} kind(s) of data not allowed yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                    TextButton(onClick = onConnect) { Text("Allow more") }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.padding(top = Spacing.sm)) {
                    Button(onClick = onSync, enabled = sync != SyncState.Syncing) {
                        if (sync == SyncState.Syncing) {
                            CircularProgressIndicator(Modifier.height(18.dp).width(18.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Rounded.Sync, null)
                        }
                        Text("  Sync now")
                    }
                    OutlinedButton(onClick = onSettings) { Text("Permissions") }
                }
                TextButton(onClick = { confirmDisconnect = true }) { Text("Stop syncing") }
            }
        }
    }
}

@Composable
internal fun ReadinessCard(readiness: app.forge.domain.activity.Readiness) {
    ForgeCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Favorite, null, tint = MaterialTheme.colorScheme.tertiary)
            Spacer(Modifier.width(Spacing.sm))
            Text("Readiness: ${readiness.level.label}", style = MaterialTheme.typography.titleMedium)
        }
        if (readiness.reasons.isEmpty()) {
            Text(
                "Wear your strap to bed for sleep and HRV. Forge compares each morning with your own last 4 weeks.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        readiness.reasons.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
        if (readiness.advice.isNotEmpty()) {
            Text(readiness.advice, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = Spacing.xs))
        }
    }
}

@Composable
private fun TodayTiles(today: DailyHealthEntity?) {
    androidx.compose.foundation.layout.Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            StatTile("Steps today", today?.steps?.let { "%,d".format(it) } ?: "–", Modifier.weight(1f))
            StatTile("Sleep", today?.sleepMinutes?.let(::hoursText) ?: "–", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            StatTile("HRV", today?.hrvMs?.let { "${it.roundToInt()} ms" } ?: "–", Modifier.weight(1f))
            StatTile("Resting HR", today?.restingHr?.let { "${it.roundToInt()} bpm" } ?: "–", Modifier.weight(1f))
        }
    }
}

private data class ChartSeries(val title: String, val points: List<Pair<Long, Double>>, val format: (Double) -> String)

/** One chart per measure (never two scales on one chart). */
private fun androidx.compose.foundation.lazy.LazyListScope.charts(days: List<DailyHealthEntity>) {
    val zone = ZoneId.systemDefault()
    fun millis(day: Long) = LocalDate.ofEpochDay(day).atStartOfDay(zone).toInstant().toEpochMilli()
    val dayFormat = DateTimeFormatter.ofPattern("EEE d MMM")
    fun time(ms: Long) = Instant.ofEpochMilli(ms).atZone(zone).format(dayFormat)
    val series = listOf(
        ChartSeries("Sleep", days.mapNotNull { d -> d.sleepMinutes?.let { millis(d.epochDay) to it / 60.0 } }) { hoursText((it * 60).roundToInt()) },
        ChartSeries("HRV", days.mapNotNull { d -> d.hrvMs?.let { millis(d.epochDay) to it } }) { "${it.roundToInt()} ms" },
        ChartSeries("Resting heart rate", days.mapNotNull { d -> d.restingHr?.let { millis(d.epochDay) to it } }) { "${it.roundToInt()} bpm" },
        ChartSeries("Steps", days.mapNotNull { d -> d.steps?.let { millis(d.epochDay) to it.toDouble() } }) { "%,d".format(it.roundToInt()) },
    )
    series.filter { it.points.size >= 2 }.forEach { (title, points, format) ->
        item(key = "chart-$title") {
            ForgeCard {
                SectionHeader("$title · last 30 days")
                LineChart(points = points, formatValue = format, formatTime = ::time, description = "$title over the last 30 days")
            }
        }
    }
}

@Composable
private fun ZeppHelp() {
    ForgeCard {
        Text("Using an Amazfit (Zepp)?", style = MaterialTheme.typography.titleMedium)
        listOf(
            "Open the Zepp app → Profile → Add accounts (or Data sharing) → Health Connect.",
            "Turn on sharing and allow everything Zepp asks to write.",
            "Come back here and tap Connect. Your strap syncs to Zepp, Zepp to Health Connect, then Forge reads it.",
            "Wear the strap to bed: sleep and HRV power the readiness check.",
            "When you lift, start a Strength workout on the strap too. Forge adds its heart rate to your Forge workout instead of making a duplicate.",
        ).forEachIndexed { i, step ->
            Text("${i + 1}. $step", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = Spacing.xs))
        }
    }
}

internal fun hoursText(minutes: Int) = "${minutes / 60}h ${minutes % 60}m"

private fun lastSyncText(millis: Long): String {
    val mins = (System.currentTimeMillis() - millis) / 60_000
    return when {
        mins < 1 -> "just now"
        mins < 60 -> "$mins min ago"
        mins < 24 * 60 -> "${mins / 60} h ago"
        else -> Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d MMM, h:mm a"))
    }
}

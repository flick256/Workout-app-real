package app.forge.fitness.feature.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.forge.domain.calc.Units
import app.forge.domain.model.LogType
import app.forge.domain.model.SetType
import app.forge.domain.model.WeightUnit
import app.forge.fitness.data.db.SetEntryEntity
import app.forge.fitness.ui.components.BigButton
import app.forge.fitness.ui.components.ForgeCard
import app.forge.fitness.ui.components.LocalSnackbarHostState
import app.forge.fitness.ui.components.showUndo
import app.forge.fitness.ui.format.Format
import app.forge.fitness.ui.theme.Spacing
import app.forge.fitness.ui.theme.tabular
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/** A finished workout. Right after finishing, it doubles as the "well done" summary. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionDetailScreen(
    onBack: () -> Unit,
    vm: SessionDetailViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = LocalSnackbarHostState.current
    val scope = rememberCoroutineScope()
    val justFinished = vm.route.justFinished
    val session = state.session

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(if (justFinished) "Workout complete" else session?.name.orEmpty()) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
                },
                actions = {
                    if (!justFinished && session != null) {
                        IconButton(onClick = {
                            vm.delete()
                            onBack()
                            scope.launch { snackbar.showUndo("Workout deleted") { vm.restore() } }
                        }) { Icon(Icons.Rounded.DeleteOutline, "Delete workout") }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        bottomBar = {
            if (justFinished) {
                BigButton(
                    text = "Done",
                    onClick = onBack,
                    modifier = Modifier.navigationBarsPadding().padding(Spacing.screen),
                )
            }
        },
    ) { padding ->
        if (session == null) return@Scaffold
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = Spacing.screen,
                end = Spacing.screen,
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + Spacing.xxl,
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            item {
                ForgeCard {
                    if (justFinished) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.EmojiEvents, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(Spacing.sm))
                            Text(session.name, style = MaterialTheme.typography.titleLarge)
                        }
                    }
                    Text(
                        Instant.ofEpochMilli(session.startedAt).atZone(ZoneId.systemDefault())
                            .format(DateTimeFormatter.ofPattern("EEEE d MMMM yyyy · h:mm a")),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(Spacing.md))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Stat("Time", session.endedAt?.let { Format.durationWords((it - session.startedAt) / 1000) } ?: "–")
                        Stat("Sets", state.summary?.completedSets?.toString() ?: "0")
                        Stat("Reps", state.summary?.totalReps?.toString() ?: "0")
                        Stat("Volume", Format.volume(state.summary?.volumeKg ?: 0.0, state.unit))
                    }
                    session.notes?.let {
                        Spacer(Modifier.height(Spacing.md))
                        Text(it, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            items(state.exercises, key = { it.first.item.id }) { (item, sets) ->
                ForgeCard {
                    Text(item.exercise.name, style = MaterialTheme.typography.titleMedium)
                    item.item.notes?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.height(Spacing.sm))
                    var work = 0
                    sets.forEach { set ->
                        val label = if (set.type == SetType.WORKING) (++work).toString() else set.type.short
                        Row(Modifier.padding(vertical = 2.dp)) {
                            Text(
                                label,
                                style = MaterialTheme.typography.bodyMedium.tabular(),
                                color = if (set.type == SetType.WARMUP) MaterialTheme.colorScheme.tertiary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.width(Spacing.xl),
                            )
                            Text(describe(set, item.exercise.logType, state.unit), style = MaterialTheme.typography.bodyMedium.tabular())
                        }
                    }
                }
            }
        }
    }
}

/** "60 kg × 8 @ 8", "+10 kg × 12", "1:30", "5 km · 25:00". */
internal fun describe(set: SetEntryEntity, logType: LogType, unit: WeightUnit): String {
    val rpe = set.rpe?.let { " @ ${Format.rpe(it)}" }.orEmpty()
    return when (logType) {
        LogType.WEIGHT_REPS -> "${set.weightKg?.let { Format.weight(it, unit) } ?: "–"} × ${set.reps ?: "–"}$rpe"
        LogType.REPS -> {
            val added = set.weightKg?.takeIf { it > 0 }?.let { "+${Format.weight(it, unit)} × " }.orEmpty()
            val load = set.loadKg?.takeIf { it != set.weightKg }?.let { " (≈ ${Format.weight(Units.roundTo(it, 0.5), unit)})" }.orEmpty()
            "$added${set.reps ?: "–"} reps$load$rpe"
        }
        LogType.DURATION -> (set.durationSeconds?.let { Format.duration(it.toLong()) } ?: "–") + rpe
        LogType.DISTANCE_DURATION -> listOfNotNull(
            set.distanceMeters?.let { "${Units.format(it / 1000.0)} km" },
            set.durationSeconds?.let { Format.duration(it.toLong()) },
        ).joinToString(" · ").ifEmpty { "–" } + rpe
    }
}

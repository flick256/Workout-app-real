package app.forge.fitness.feature.progress

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.Info
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
import androidx.compose.ui.Modifier
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.toRoute
import app.forge.domain.analytics.RecordKind
import app.forge.domain.calc.Units
import app.forge.domain.model.WeightUnit
import app.forge.fitness.data.analytics.AnalyticsRepository
import app.forge.fitness.data.analytics.ExerciseProgress
import app.forge.fitness.data.analytics.StrengthMetric
import app.forge.fitness.data.prefs.UserPreferencesRepository
import app.forge.fitness.feature.history.describe
import app.forge.fitness.ui.charts.LineChart
import app.forge.fitness.ui.charts.StatTile
import app.forge.fitness.ui.components.EmptyState
import app.forge.fitness.ui.components.ForgeCard
import app.forge.fitness.ui.components.SectionHeader
import app.forge.fitness.ui.format.Format
import app.forge.fitness.ui.navigation.ExerciseProgressRoute
import app.forge.fitness.ui.theme.Spacing
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class ExerciseProgressState(val loading: Boolean = true, val progress: ExerciseProgress? = null, val unit: WeightUnit = WeightUnit.KG)

@HiltViewModel
class ExerciseProgressViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    analytics: AnalyticsRepository,
    preferences: UserPreferencesRepository,
) : ViewModel() {
    val exerciseId = savedStateHandle.toRoute<ExerciseProgressRoute>().exerciseId

    val state: StateFlow<ExerciseProgressState> = combine(
        analytics.observeExerciseProgress(exerciseId),
        preferences.preferences,
    ) { p, prefs -> ExerciseProgressState(false, p, prefs.weightUnit) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExerciseProgressState())
}

/** One exercise's strength over time, its records, and every session as a table. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseProgressScreen(
    onBack: () -> Unit,
    onOpenDetails: (String) -> Unit,
    vm: ExerciseProgressViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val p = state.progress
    val unit = state.unit
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(p?.exercise?.name.orEmpty(), maxLines = 1) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
                actions = { IconButton(onClick = { onOpenDetails(vm.exerciseId) }) { Icon(Icons.Outlined.Info, "Exercise details") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        if (p == null) return@Scaffold
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = Spacing.screen, end = Spacing.screen,
                top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + Spacing.xxl,
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            if (p.series.isEmpty()) {
                item { EmptyState(Icons.Outlined.Info, "No history yet", "Finish a workout with this exercise to start its chart.") }
                return@LazyColumn
            }
            item(key = "chart") {
                ForgeCard {
                    Text(p.metric.label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    LineChart(
                        points = p.series,
                        formatValue = { metricText(it, p.metric, unit) },
                        formatTime = ::shortDate,
                        description = "${p.metric.label} for ${p.exercise.name}",
                    )
                    if (p.series.size >= 2) {
                        val change = p.series.last().second - p.series.first().second
                        Text(
                            "${if (change >= 0) "Up" else "Down"} ${metricText(kotlin.math.abs(change), p.metric, unit)} " +
                                "since ${shortDate(p.series.first().first)}. Drag across the chart to read any workout.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            item(key = "records") {
                val r = p.records
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    r[RecordKind.E1RM]?.let { StatTile("Best e1RM", Format.weight(Units.roundTo(it.value, 0.5), unit), Modifier.weight(1f), shortDate(it.atMillis)) }
                    r[RecordKind.HEAVIEST]?.let { StatTile("Heaviest", Format.weight(it.value, unit), Modifier.weight(1f), it.reps?.let { reps -> "× $reps" }) }
                    r[RecordKind.MOST_REPS]?.let { StatTile("Most reps", "${it.value.roundToInt()}", Modifier.weight(1f), shortDate(it.atMillis)) }
                    r[RecordKind.LONGEST_HOLD]?.let { StatTile("Longest hold", Format.duration(it.value.toLong()), Modifier.weight(1f), shortDate(it.atMillis)) }
                }
            }
            item(key = "table-h") { SectionHeader("Every workout") }
            // Index in the key: two workouts could in theory start in the same millisecond.
            itemsIndexed(p.sessions, key = { i, s -> "${s.first}-$i" }) { _, (at, sets) ->
                ForgeCard {
                    Text(shortDate(at), style = MaterialTheme.typography.titleSmall)
                    sets.forEach { Text(describe(it, p.exercise.logType, unit), style = MaterialTheme.typography.bodyMedium) }
                }
            }
        }
    }
}

private fun metricText(v: Double, metric: StrengthMetric, unit: WeightUnit): String = when (metric) {
    StrengthMetric.E1RM -> Format.weight(Units.roundTo(v, 0.5), unit)
    StrengthMetric.REPS -> "${v.roundToInt()} reps"
    StrengthMetric.HOLD -> Format.duration(v.toLong())
}

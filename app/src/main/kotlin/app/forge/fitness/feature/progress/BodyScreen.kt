package app.forge.fitness.feature.progress

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.MonitorWeight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.forge.domain.calc.Units
import app.forge.domain.model.BodyMetricKind
import app.forge.domain.model.WeightUnit
import app.forge.fitness.data.analytics.AnalyticsRepository
import app.forge.fitness.data.db.BodyMetricEntity
import app.forge.fitness.data.prefs.UserPreferencesRepository
import app.forge.fitness.ui.charts.LineChart
import app.forge.fitness.ui.components.EmptyState
import app.forge.fitness.ui.components.ForgeCard
import app.forge.fitness.ui.components.LocalSnackbarHostState
import app.forge.fitness.ui.components.TextInputDialog
import app.forge.fitness.ui.components.showUndo
import app.forge.fitness.ui.format.Format
import app.forge.fitness.ui.theme.Spacing
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class BodyState(val metrics: List<BodyMetricEntity> = emptyList(), val unit: WeightUnit = WeightUnit.KG)

@HiltViewModel
class BodyViewModel @Inject constructor(
    private val analytics: AnalyticsRepository,
    preferences: UserPreferencesRepository,
) : ViewModel() {
    val state: StateFlow<BodyState> = combine(analytics.observeBodyMetrics(), preferences.preferences) { m, p -> BodyState(m, p.weightUnit) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BodyState())

    fun log(kind: BodyMetricKind, value: Double) {
        viewModelScope.launch { analytics.logMeasurement(kind, value) }
    }

    fun delete(id: String) {
        viewModelScope.launch { analytics.deleteMeasurement(id) }
    }

    fun restore(id: String) {
        viewModelScope.launch { analytics.restoreMeasurement(id) }
    }
}

/** Bodyweight chart plus any body measurements you track. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BodyScreen(onBack: () -> Unit, vm: BodyViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    var kind by remember { mutableStateOf(BodyMetricKind.WEIGHT) }
    var logging by remember { mutableStateOf(false) }
    val snackbar = LocalSnackbarHostState.current
    val scope = rememberCoroutineScope()
    val unit = state.unit
    val entries = state.metrics.filter { it.kind == kind }
    fun show(e: BodyMetricEntity) =
        if (e.kind == BodyMetricKind.WEIGHT) Format.weight(e.value, unit) else "${Units.format(e.value)} ${e.kind.unit}"

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Body") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { logging = true }, icon = { Icon(Icons.Rounded.Add, null) }, text = { Text("Log ${kind.label.lowercase()}") })
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = Spacing.screen, end = Spacing.screen,
                top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + Spacing.xxl * 3,
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            item(key = "kinds") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    items(BodyMetricKind.entries) { k ->
                        FilterChip(selected = k == kind, onClick = { kind = k }, label = { Text(k.label) })
                    }
                }
            }
            if (entries.isEmpty()) {
                item(key = "empty") {
                    EmptyState(Icons.Rounded.MonitorWeight, "No ${kind.label.lowercase()} entries", "Tap \"Log\" to add your first one.")
                }
            } else {
                if (entries.size >= 2) {
                    item(key = "chart") {
                        ForgeCard {
                            LineChart(
                                points = entries.map { it.measuredAt to (if (kind == BodyMetricKind.WEIGHT) app.forge.domain.calc.Units.fromKg(it.value, unit) else it.value) }.reversed(),
                                formatValue = { v -> if (kind == BodyMetricKind.WEIGHT) "${Units.format(Units.roundTo(v, 0.1))} ${unit.symbol}" else "${Units.format(Units.roundTo(v, 0.1))} ${kind.unit}" },
                                formatTime = ::shortDate,
                                description = "${kind.label} over time",
                            )
                        }
                    }
                }
                items(entries, key = { it.id }) { e ->
                    ListItem(
                        headlineContent = { Text(show(e)) },
                        supportingContent = { Text(shortDate(e.measuredAt) + if (e.isDemo) " · demo" else "") },
                        trailingContent = {
                            IconButton(onClick = {
                                vm.delete(e.id)
                                scope.launch { snackbar.showUndo("Entry deleted") { vm.restore(e.id) } }
                            }) { Icon(Icons.Rounded.DeleteOutline, "Delete entry") }
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                }
            }
        }
    }

    if (logging) {
        val isWeight = kind == BodyMetricKind.WEIGHT
        TextInputDialog(
            title = "Log ${kind.label.lowercase()}",
            initial = "",
            keyboardType = KeyboardType.Decimal,
            suffix = if (isWeight) unit.symbol else kind.unit,
            onConfirm = { text ->
                val value = if (isWeight) Format.parseWeight(text, unit) else text.replace(',', '.').toDoubleOrNull()
                if (value != null && value > 0) vm.log(kind, value)
                logging = false
            },
            onDismiss = { logging = false },
        )
    }
}

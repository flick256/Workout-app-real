package app.forge.fitness.feature.ai

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import app.forge.domain.insights.FindingKind
import app.forge.domain.insights.StallReport
import app.forge.fitness.data.ai.AiAssistant
import app.forge.fitness.data.ai.InsightText
import app.forge.fitness.data.insights.InsightsRepository
import app.forge.fitness.ui.components.ForgeCard
import app.forge.fitness.ui.navigation.ExerciseProgressRoute
import app.forge.fitness.ui.theme.Spacing
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class InsightState(
    val loading: Boolean = true,
    /** Forge's own version, always available. */
    val plain: String = "",
    val ai: InsightText? = null,
    val aiAvailable: Boolean = false,
    val generating: Boolean = false,
    val showAi: Boolean = true,
)

@HiltViewModel
class WeeklySummaryViewModel @Inject constructor(
    private val insights: InsightsRepository,
    private val assistant: AiAssistant,
) : ViewModel() {
    private val _state = MutableStateFlow(InsightState())
    val state: StateFlow<InsightState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val facts = insights.weeklyFacts()
            _state.value = InsightState(loading = false, plain = app.forge.domain.insights.WeeklyReport.template(facts), aiAvailable = assistant.isAvailable)
        }
    }

    fun writeWithAi() {
        if (_state.value.generating) return
        _state.update { it.copy(generating = true) }
        viewModelScope.launch {
            val result = assistant.weeklySummary(insights.weeklyFacts())
            _state.update { it.copy(ai = result, generating = false, showAi = true) }
        }
    }

    fun toggle() = _state.update { it.copy(showAi = !it.showAi) }
}

@HiltViewModel
class PlateauViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val insights: InsightsRepository,
    private val assistant: AiAssistant,
) : ViewModel() {
    private val exerciseId = savedStateHandle.toRoute<ExerciseProgressRoute>().exerciseId
    private val _state = MutableStateFlow(InsightState())
    val state: StateFlow<InsightState> = _state.asStateFlow()
    private var report: StallReport? = null

    /** Whether there's anything worth explaining (a plateau or a drop). */
    val worthExplaining: Boolean get() = report?.findings?.any { it.kind != FindingKind.NOT_ENOUGH_DATA && it.kind != FindingKind.PROGRESSING } == true

    init {
        viewModelScope.launch {
            val r = insights.stallReport(exerciseId)
            report = r
            _state.value = InsightState(loading = false, plain = r?.asText().orEmpty(), aiAvailable = assistant.isAvailable)
        }
    }

    fun explainWithAi(exerciseName: String) {
        val r = report ?: return
        if (_state.value.generating) return
        _state.update { it.copy(generating = true) }
        viewModelScope.launch {
            val result = assistant.explainStall(exerciseName, r)
            _state.update { it.copy(ai = result, generating = false, showAi = true) }
        }
    }

    fun toggle() = _state.update { it.copy(showAi = !it.showAi) }
}

/** "Your week": Forge's summary, optionally reworded by the on-device AI. */
@Composable
fun WeeklySummaryCard(vm: WeeklySummaryViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    if (state.loading) return
    InsightCard(
        title = "Your last 7 days",
        state = state,
        aiButton = "Write it with AI",
        onAi = vm::writeWithAi,
        onToggle = vm::toggle,
    )
}

/** "Why has it stalled?" on an exercise's progress page. */
@Composable
fun PlateauCard(exerciseName: String, vm: PlateauViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    if (state.loading || state.plain.isBlank()) return
    InsightCard(
        title = "Plateau check",
        state = state,
        aiButton = if (vm.worthExplaining) "Explain with AI" else null,
        onAi = { vm.explainWithAi(exerciseName) },
        onToggle = vm::toggle,
    )
}

@Composable
private fun InsightCard(title: String, state: InsightState, aiButton: String?, onAi: () -> Unit, onToggle: () -> Unit) {
    val ai = state.ai
    val showingAi = ai != null && ai.byAi && state.showAi
    ForgeCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(if (showingAi) Icons.Rounded.AutoAwesome else Icons.Rounded.Insights, null, tint = MaterialTheme.colorScheme.secondary)
            Text("  $title", style = MaterialTheme.typography.titleMedium)
        }
        Text(
            if (showingAi) ai!!.text else state.plain,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = Spacing.xs),
        )
        if (showingAi) {
            Text(
                "Written by the on-device AI from your numbers, and checked by Forge.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.xs),
            )
        }
        ai?.note?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary, modifier = Modifier.padding(top = Spacing.xs))
        }
        when {
            state.generating -> Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = Spacing.sm)) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Text("  Thinking on your phone… (first time takes a few seconds)", style = MaterialTheme.typography.bodySmall)
            }
            ai != null && ai.byAi -> TextButton(onClick = onToggle) { Text(if (state.showAi) "Show Forge's plain version" else "Show the AI version") }
            aiButton != null && state.aiAvailable -> FilledTonalButton(onClick = onAi, modifier = Modifier.padding(top = Spacing.sm)) {
                Icon(Icons.Rounded.AutoAwesome, null)
                Text(" $aiButton")
            }
        }
    }
}

package app.forge.fitness.feature.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.forge.domain.model.WeightUnit
import app.forge.fitness.data.db.SessionExerciseLine
import app.forge.fitness.data.db.SessionSummaryRow
import app.forge.fitness.data.prefs.UserPreferencesRepository
import app.forge.fitness.data.workout.WorkoutRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HistoryItem(val summary: SessionSummaryRow, val lines: List<SessionExerciseLine>)

data class HistoryState(
    val loading: Boolean = true,
    val months: List<Pair<YearMonth, List<HistoryItem>>> = emptyList(),
    val unit: WeightUnit = WeightUnit.KG,
)

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val repository: WorkoutRepository,
    preferences: UserPreferencesRepository,
) : ViewModel() {

    val state: StateFlow<HistoryState> = combine(
        repository.observeHistory(),
        repository.observeHistoryLines(),
        preferences.preferences,
    ) { sessions, lines, prefs ->
        val linesBySession = lines.groupBy { it.sessionId }
        val zone = ZoneId.systemDefault()
        HistoryState(
            loading = false,
            months = sessions
                .map { HistoryItem(it, linesBySession[it.id].orEmpty().sortedBy { l -> l.position }) }
                .groupBy { YearMonth.from(Instant.ofEpochMilli(it.summary.startedAt).atZone(zone)) }
                .toList(),
            unit = prefs.weightUnit,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryState())

    fun delete(id: String) = viewModelScope.launch { repository.deleteSession(id) }

    fun restore(id: String) = viewModelScope.launch { repository.restoreSession(id) }
}

package app.forge.fitness.feature.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.forge.domain.model.WeightUnit
import app.forge.fitness.data.activity.ActivityRepository
import app.forge.fitness.data.db.ActivitySessionEntity
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

/** One row in History: a Forge workout or a sport/cardio activity. */
sealed interface HistoryEntry {
    val startedAt: Long
    val key: String

    data class Workout(val item: HistoryItem) : HistoryEntry {
        override val startedAt get() = item.summary.startedAt
        override val key get() = item.summary.id
    }

    data class Activity(val activity: ActivitySessionEntity) : HistoryEntry {
        override val startedAt get() = activity.startedAt
        override val key get() = "a-" + activity.id
    }
}

data class HistoryState(
    val loading: Boolean = true,
    val months: List<Pair<YearMonth, List<HistoryEntry>>> = emptyList(),
    val unit: WeightUnit = WeightUnit.KG,
)

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val repository: WorkoutRepository,
    activities: ActivityRepository,
    preferences: UserPreferencesRepository,
) : ViewModel() {

    val state: StateFlow<HistoryState> = combine(
        repository.observeHistory(),
        repository.observeHistoryLines(),
        activities.observeActivities(),
        preferences.preferences,
    ) { sessions, lines, others, prefs ->
        val linesBySession = lines.groupBy { it.sessionId }
        val zone = ZoneId.systemDefault()
        HistoryState(
            loading = false,
            months = (
                sessions.map {
                    HistoryEntry.Workout(HistoryItem(it, linesBySession[it.id].orEmpty().sortedBy { l -> l.position }))
                } + others.map { HistoryEntry.Activity(it) }
                )
                .sortedByDescending { it.startedAt }
                .groupBy { YearMonth.from(Instant.ofEpochMilli(it.startedAt).atZone(zone)) }
                .toList(),
            unit = prefs.weightUnit,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryState())

    fun delete(id: String) = viewModelScope.launch { repository.deleteSession(id) }

    fun restore(id: String) = viewModelScope.launch { repository.restoreSession(id) }
}

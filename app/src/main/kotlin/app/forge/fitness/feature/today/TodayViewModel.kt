package app.forge.fitness.feature.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.forge.domain.model.WeightUnit
import app.forge.fitness.data.db.SessionSummaryRow
import app.forge.fitness.data.db.WorkoutSessionEntity
import app.forge.fitness.data.prefs.UserPreferencesRepository
import app.forge.fitness.data.workout.WorkoutRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class TodayState(
    val active: WorkoutSessionEntity? = null,
    val lastWorkout: SessionSummaryRow? = null,
    val workoutsThisWeek: Int = 0,
    val unit: WeightUnit = WeightUnit.KG,
)

@HiltViewModel
class TodayViewModel @Inject constructor(
    private val repository: WorkoutRepository,
    preferences: UserPreferencesRepository,
) : ViewModel() {

    val state: StateFlow<TodayState> = combine(
        repository.observeActiveSession(),
        repository.observeHistory(),
        preferences.preferences,
    ) { active, history, prefs ->
        val weekAgo = System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000
        TodayState(
            active = active,
            lastWorkout = history.firstOrNull(),
            workoutsThisWeek = history.count { it.startedAt >= weekAgo },
            unit = prefs.weightUnit,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayState())

    /** Starts a new workout, or returns the one in progress. */
    suspend fun startWorkout(): String = repository.startOrResume()
}

package app.forge.fitness.feature.goals

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.forge.domain.goals.DayMask
import app.forge.domain.goals.GoalKind
import app.forge.domain.goals.HabitKind
import app.forge.fitness.data.db.ExerciseDao
import app.forge.fitness.data.db.ExerciseEntity
import app.forge.fitness.data.goals.GoalsOverview
import app.forge.fitness.data.goals.GoalsRepository
import app.forge.fitness.reminders.ReminderScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class GoalsViewModel @Inject constructor(
    private val repository: GoalsRepository,
    private val reminders: ReminderScheduler,
    exercises: ExerciseDao,
) : ViewModel() {

    val state: StateFlow<GoalsOverview> = repository.observeOverview()
        .onEach { overview ->
            // Remember when a one-off goal is first reached (for the "Goal getter" achievement).
            overview.goals.filter { it.progress.done && it.goal.reachedAt == null && it.kind in ONE_OFF }
                .forEach { repository.markReached(it.goal.id) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GoalsOverview())

    /** For strength/reps goals: exercises you've actually logged first. */
    val exercises: StateFlow<List<ExerciseEntity>> = exercises.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun toggleHabit(id: String) = launch { repository.toggleToday(id) }

    fun saveHabit(id: String?, kind: HabitKind, name: String, target: Double?, due: DayMask, reminderMinutes: Int?) = launch {
        val saved = repository.saveHabit(id, kind, name, target, due, reminderMinutes)
        repository.getHabit(saved)?.let { reminders.schedule(it) }
    }

    fun deleteHabit(id: String) = launch {
        repository.deleteHabit(id)
        reminders.cancel(id)
    }

    fun restoreHabit(id: String) = launch {
        repository.restoreHabit(id)
        repository.getHabit(id)?.let { reminders.schedule(it) }
    }

    fun addGoal(kind: GoalKind, target: Double, exerciseId: String?) = launch {
        val start = if (kind == GoalKind.BODYWEIGHT) repository.latestWeightKg() else null
        repository.addGoal(kind, target, exerciseId, start)
    }

    fun deleteGoal(id: String) = launch { repository.deleteGoal(id) }

    fun restoreGoal(id: String) = launch { repository.restoreGoal(id) }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    private companion object {
        val ONE_OFF = setOf(GoalKind.BODYWEIGHT, GoalKind.LIFT, GoalKind.REPS)
    }
}

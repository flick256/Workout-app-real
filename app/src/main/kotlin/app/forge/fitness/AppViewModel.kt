package app.forge.fitness

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.forge.domain.model.ThemeMode
import app.forge.fitness.data.db.WorkoutSessionEntity
import app.forge.fitness.data.prefs.UserPreferencesRepository
import app.forge.fitness.data.routine.RoutineRepository
import app.forge.fitness.data.workout.WorkoutRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** App-wide state the activity needs before drawing anything (currently the theme). */
@HiltViewModel
class AppViewModel @Inject constructor(
    private val preferences: UserPreferencesRepository,
    private val workouts: WorkoutRepository,
    private val routines: RoutineRepository,
) : ViewModel() {
    /** Null until preferences are read; the splash screen stays up until then. */
    val themeMode: StateFlow<ThemeMode?> = preferences.preferences
        .map { it.themeMode }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /**
     * Null until known. First-run setup shows on a fresh install only: if you already have
     * workouts (an upgrade from an earlier version), it's marked done and skipped.
     */
    val needsSetup: StateFlow<Boolean?> = preferences.preferences
        .map { p ->
            when {
                p.setupDone -> false
                workouts.hasAnyWorkouts() -> { preferences.setSetupDone(); false }
                else -> true
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val active = workouts.observeActiveSession()
        .map { ActiveWorkout(loaded = true, session = it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ActiveWorkout(loaded = false, session = null))

    /** The workout in progress, if any (for the "Resume" bar above the tabs). */
    val activeWorkout: StateFlow<ActiveWorkout> = active

    /** From a widget or shortcut: starts an empty workout (or resumes the one running). */
    suspend fun startWorkout() {
        workouts.startOrResume()
    }

    /** Starts today's routine, unless a workout is already running (that one is resumed). */
    suspend fun startRoutine(routineId: String) {
        val routine = routines.getRoutineWithExercises(routineId) ?: return startWorkout()
        workouts.startFromRoutine(routine)
    }
}

/** [loaded] is false until the database has answered, so "no workout" isn't assumed too early. */
data class ActiveWorkout(val loaded: Boolean, val session: WorkoutSessionEntity?)

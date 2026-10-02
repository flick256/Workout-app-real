package app.forge.fitness

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.forge.domain.model.ThemeMode
import app.forge.fitness.data.db.WorkoutSessionEntity
import app.forge.fitness.data.prefs.UserPreferencesRepository
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
    preferences: UserPreferencesRepository,
    workouts: WorkoutRepository,
) : ViewModel() {
    /** Null until preferences are read; the splash screen stays up until then. */
    val themeMode: StateFlow<ThemeMode?> = preferences.preferences
        .map { it.themeMode }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val active = workouts.observeActiveSession()
        .map { ActiveWorkout(loaded = true, session = it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ActiveWorkout(loaded = false, session = null))

    /** The workout in progress, if any (for the "Resume" bar above the tabs). */
    val activeWorkout: StateFlow<ActiveWorkout> = active
}

/** [loaded] is false until the database has answered, so "no workout" isn't assumed too early. */
data class ActiveWorkout(val loaded: Boolean, val session: WorkoutSessionEntity?)

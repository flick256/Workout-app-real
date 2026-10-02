package app.forge.fitness.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.forge.domain.model.Equipment
import app.forge.domain.model.ThemeMode
import app.forge.domain.model.WeightUnit
import app.forge.fitness.data.prefs.UserPreferences
import app.forge.fitness.data.prefs.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: UserPreferencesRepository,
) : ViewModel() {

    val preferences: StateFlow<UserPreferences> = repository.preferences
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserPreferences())

    fun setThemeMode(mode: ThemeMode) = launch { repository.setThemeMode(mode) }

    fun setWeightUnit(unit: WeightUnit) = launch { repository.setWeightUnit(unit) }

    fun setDefaultRest(seconds: Int) = launch { repository.setDefaultRestSeconds(seconds) }

    fun toggleEquipment(item: Equipment) = launch {
        val current = preferences.value.equipment
        // Bodyweight is always available: you can't un-own your body.
        if (item == Equipment.BODY_ONLY) return@launch
        repository.setEquipment(if (item in current) current - item else current + item)
    }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}

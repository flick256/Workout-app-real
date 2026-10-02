package app.forge.fitness.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.forge.domain.model.Equipment
import app.forge.domain.model.ThemeMode
import app.forge.domain.model.WeightUnit
import android.net.Uri
import app.forge.domain.model.BodyMetricKind
import app.forge.fitness.data.backup.JsonExporter
import app.forge.fitness.data.db.BodyMetricDao
import app.forge.fitness.data.db.BodyMetricEntity
import app.forge.fitness.data.workout.WorkoutRepository
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
    private val exporter: JsonExporter,
    private val workouts: WorkoutRepository,
    bodyMetrics: BodyMetricDao,
) : ViewModel() {

    /** Your most recent bodyweight entry. */
    val latestBodyweight: StateFlow<BodyMetricEntity?> = bodyMetrics.observeLatest(BodyMetricKind.WEIGHT)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun logBodyweight(kg: Double) = launch { workouts.logBodyweight(kg) }

    fun setHeight(cm: Double?) = launch { repository.setHeightCm(cm) }


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

    fun setOwnedWeights(item: Equipment, weightsKg: List<Double>) = launch {
        repository.setOwnedWeights(item, weightsKg)
    }

    /** Writes a full JSON export to [uri]; returns a message for the snackbar. */
    suspend fun export(uri: Uri): String = runCatching { exporter.exportTo(uri) }.fold(
        onSuccess = { "Exported ${it.workouts} workouts (${it.sets} sets)" },
        onFailure = { "Export failed: ${it.message ?: "unknown error"}" },
    )

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}

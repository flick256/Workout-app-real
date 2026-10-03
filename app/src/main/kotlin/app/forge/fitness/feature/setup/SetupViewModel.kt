package app.forge.fitness.feature.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.forge.domain.model.Equipment
import app.forge.domain.model.WeightUnit
import app.forge.domain.nutrition.ActivityLevel
import app.forge.domain.nutrition.NutritionGoal
import app.forge.domain.nutrition.Sex
import app.forge.fitness.data.analytics.DemoDataLoader
import app.forge.fitness.data.prefs.UserPreferences
import app.forge.fitness.data.prefs.UserPreferencesRepository
import app.forge.fitness.data.workout.WorkoutRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Everything first-run setup asks. All optional; the defaults are what Forge used before. */
data class SetupAnswers(
    val unit: WeightUnit = WeightUnit.KG,
    val equipment: Set<Equipment> = UserPreferences.DEFAULT_EQUIPMENT,
    /** As typed, in [unit]. */
    val bodyweight: String = "",
    val heightCm: String = "",
    val birthYear: String = "",
    val sex: Sex? = null,
    val goal: NutritionGoal = NutritionGoal.MAINTAIN,
    val activity: ActivityLevel = ActivityLevel.MODERATE,
    val connectStrap: Boolean = false,
    val demoData: Boolean = false,
)

@HiltViewModel
class SetupViewModel @Inject constructor(
    private val preferences: UserPreferencesRepository,
    private val workouts: WorkoutRepository,
    private val demo: DemoDataLoader,
) : ViewModel() {
    private val _answers = MutableStateFlow(SetupAnswers())
    val answers: StateFlow<SetupAnswers> = _answers.asStateFlow()
    private val _saving = MutableStateFlow(false)
    val saving: StateFlow<Boolean> = _saving.asStateFlow()

    fun edit(change: (SetupAnswers) -> SetupAnswers) = _answers.update(change)

    /** Saves what was answered (bad or blank fields are left unset) and closes setup. */
    fun finish(skipped: Boolean, onDone: (connectStrap: Boolean) -> Unit) {
        if (_saving.value) return
        _saving.value = true
        viewModelScope.launch {
            val a = _answers.value
            if (!skipped) {
                preferences.setWeightUnit(a.unit)
                preferences.setEquipment(a.equipment.ifEmpty { setOf(Equipment.BODY_ONLY) })
                parseNumber(a.heightCm)?.takeIf { it in 100.0..250.0 }?.let { preferences.setHeightCm(it) }
                a.birthYear.trim().toIntOrNull()?.takeIf { it in 1920..2020 }?.let { preferences.setBirthYear(it) }
                a.sex?.let { preferences.setSex(it) }
                preferences.setNutritionGoal(a.goal)
                preferences.setActivityLevel(a.activity)
                parseNumber(a.bodyweight)
                    ?.let { if (a.unit == WeightUnit.LB) it * LB_TO_KG else it }
                    ?.takeIf { it in 20.0..400.0 }
                    ?.let { workouts.logBodyweight(it) }
                if (a.demoData) runCatching { demo.load() }
            }
            preferences.setSetupDone()
            onDone(!skipped && a.connectStrap)
        }
    }

    private fun parseNumber(text: String) = text.trim().replace(',', '.').toDoubleOrNull()

    private companion object {
        const val LB_TO_KG = 0.45359237
    }
}

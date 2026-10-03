package app.forge.fitness.feature.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.forge.domain.calc.Units
import app.forge.domain.model.Equipment
import app.forge.domain.model.WeightUnit
import app.forge.domain.nutrition.ActivityLevel
import app.forge.domain.nutrition.NutritionGoal
import app.forge.domain.nutrition.Sex
import app.forge.fitness.data.analytics.DemoDataLoader
import app.forge.fitness.data.exercise.ExerciseSeeder
import app.forge.fitness.data.prefs.UserPreferences
import app.forge.fitness.data.prefs.UserPreferencesRepository
import app.forge.fitness.data.workout.WorkoutRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
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
) {
    /** Switching units converts a bodyweight already typed, so 180 lb doesn't become 180 kg. */
    fun withUnit(new: WeightUnit): SetupAnswers {
        val value = parseNumber(bodyweight) ?: return copy(unit = new)
        val converted = Units.fromKg(Units.toKg(value, unit), new)
        return copy(unit = new, bodyweight = Units.format(Units.roundTo(converted, 0.1)))
    }
}

private fun parseNumber(text: String) = text.trim().replace(',', '.').toDoubleOrNull()

@HiltViewModel
class SetupViewModel @Inject constructor(
    private val preferences: UserPreferencesRepository,
    private val workouts: WorkoutRepository,
    private val demo: DemoDataLoader,
    private val seeder: ExerciseSeeder,
) : ViewModel() {
    private val _answers = MutableStateFlow(SetupAnswers())
    val answers: StateFlow<SetupAnswers> = _answers.asStateFlow()
    private val _saving = MutableStateFlow(false)
    val saving: StateFlow<Boolean> = _saving.asStateFlow()

    /** Set when setup is saved: whether to open strap setup next. Survives rotation. */
    private val _done = MutableStateFlow<Boolean?>(null)
    val done: StateFlow<Boolean?> = _done.asStateFlow()

    init {
        // Start from whatever is already set, so finishing never resets a setting to a default.
        viewModelScope.launch {
            val p = preferences.preferences.first()
            _answers.update {
                it.copy(
                    unit = p.weightUnit,
                    equipment = p.equipment,
                    heightCm = p.heightCm?.let { cm -> Units.format(cm) }.orEmpty(),
                    birthYear = p.birthYear?.toString().orEmpty(),
                    sex = p.sex,
                    goal = p.nutritionGoal,
                    activity = p.activityLevel,
                )
            }
        }
    }

    fun edit(change: (SetupAnswers) -> SetupAnswers) = _answers.update(change)

    /** Saves what was answered (bad or blank fields are left unset) and closes setup. */
    fun finish(skipped: Boolean) {
        if (_saving.value) return
        _saving.value = true
        viewModelScope.launch {
            val a = _answers.value
            if (!skipped) {
                preferences.setWeightUnit(a.unit)
                preferences.setEquipment(a.equipment.ifEmpty { setOf(Equipment.BODY_ONLY) })
                parseNumber(a.heightCm)?.takeIf { it in 100.0..250.0 }?.let { preferences.setHeightCm(it) }
                a.birthYear.trim().toIntOrNull()
                    ?.takeIf { it in 1920..LocalDate.now().year - 10 }
                    ?.let { preferences.setBirthYear(it) }
                a.sex?.let { preferences.setSex(it) }
                preferences.setNutritionGoal(a.goal)
                preferences.setActivityLevel(a.activity)
                parseNumber(a.bodyweight)
                    ?.let { Units.toKg(it, a.unit) }
                    ?.takeIf { it in 20.0..400.0 }
                    ?.let { workouts.logBodyweight(it) }
                if (a.demoData) {
                    // Demo workouts need the exercise library, which may still be loading on a first launch.
                    runCatching { seeder.seedIfNeeded(); demo.load() }
                }
            }
            preferences.setSetupDone()
            _done.value = !skipped && a.connectStrap
        }
    }
}

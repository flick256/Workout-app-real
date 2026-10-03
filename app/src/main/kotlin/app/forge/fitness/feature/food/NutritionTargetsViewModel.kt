package app.forge.fitness.feature.food

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.forge.domain.model.BodyMetricKind
import app.forge.domain.nutrition.ActivityLevel
import app.forge.domain.nutrition.NutritionGoal
import app.forge.domain.nutrition.Sex
import app.forge.fitness.data.db.BodyMetricDao
import app.forge.fitness.data.nutrition.FoodRepository
import app.forge.fitness.data.nutrition.TargetsState
import app.forge.fitness.data.prefs.CustomTargets
import app.forge.fitness.data.prefs.UserPreferences
import app.forge.fitness.data.prefs.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class TargetsScreenState(
    val loaded: Boolean = false,
    val prefs: UserPreferences = UserPreferences(),
    val weightKg: Double? = null,
    val targets: TargetsState = TargetsState.Missing(emptyList()),
    /** What the formula gives, even when you've set your own numbers. */
    val calculated: TargetsState = TargetsState.Missing(emptyList()),
)

@HiltViewModel
class NutritionTargetsViewModel @Inject constructor(
    private val preferences: UserPreferencesRepository,
    bodyMetrics: BodyMetricDao,
) : ViewModel() {

    val state: StateFlow<TargetsScreenState> = combine(
        preferences.preferences,
        bodyMetrics.observeLatest(BodyMetricKind.WEIGHT),
    ) { prefs, weight ->
        val year = LocalDate.now().year
        TargetsScreenState(
            loaded = true,
            prefs = prefs,
            weightKg = weight?.value,
            targets = FoodRepository.targetsFor(prefs, weight?.value, year),
            calculated = FoodRepository.targetsFor(prefs.copy(customTargets = null), weight?.value, year),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TargetsScreenState())

    fun setSex(sex: Sex) = launch { preferences.setSex(sex) }
    fun setBirthYear(year: Int?) = launch { preferences.setBirthYear(year) }
    fun setHeight(cm: Double?) = launch { preferences.setHeightCm(cm) }
    fun setActivity(level: ActivityLevel) = launch { preferences.setActivityLevel(level) }
    fun setGoal(goal: NutritionGoal) = launch { preferences.setNutritionGoal(goal) }
    fun setCustom(targets: CustomTargets?) = launch { preferences.setCustomTargets(targets) }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}

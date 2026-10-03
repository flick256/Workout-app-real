package app.forge.fitness.feature.food

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import app.forge.domain.nutrition.Meal
import app.forge.domain.nutrition.Nutrients
import app.forge.fitness.data.db.FoodLogEntity
import app.forge.fitness.data.nutrition.FoodRepository
import app.forge.fitness.data.nutrition.TargetsState
import app.forge.fitness.data.nutrition.total
import app.forge.fitness.ui.navigation.FoodRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

data class FoodDayState(
    val loaded: Boolean = false,
    val day: LocalDate = LocalDate.now(),
    val meals: Map<Meal, List<FoodLogEntity>> = emptyMap(),
    val total: Nutrients = Nutrients.ZERO,
    val targets: TargetsState = TargetsState.Missing(emptyList()),
) {
    val isToday: Boolean get() = day == LocalDate.now()
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class FoodDayViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: FoodRepository,
) : ViewModel() {

    private val day = MutableStateFlow(
        savedStateHandle.toRoute<FoodRoute>().epochDay?.let(LocalDate::ofEpochDay) ?: LocalDate.now(),
    )

    val state: StateFlow<FoodDayState> = combine(
        day,
        day.flatMapLatest { repository.observeDay(it) },
        repository.observeTargets(),
    ) { d, entries, targets ->
        FoodDayState(
            loaded = true,
            day = d,
            meals = entries.filter { it.epochDay == d.toEpochDay() }.groupBy { Meal.fromKey(it.meal) },
            total = entries.filter { it.epochDay == d.toEpochDay() }.total(),
            targets = targets,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FoodDayState())

    fun previousDay() { day.value = day.value.minusDays(1) }

    fun nextDay() { if (day.value < LocalDate.now()) day.value = day.value.plusDays(1) }

    fun goToToday() { day.value = LocalDate.now() }

    suspend fun updateEntry(id: String, grams: Double?, meal: Meal) = repository.updateEntry(id, grams, meal)

    suspend fun deleteEntry(id: String) = repository.deleteEntry(id)

    suspend fun restoreEntry(id: String) = repository.restoreEntry(id)

    /** Copies [meal] from the day before into the day being viewed. */
    suspend fun copyFromPreviousDay(meal: Meal): Int = repository.copyMeal(state.value.day.minusDays(1), meal, state.value.day)
}

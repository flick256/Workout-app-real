package app.forge.fitness.feature.food

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import app.forge.domain.nutrition.FoodInfo
import app.forge.domain.nutrition.Meal
import app.forge.domain.nutrition.Nutrients
import app.forge.fitness.data.db.FoodEntity
import app.forge.fitness.data.nutrition.BarcodeResult
import app.forge.fitness.data.nutrition.FoodRepository
import app.forge.fitness.data.nutrition.SearchResult
import app.forge.fitness.ui.navigation.FoodAddRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.debounce
import app.forge.fitness.data.nutrition.GenericFoodsRepository
import app.forge.domain.nutrition.GenericMatches
import app.forge.domain.nutrition.GenericFood
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface OnlineState {
    data object Idle : OnlineState
    data object Loading : OnlineState
    data class Results(val query: String, val foods: List<FoodInfo>) : OnlineState
    data class Error(val message: String) : OnlineState
}

/** Something the screen should tell you about after a scan. */
sealed interface ScanOutcome {
    data class NotFound(val barcode: String) : ScanOutcome
    data class Problem(val message: String) : ScanOutcome
}

data class FoodAddState(
    val query: String = "",
    val results: List<FoodEntity> = emptyList(),
    val recent: List<FoodEntity> = emptyList(),
    val favorites: List<FoodEntity> = emptyList(),
    val online: OnlineState = OnlineState.Idle,
    val busy: Boolean = false,
    /** From the bundled Australian food database (works offline). */
    val generic: GenericMatches = GenericMatches(emptyList()),
)

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class FoodAddViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: FoodRepository,
    private val genericFoods: GenericFoodsRepository,
) : ViewModel() {

    private val route = savedStateHandle.toRoute<FoodAddRoute>()
    val day: LocalDate = LocalDate.ofEpochDay(route.epochDay)
    val meal: Meal = Meal.fromKey(route.meal)

    private val query = MutableStateFlow("")
    private val online = MutableStateFlow<OnlineState>(OnlineState.Idle)
    private val busy = MutableStateFlow(false)
    private var onlineJob: Job? = null

    /** The food whose amount sheet is open. */
    val selected = MutableStateFlow<FoodEntity?>(null)
    val scanOutcome = MutableStateFlow<ScanOutcome?>(null)

    val state: StateFlow<FoodAddState> = combine(
        query,
        query.flatMapLatest { q -> if (q.isBlank()) flowOf(emptyList()) else repository.search(q) },
        repository.observeRecent(),
        repository.observeFavorites(),
        combine(
            online,
            busy,
            // Short pause so typing doesn't search on every letter.
            query.debounce(150).mapLatest { q -> if (q.trim().length < 2) GenericMatches(emptyList()) else genericFoods.search(q) },
            ::Triple,
        ),
    ) { q, results, recent, favorites, (onlineState, isBusy, generic) ->
        // Ones you've already saved show under "On your phone" instead.
        val saved = results.map { it.id }.toSet()
        FoodAddState(q, results, recent, favorites, onlineState, isBusy, generic.copy(foods = generic.foods.filter { "ausnut-${it.key}" !in saved }))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FoodAddState())

    fun setQuery(value: String) {
        query.value = value
        if (online.value !is OnlineState.Loading) online.value = OnlineState.Idle
    }

    /** Searches Open Food Facts (only when you ask, to respect its rate limits). */
    fun searchOnline() {
        val q = query.value.trim()
        if (q.length < 2) return
        onlineJob?.cancel()
        online.value = OnlineState.Loading
        onlineJob = viewModelScope.launch {
            online.value = when (val r = repository.searchOnline(q)) {
                is SearchResult.Results -> OnlineState.Results(q, r.foods)
                is SearchResult.Failed -> OnlineState.Error(r.message)
            }
        }
    }

    fun pickOnline(info: FoodInfo) {
        viewModelScope.launch { selected.value = repository.saveFromCatalog(info) }
    }

    fun pick(food: FoodEntity) { selected.value = food }

    fun pickGeneric(food: GenericFood) {
        viewModelScope.launch { selected.value = repository.saveGeneric(food) }
    }

    fun openById(id: String) {
        viewModelScope.launch { repository.getFood(id)?.let { selected.value = it } }
    }

    fun onScanned(code: String) {
        viewModelScope.launch {
            busy.value = true
            when (val r = repository.lookupBarcode(code)) {
                is BarcodeResult.Found -> selected.value = r.food
                is BarcodeResult.NotFound -> scanOutcome.value = ScanOutcome.NotFound(r.barcode)
                is BarcodeResult.Failed -> scanOutcome.value = ScanOutcome.Problem(r.message)
                BarcodeResult.Invalid -> scanOutcome.value = ScanOutcome.Problem("That doesn't look like a food barcode ($code).")
            }
            busy.value = false
        }
    }

    suspend fun log(food: FoodEntity, grams: Double, meal: Meal) {
        repository.log(food, grams, meal, day)
        selected.value = null
    }

    suspend fun quickAdd(name: String, nutrients: Nutrients, meal: Meal) = repository.quickAdd(name, nutrients, meal, day)

    fun toggleFavorite(food: FoodEntity) {
        viewModelScope.launch {
            repository.toggleFavorite(food.id)
            if (selected.value?.id == food.id) selected.value = repository.getFood(food.id)
        }
    }
}

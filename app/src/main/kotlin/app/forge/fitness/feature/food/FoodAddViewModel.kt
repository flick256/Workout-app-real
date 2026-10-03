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
import app.forge.fitness.data.nutrition.MealLogger
import app.forge.fitness.data.nutrition.MealLine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import app.forge.fitness.data.prefs.UserPreferencesRepository
import app.forge.fitness.data.nutrition.WebLookupResult
import app.forge.fitness.data.nutrition.WebFoodLookup
import app.forge.fitness.data.nutrition.WebFood
import kotlinx.coroutines.flow.collectLatest
import app.forge.fitness.data.db.FoodSource
import app.forge.fitness.data.nutrition.FsResult
import app.forge.fitness.data.nutrition.FatSecretClient
import app.forge.domain.nutrition.FatSecretHit
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

/** FatSecret brand/chain search. Hidden until you've added your key. */
sealed interface BrandState {
    data object NotSetUp : BrandState
    data object Idle : BrandState
    data object Loading : BrandState
    data class Results(val query: String, val hits: List<FatSecretHit>) : BrandState
    data class Error(val message: String) : BrandState
}

/** Looking a food up on the web (when the databases don't have it). */
sealed interface WebState {
    data object Off : WebState
    data object Idle : WebState
    data class Loading(val step: String) : WebState
    data class Found(val query: String, val food: WebFood, val more: List<String>) : WebState
    data class NotFound(val message: String) : WebState
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
    val brands: BrandState = BrandState.NotSetUp,
    val web: WebState = WebState.Off,
)

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class FoodAddViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: FoodRepository,
    private val genericFoods: GenericFoodsRepository,
    private val fatSecret: FatSecretClient,
    private val webLookup: WebFoodLookup,
    private val mealLogger: MealLogger,
    preferences: UserPreferencesRepository,
) : ViewModel() {

    private val route = savedStateHandle.toRoute<FoodAddRoute>()
    val day: LocalDate = LocalDate.ofEpochDay(route.epochDay)
    val meal: Meal = Meal.fromKey(route.meal)

    private val query = MutableStateFlow("")
    private val online = MutableStateFlow<OnlineState>(OnlineState.Idle)
    private val brands = MutableStateFlow<BrandState>(BrandState.NotSetUp)
    private val web = MutableStateFlow<WebState>(WebState.Idle)
    private var webJob: Job? = null
    private val webEnabled = preferences.preferences.map { it.webFoodLookup }.distinctUntilChanged()
    private val busy = MutableStateFlow(false)
    private var onlineJob: Job? = null
    private var brandsJob: Job? = null

    /** "Type what you ate": the lines read from your sentence (null until read). */
    val mealLines = MutableStateFlow<List<MealLine>?>(null)
    val mealReading = MutableStateFlow(false)

    fun readMeal(text: String) {
        if (text.isBlank()) return
        mealReading.value = true
        viewModelScope.launch {
            mealLines.value = runCatching { mealLogger.read(text) }.getOrDefault(emptyList())
            mealReading.value = false
        }
    }

    fun chooseMealFood(line: Int, choice: Int) {
        val lines = mealLines.value ?: return
        val target = lines.getOrNull(line) ?: return
        viewModelScope.launch {
            val updated = mealLogger.choose(target, choice)
            mealLines.value = mealLines.value?.toMutableList()?.also { if (line < it.size) it[line] = updated }
        }
    }

    fun setMealGrams(line: Int, grams: Double) {
        mealLines.value = mealLines.value?.toMutableList()?.also { list ->
            list.getOrNull(line)?.let { list[line] = it.copy(grams = grams, explanation = "Your amount") }
        }
    }

    fun removeMealLine(line: Int) {
        mealLines.value = mealLines.value?.toMutableList()?.also { if (line < it.size) it.removeAt(line) }
    }

    fun closeMeal() {
        mealLines.value = null
        mealReading.value = false
    }

    /** Logs every matched line. Returns how many foods were added. */
    suspend fun logMeal(meal: Meal): Int {
        val lines = mealLines.value ?: return 0
        val added = mealLogger.logAll(lines, meal, day)
        closeMeal()
        return added
    }

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
            brands,
            busy,
            // Short pause so typing doesn't search on every letter.
            query.debounce(150).mapLatest { q -> if (q.trim().length < 2) GenericMatches(emptyList()) else genericFoods.search(q) },
            combine(web, webEnabled) { w, on -> if (on) w else WebState.Off },
        ) { o, b, isBusy, generic, w -> Online(o, b, isBusy, generic, w) },
    ) { q, results, recent, favorites, o ->
        // Ones you've already saved show under "On your phone" instead.
        val saved = results.map { it.id }.toSet()
        FoodAddState(
            q, results, recent, favorites, o.off, o.busy,
            o.generic.copy(foods = o.generic.foods.filter { "ausnut-${it.key}" !in saved }),
            o.brands,
            o.web,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FoodAddState())

    private data class Online(
        val off: OnlineState,
        val brands: BrandState,
        val busy: Boolean,
        val generic: GenericMatches,
        val web: WebState,
    )

    init {
        viewModelScope.launch { if (fatSecret.isSetUp()) brands.value = BrandState.Idle }
        // Brands are searched online by themselves once you stop typing for a moment.
        viewModelScope.launch {
            query.debounce(AUTO_SEARCH_MS).collectLatest { q -> if (q.trim().length >= 3) searchOnline() }
        }
    }

    fun setQuery(value: String) {
        query.value = value
        if (online.value !is OnlineState.Loading) online.value = OnlineState.Idle
        if (brands.value !is BrandState.NotSetUp && brands.value !is BrandState.Loading) brands.value = BrandState.Idle
        if (web.value !is WebState.Loading) web.value = WebState.Idle
    }

    /** Searches the web and reads the best nutrition page it finds. You check it before it's saved. */
    fun lookUpOnWeb() {
        val q = query.value.trim()
        if (q.length < 3) return
        webJob?.cancel()
        web.value = WebState.Loading("Searching the web…")
        webJob = viewModelScope.launch {
            web.value = webLookup.lookup(q) { step -> web.value = WebState.Loading(step) }.toState(q)
        }
    }

    /** The answer looked wrong: read the next page from the same search. */
    fun tryNextPage() {
        val found = web.value as? WebState.Found ?: return
        webJob?.cancel()
        web.value = WebState.Loading("Trying the next page…")
        webJob = viewModelScope.launch {
            web.value = webLookup.tryPages(found.query, found.more) { step -> web.value = WebState.Loading(step) }.toState(found.query)
        }
    }

    fun useWebFood() {
        val found = web.value as? WebState.Found ?: return
        viewModelScope.launch {
            val id = "web-" + Integer.toHexString((found.food.url + "|" + found.food.info.name.lowercase()).hashCode())
            selected.value = repository.saveWithId(id, found.food.info, FoodSource.WEB)
            web.value = WebState.Idle
        }
    }

    private fun WebLookupResult.toState(q: String): WebState = when (this) {
        is WebLookupResult.Found -> WebState.Found(q, food, more)
        is WebLookupResult.NotFound -> WebState.NotFound(message)
        is WebLookupResult.Failed -> WebState.NotFound(message)
    }

    /** Searches brands online: FatSecret (if you've added a key) and Open Food Facts, side by side. */
    fun searchOnline() {
        val q = query.value.trim()
        if (q.length < 2) return
        brandsJob?.cancel()
        brandsJob = viewModelScope.launch {
            // Checked each time, so a key added in Settings works as soon as you come back.
            if (!fatSecret.isSetUp()) {
                brands.value = BrandState.NotSetUp
                return@launch
            }
            brands.value = BrandState.Loading
            run {
                brands.value = when (val r = fatSecret.search(q)) {
                    is FsResult.Ok -> BrandState.Results(q, r.value)
                    is FsResult.Failed -> BrandState.Error(r.message)
                    FsResult.NotSetUp -> BrandState.NotSetUp
                }
            }
        }
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

    /** FatSecret's search gives a summary; the full details (with the serving's weight) are fetched on pick. */
    fun pickBrand(hit: FatSecretHit) {
        viewModelScope.launch {
            busy.value = true
            val info = when (val r = fatSecret.food(hit.id)) {
                is FsResult.Ok -> r.value
                else -> hit.toFoodInfo()
            }
            if (info != null) {
                selected.value = repository.saveWithId("fatsecret-${hit.id}", info, FoodSource.FATSECRET)
            } else {
                scanOutcome.value = ScanOutcome.Problem("Couldn't get the details for ${hit.name}. Try again in a moment.")
            }
            busy.value = false
        }
    }

    /** Fallback when details can't be fetched: the search summary, one serving counted as 100 g. */
    private fun FatSecretHit.toFoodInfo(): FoodInfo? {
        val kcal = kcal ?: return null
        return FoodInfo(
            name = name,
            brand = brand,
            per100g = Nutrients(kcal = kcal, proteinG = proteinG ?: 0.0, carbsG = carbsG ?: 0.0, fatG = fatG ?: 0.0),
            servingG = 100.0,
            servingLabel = per?.let { "$it (weight unknown, counted as 100 g)" },
        )
    }

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

    private companion object {
        const val AUTO_SEARCH_MS = 700L
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

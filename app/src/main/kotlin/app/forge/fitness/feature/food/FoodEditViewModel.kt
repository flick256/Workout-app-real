package app.forge.fitness.feature.food

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import android.net.Uri
import app.forge.domain.calc.Units
import app.forge.domain.parse.LabelBasis
import app.forge.domain.parse.LabelField
import app.forge.fitness.data.ai.LabelReader
import app.forge.domain.nutrition.FoodInfo
import app.forge.domain.nutrition.Nutrients
import app.forge.domain.nutrition.OpenFoodFacts
import app.forge.fitness.data.nutrition.BarcodeInUse
import app.forge.fitness.data.nutrition.FoodRepository
import app.forge.fitness.ui.navigation.FoodEditRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.math.abs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FoodForm(
    val loaded: Boolean = false,
    val isNew: Boolean = true,
    val name: String = "",
    val brand: String = "",
    val barcode: String = "",
    /** Labels list values per 100 g and per serving; type whichever is easier. */
    val perServing: Boolean = false,
    val servingG: String = "",
    val servingLabel: String = "",
    val kcal: String = "",
    val protein: String = "",
    val carbs: String = "",
    val fat: String = "",
    val fiber: String = "",
    val sugar: String = "",
    val salt: String = "",
    val error: String? = null,
    val saving: Boolean = false,
    val reading: Boolean = false,
    /** After scanning a label: what was filled in, to check against the packet. */
    val scanNote: String? = null,
) {
    private fun n(s: String) = parseNumber(s)

    /** Calories implied by the macros, if they're filled in. */
    val macroKcal: Double? get() {
        val p = n(protein); val c = n(carbs); val f = n(fat)
        return if (p == null && c == null && f == null) null else (p ?: 0.0) * 4 + (c ?: 0.0) * 4 + (f ?: 0.0) * 9
    }

    /** Labels round, so only flag a big mismatch (likely a typo or kJ typed as kcal). */
    val energyWarning: String? get() {
        val typed = n(kcal) ?: return null
        val macros = macroKcal ?: return null
        if (typed < 20 && macros < 20) return null
        return if (abs(typed - macros) > 0.25 * maxOf(typed, macros)) {
            "Calories don't match the macros (≈${macros.toInt()} kcal). If the label shows kJ, divide by 4.2."
        } else {
            null
        }
    }
}

@HiltViewModel
class FoodEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: FoodRepository,
    private val labels: LabelReader,
) : ViewModel() {

    private val route = savedStateHandle.toRoute<FoodEditRoute>()
    private val _form = MutableStateFlow(FoodForm(loaded = route.foodId == null, barcode = route.barcode.orEmpty(), name = route.name.orEmpty()))
    val form: StateFlow<FoodForm> = _form.asStateFlow()

    init {
        route.foodId?.let { id ->
            viewModelScope.launch {
                val f = repository.getFood(id) ?: return@launch
                fun fmt(v: Double?) = v?.let { Units.format(it) }.orEmpty()
                _form.value = FoodForm(
                    loaded = true, isNew = false, name = f.name, brand = f.brand.orEmpty(), barcode = f.barcode.orEmpty(),
                    servingG = fmt(f.servingG), servingLabel = f.servingLabel.orEmpty(),
                    kcal = fmt(f.kcal), protein = fmt(f.proteinG), carbs = fmt(f.carbsG), fat = fmt(f.fatG),
                    fiber = fmt(f.fiberG), sugar = fmt(f.sugarG), salt = fmt(f.saltG),
                )
            }
        }
    }

    fun update(change: (FoodForm) -> FoodForm) = _form.update { change(it).copy(error = null) }

    /** Reads a photo of the nutrition panel and fills in what it finds (you check it). */
    fun scanLabel(uri: Uri) {
        _form.update { it.copy(reading = true, scanNote = null, error = null) }
        viewModelScope.launch {
            val reading = runCatching { labels.read(uri) }.getOrNull()
            _form.update { f ->
                if (reading == null || !reading.isUseful) {
                    f.copy(reading = false, scanNote = "Couldn't read the panel. Try a straight-on, well-lit photo of just the table.")
                } else {
                    fun v(field: LabelField) = reading.values[field]?.let { Units.format(it) }
                    f.copy(
                        reading = false,
                        perServing = reading.basis == LabelBasis.PER_SERVING,
                        servingG = reading.servingG?.let { Units.format(it) } ?: f.servingG,
                        kcal = v(LabelField.ENERGY_KCAL) ?: f.kcal,
                        protein = v(LabelField.PROTEIN) ?: f.protein,
                        carbs = v(LabelField.CARBS) ?: f.carbs,
                        fat = v(LabelField.FAT) ?: f.fat,
                        sugar = v(LabelField.SUGARS) ?: f.sugar,
                        fiber = v(LabelField.FIBRE) ?: f.fiber,
                        salt = v(LabelField.SALT) ?: f.salt,
                        scanNote = "Filled in from the label (" +
                            (if (reading.basis == LabelBasis.PER_100G) "per 100 g" else "per serving") + "). " +
                            (if (reading.missing.isEmpty()) "Check the numbers against the packet." else
                                "Couldn't find: ${reading.missing.joinToString { it.label.lowercase() }}. Please type those in."),
                    )
                }
            }
        }
    }

    /** Saves and returns the food id, or null with an error shown. */
    suspend fun save(): String? {
        val f = _form.value
        if (f.saving) return null
        fun fail(message: String): String? { _form.update { it.copy(error = message) }; return null }
        if (f.name.isBlank()) return fail("Give the food a name")
        val serving = parseNumber(f.servingG)?.takeIf { it > 0 }
        if (f.perServing && serving == null) return fail("Enter the serving size in grams")
        val kcal = parseNumber(f.kcal) ?: f.macroKcal ?: return fail("Enter the calories (or the macros)")
        val code = f.barcode.trim()
        if (code.isNotEmpty() && !OpenFoodFacts.isValidBarcode(code)) return fail("A barcode is 8, 12, 13 or 14 digits")

        // Store everything per 100 g.
        val scale = if (f.perServing) 100.0 / serving!! else 1.0
        fun per100(s: String) = parseNumber(s)?.times(scale)
        val info = FoodInfo(
            name = f.name.trim(),
            brand = f.brand.trim().ifEmpty { null },
            barcode = code.ifEmpty { null },
            per100g = Nutrients(
                kcal = kcal * scale,
                proteinG = per100(f.protein) ?: 0.0,
                carbsG = per100(f.carbs) ?: 0.0,
                fatG = per100(f.fat) ?: 0.0,
                fiberG = per100(f.fiber),
                sugarG = per100(f.sugar),
                saltG = per100(f.salt),
            ),
            servingG = serving,
            servingLabel = f.servingLabel.trim().ifEmpty { null },
        )
        _form.update { it.copy(saving = true) }
        return try {
            repository.saveCustom(route.foodId, info)
        } catch (e: BarcodeInUse) {
            fail("That barcode already belongs to \"${e.foodName}\"")
        } finally {
            _form.update { it.copy(saving = false) }
        }
    }

    suspend fun delete() {
        route.foodId?.let { repository.deleteFood(it) }
    }
}

package app.forge.fitness.feature.food

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import app.forge.domain.nutrition.Meal
import app.forge.domain.nutrition.Nutrients
import app.forge.fitness.ui.theme.Spacing
import kotlin.math.roundToInt

/** "228 kcal · P 7 g · C 34 g · F 5 g" */
fun macroLine(n: Nutrients): String =
    "${n.kcal.roundToInt()} kcal · P ${n.proteinG.roundToInt()} g · C ${n.carbsG.roundToInt()} g · F ${n.fatG.roundToInt()} g"

fun kcalText(kcal: Number): String = "%,d".format(kcal.toDouble().roundToInt())

/** Grams without a trailing ".0". */
fun gramsText(g: Double): String = if (g % 1.0 == 0.0) "${g.toInt()} g" else "${(g * 10).roundToInt() / 10.0} g"

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MealChips(selected: Meal, onSelect: (Meal) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Meal.entries.forEach { meal ->
            FilterChip(selected = meal == selected, onClick = { onSelect(meal) }, label = { Text(meal.label) })
        }
    }
}

/** Parses "12", "12.5" or "12,5"; null if empty or not a number. */
fun parseNumber(text: String): Double? = text.trim().replace(',', '.').takeIf { it.isNotEmpty() }?.toDoubleOrNull()

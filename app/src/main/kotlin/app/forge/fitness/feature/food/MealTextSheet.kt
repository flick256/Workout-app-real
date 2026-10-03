package app.forge.fitness.feature.food

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.forge.domain.nutrition.Meal
import app.forge.fitness.data.nutrition.MealLine
import app.forge.fitness.ui.theme.Sizes
import app.forge.fitness.ui.theme.Spacing
import kotlin.math.roundToInt

/**
 * Type or say a whole meal ("2 weet-bix with milk and a banana"), check what Forge
 * understood, fix anything with a tap, and log it all at once.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MealTextSheet(
    initialMeal: Meal,
    lines: List<MealLine>?,
    reading: Boolean,
    onRead: (String) -> Unit,
    onChoose: (line: Int, choice: Int) -> Unit,
    onGrams: (line: Int, grams: Double) -> Unit,
    onRemove: (line: Int) -> Unit,
    onSearchInstead: (String) -> Unit,
    onDismiss: () -> Unit,
    onLog: (Meal) -> Unit,
) {
    var text by rememberSaveable { mutableStateOf("") }
    var meal by rememberSaveable { mutableStateOf(initialMeal) }
    val speech = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let {
                text = it
                onRead(it)
            }
        }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.padding(horizontal = Spacing.screen).padding(bottom = Spacing.xl).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Text("What did you eat?", style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text("e.g. 2 weet-bix with milk and a banana") },
                trailingIcon = {
                    IconButton(onClick = {
                        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                            .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                            .putExtra(RecognizerIntent.EXTRA_PROMPT, "Say what you ate")
                        runCatching { speech.launch(intent) }
                    }) { Icon(Icons.Rounded.Mic, "Say it") }
                },
                minLines = 2,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = { onRead(text) },
                enabled = text.isNotBlank() && !reading,
                modifier = Modifier.fillMaxWidth().heightIn(min = Sizes.touch),
            ) { Text(if (lines == null) "Read it" else "Read it again") }

            if (reading) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Text("  Working it out…")
                }
            }
            if (lines != null && !reading) {
                if (lines.isEmpty()) {
                    Text(
                        "Couldn't pick out any foods. Try separating them with commas, e.g. \"toast, 2 eggs, orange juice\".",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                lines.forEachIndexed { i, line ->
                    MealLineRow(line, onChoose = { onChoose(i, it) }, onGrams = { onGrams(i, it) }, onRemove = { onRemove(i) }, onSearchInstead = onSearchInstead)
                    HorizontalDivider()
                }
                val ready = lines.filter { it.choice != null && it.grams > 0 }
                if (ready.isNotEmpty()) {
                    val kcal = ready.sumOf { it.nutrients?.kcal ?: 0.0 }
                    val protein = ready.sumOf { it.nutrients?.proteinG ?: 0.0 }
                    Text(
                        "Total: ${kcal.roundToInt()} kcal · protein ${protein.roundToInt()} g",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    MealChips(meal) { meal = it }
                    Button(onClick = { onLog(meal) }, modifier = Modifier.fillMaxWidth().heightIn(min = Sizes.touch)) {
                        Text("Add ${ready.size} ${if (ready.size == 1) "food" else "foods"} to ${meal.label}")
                    }
                }
            }
        }
    }
}

@Composable
private fun MealLineRow(
    line: MealLine,
    onChoose: (Int) -> Unit,
    onGrams: (Double) -> Unit,
    onRemove: () -> Unit,
    onSearchInstead: (String) -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    var grams by remember(line.grams, line.chosen) { mutableStateOf(line.grams.roundToInt().toString()) }
    Column(Modifier.fillMaxWidth().padding(vertical = Spacing.xs)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                listOfNotNull(line.item.quantity?.let { q -> if (q % 1.0 == 0.0) q.toInt().toString() else q.toString() }, line.item.unit, line.item.food)
                    .joinToString(" "),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onRemove) { Icon(Icons.Rounded.Close, "Remove ${line.item.food}") }
        }
        val choice = line.choice
        if (choice == null) {
            Text("No match on your phone or in the built-in foods.", style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { onSearchInstead(line.item.food) }) { Text("Search brands & the web for \"${line.item.food}\"") }
        } else {
            Box {
                TextButton(onClick = { menu = true }, modifier = Modifier.heightIn(min = Sizes.touch)) {
                    Text(choice.name, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    if (line.choices.size > 1) Icon(Icons.Rounded.ArrowDropDown, "Other matches")
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    line.choices.forEachIndexed { i, c ->
                        DropdownMenuItem(text = { Text(c.name, maxLines = 2) }, onClick = { menu = false; onChoose(i) })
                    }
                    DropdownMenuItem(text = { Text("None of these: search for it") }, onClick = { menu = false; onSearchInstead(line.item.food) })
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = grams,
                    onValueChange = { v ->
                        grams = v.filter { it.isDigit() }.take(5)
                        grams.toDoubleOrNull()?.takeIf { it > 0 }?.let(onGrams)
                    },
                    suffix = { Text("g") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.width(110.dp),
                )
                Column(Modifier.padding(start = Spacing.md).weight(1f)) {
                    Text(line.explanation, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    line.nutrients?.let { n ->
                        Text("${n.kcal.roundToInt()} kcal · P ${n.proteinG.roundToInt()} g", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

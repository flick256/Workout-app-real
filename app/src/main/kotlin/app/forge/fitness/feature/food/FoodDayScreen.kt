package app.forge.fitness.feature.food

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.forge.domain.nutrition.Meal
import app.forge.domain.nutrition.Nutrients
import app.forge.fitness.data.db.FoodLogEntity
import app.forge.fitness.data.nutrition.TargetsState
import app.forge.fitness.data.nutrition.nutrients
import app.forge.fitness.data.nutrition.total
import app.forge.fitness.ui.charts.BarRow
import app.forge.fitness.ui.charts.TargetBars
import app.forge.fitness.ui.components.ForgeCard
import app.forge.fitness.ui.components.LocalSnackbarHostState
import app.forge.fitness.ui.components.showUndo
import app.forge.fitness.ui.theme.Sizes
import app.forge.fitness.ui.theme.Spacing
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/** One day of eating: totals against your targets, then each meal. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoodDayScreen(
    onBack: () -> Unit,
    onAdd: (epochDay: Long, meal: Meal) -> Unit,
    onTargets: () -> Unit,
    vm: FoodDayViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val snackbar = LocalSnackbarHostState.current
    var editing by remember { mutableStateOf<FoodLogEntity?>(null) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Food") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
                actions = { IconButton(onClick = onTargets) { Icon(Icons.Rounded.Tune, "Targets") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        if (!state.loaded) return@Scaffold
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = Spacing.screen,
                end = Spacing.screen,
                top = padding.calculateTopPadding(),
                bottom = Spacing.xxl,
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            item(key = "day") { DaySwitcher(state.day, vm::previousDay, vm::nextDay, vm::goToToday) }
            item(key = "summary") { DaySummary(state.total, state.targets, onTargets) }
            Meal.entries.forEach { meal ->
                item(key = "meal-${meal.name}") {
                    MealCard(
                        meal = meal,
                        entries = state.meals[meal].orEmpty(),
                        onAdd = { onAdd(state.day.toEpochDay(), meal) },
                        onOpen = { editing = it },
                        onCopy = {
                            scope.launch {
                                val n = vm.copyFromPreviousDay(meal)
                                snackbar.showSnackbar(
                                    if (n == 0) "Nothing logged for ${meal.label.lowercase()} the day before"
                                    else "Copied $n item${if (n == 1) "" else "s"} from the day before",
                                )
                            }
                        },
                    )
                }
            }
            item(key = "note") {
                Text(
                    "Food values come from labels and Open Food Facts, so treat totals as good estimates, not exact.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }

    editing?.let { entry ->
        EntrySheet(
            entry = entry,
            onDismiss = { editing = null },
            onSave = { grams, meal ->
                editing = null
                scope.launch { vm.updateEntry(entry.id, grams, meal) }
            },
            onDelete = {
                editing = null
                scope.launch {
                    vm.deleteEntry(entry.id)
                    snackbar.showUndo("Removed ${entry.name}") { scope.launch { vm.restoreEntry(entry.id) } }
                }
            },
        )
    }
}

@Composable
private fun DaySwitcher(day: LocalDate, onPrev: () -> Unit, onNext: () -> Unit, onToday: () -> Unit) {
    val today = LocalDate.now()
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onPrev) { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, "Previous day") }
        Text(
            when (day) {
                today -> "Today"
                today.minusDays(1) -> "Yesterday"
                else -> day.format(DateTimeFormatter.ofPattern("EEE d MMM"))
            },
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            // Tap the date to jump back to today; 48dp tall like the arrows beside it.
            modifier = Modifier
                .weight(1f)
                .heightIn(min = Sizes.touch)
                .clickable(enabled = day != today, onClickLabel = "Go to today", role = Role.Button, onClick = onToday)
                .wrapContentHeight(),
        )
        IconButton(onClick = onNext, enabled = day < today) { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, "Next day") }
    }
}

@Composable
private fun DaySummary(total: Nutrients, targets: TargetsState, onTargets: () -> Unit) {
    ForgeCard {
        val t = targets.targetsOrNull
        if (t == null) {
            Text("${kcalText(total.kcal)} kcal", style = MaterialTheme.typography.headlineSmall)
            Text(macroLine(total), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(Spacing.sm))
            val needed = (targets as? TargetsState.Missing)?.needed.orEmpty()
            Text(
                "Set up daily targets" + if (needed.isNotEmpty()) ": add ${needed.joinToString()}." else ".",
                style = MaterialTheme.typography.bodyMedium,
            )
            TextButton(onClick = onTargets) { Text("Set targets") }
            return@ForgeCard
        }
        val left = t.kcal - total.kcal
        Row(verticalAlignment = Alignment.Bottom) {
            Text("${kcalText(total.kcal)} ", style = MaterialTheme.typography.headlineSmall)
            Text("/ ${kcalText(t.kcal)} kcal", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        LinearProgressIndicator(
            progress = { (total.kcal / t.kcal).toFloat().coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xs),
        )
        Text(
            if (left >= 0) "${kcalText(left)} kcal left" else "${kcalText(-left)} kcal over",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Spacing.sm))
        TargetBars(
            listOf(
                BarRow("Protein", total.proteinG, t.proteinG.toDouble(), "${total.proteinG.roundToInt()} / ${t.proteinG} g"),
                BarRow("Carbs", total.carbsG, t.carbsG.toDouble(), "${total.carbsG.roundToInt()} / ${t.carbsG} g"),
                BarRow("Fat", total.fatG, t.fatG.toDouble(), "${total.fatG.roundToInt()} / ${t.fatG} g"),
            ),
        )
        total.fiberG?.let {
            Text(
                "Fibre ${it.roundToInt()} g" + (total.sugarG?.let { s -> " · sugars ${s.roundToInt()} g" } ?: ""),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.xs),
            )
        }
    }
}

@Composable
private fun MealCard(
    meal: Meal,
    entries: List<FoodLogEntity>,
    onAdd: () -> Unit,
    onOpen: (FoodLogEntity) -> Unit,
    onCopy: () -> Unit,
) {
    ForgeCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(meal.label, style = MaterialTheme.typography.titleMedium)
                if (entries.isNotEmpty()) {
                    Text(
                        "${kcalText(entries.total().kcal)} kcal",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (entries.isEmpty()) {
                IconButton(onClick = onCopy) { Icon(Icons.Rounded.ContentCopy, "Copy ${meal.label.lowercase()} from the day before") }
            }
            IconButton(onClick = onAdd) { Icon(Icons.Rounded.Add, "Add to ${meal.label.lowercase()}") }
        }
        entries.forEachIndexed { i, e ->
            if (i > 0) HorizontalDivider()
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = Sizes.touch)
                    .clickable { onOpen(e) }
                    .padding(vertical = Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(e.name, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        (e.grams?.let { gramsText(it) + " · " } ?: "") + "P ${e.proteinG.roundToInt()} g",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text("${e.kcal.roundToInt()} kcal", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/** Change the amount or meal of something you logged, or remove it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EntrySheet(
    entry: FoodLogEntity,
    onDismiss: () -> Unit,
    onSave: (grams: Double?, meal: Meal) -> Unit,
    onDelete: () -> Unit,
) {
    var grams by remember(entry.id) { mutableStateOf(entry.grams?.let { gramsText(it).removeSuffix(" g") }.orEmpty()) }
    var meal by remember(entry.id) { mutableStateOf(Meal.fromKey(entry.meal)) }
    val parsed = parseNumber(grams)
    val preview = if (entry.grams != null && parsed != null && entry.grams > 0) entry.nutrients * (parsed / entry.grams) else entry.nutrients
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = Spacing.screen).padding(bottom = Spacing.xl)) {
            Text(entry.name, style = MaterialTheme.typography.titleLarge)
            Text(macroLine(preview), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (entry.grams != null) {
                Spacer(Modifier.height(Spacing.md))
                OutlinedTextField(
                    value = grams,
                    onValueChange = { v -> grams = v.filter { it.isDigit() || it == '.' || it == ',' }.take(6) },
                    label = { Text("Amount") },
                    suffix = { Text("g") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(Spacing.md))
            MealChips(meal) { meal = it }
            Spacer(Modifier.height(Spacing.lg))
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                OutlinedButton(onClick = onDelete, modifier = Modifier.weight(1f).heightIn(min = Sizes.touch)) { Text("Remove") }
                Button(
                    onClick = { onSave(parsed?.takeIf { it > 0 } ?: entry.grams, meal) },
                    modifier = Modifier.weight(1f).heightIn(min = Sizes.touch),
                ) { Text("Save") }
            }
        }
    }
}

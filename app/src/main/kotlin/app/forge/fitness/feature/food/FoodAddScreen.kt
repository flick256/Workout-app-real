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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.forge.domain.nutrition.FoodInfo
import app.forge.domain.nutrition.Meal
import app.forge.domain.nutrition.Nutrients
import app.forge.fitness.data.db.FoodEntity
import app.forge.fitness.data.nutrition.per100g
import app.forge.fitness.ui.components.LocalSnackbarHostState
import app.forge.fitness.ui.components.SectionHeader
import app.forge.fitness.ui.components.rememberHaptics
import app.forge.fitness.ui.theme.Sizes
import app.forge.fitness.ui.theme.Spacing
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/** Find something to log: search, scan a barcode, quick add, or create a food. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoodAddScreen(
    autoScan: Boolean,
    onBack: () -> Unit,
    onCreateFood: (barcode: String?) -> Unit,
    onEditFood: (foodId: String) -> Unit,
    newFoodId: String?,
    onNewFoodHandled: () -> Unit,
    vm: FoodAddViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val selected by vm.selected.collectAsStateWithLifecycle()
    val scanOutcome by vm.scanOutcome.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = LocalSnackbarHostState.current
    val haptics = rememberHaptics()
    var quickAdd by remember { mutableStateOf(false) }
    var typeBarcode by remember { mutableStateOf(false) }

    // From the "Scan food" shortcut: open the scanner once (not again after rotating).
    var autoScanned by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (autoScan && !autoScanned) {
            autoScanned = true
            when (val r = BarcodeScanner.scan(context)) {
                is ScanResult.Code -> vm.onScanned(r.value)
                is ScanResult.Failed -> snackbar.showSnackbar("Scanner unavailable: ${r.message}")
                ScanResult.Cancelled -> Unit
            }
        }
    }

    // Coming back from "New food": open it straight away so you can log it.
    LaunchedEffect(newFoodId) {
        if (newFoodId != null) {
            vm.openById(newFoodId)
            onNewFoodHandled()
        }
    }

    fun scan() = scope.launch {
        when (val r = BarcodeScanner.scan(context)) {
            is ScanResult.Code -> vm.onScanned(r.value)
            is ScanResult.Failed -> snackbar.showSnackbar("Scanner unavailable: ${r.message}. You can type the barcode instead.")
            ScanResult.Cancelled -> Unit
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Add to ${vm.meal.label.lowercase()}") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().imePadding(),
            contentPadding = PaddingValues(
                start = Spacing.screen,
                end = Spacing.screen,
                top = padding.calculateTopPadding(),
                bottom = Spacing.xxl,
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            item(key = "search") {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = vm::setQuery,
                    placeholder = { Text("Search foods") },
                    leadingIcon = { Icon(Icons.Rounded.Search, null) },
                    trailingIcon = {
                        if (state.query.isNotEmpty()) IconButton(onClick = { vm.setQuery("") }) { Icon(Icons.Rounded.Clear, "Clear") }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { vm.searchOnline() }),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item(key = "actions") {
                // Three to a row: slimmer padding and one ellipsised line so large fonts don't break the buttons.
                val buttonPadding = PaddingValues(horizontal = Spacing.sm, vertical = Spacing.sm)
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.padding(vertical = Spacing.sm)) {
                    FilledTonalButton(onClick = { scan() }, contentPadding = buttonPadding, modifier = Modifier.weight(1f).heightIn(min = Sizes.touch)) {
                        Icon(Icons.Rounded.QrCodeScanner, null)
                        Text(" Scan", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    OutlinedButton(onClick = { quickAdd = true }, contentPadding = buttonPadding, modifier = Modifier.weight(1f).heightIn(min = Sizes.touch)) {
                        Icon(Icons.Rounded.Bolt, null)
                        Text(" Quick", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    OutlinedButton(onClick = { onCreateFood(null) }, contentPadding = buttonPadding, modifier = Modifier.weight(1f).heightIn(min = Sizes.touch)) {
                        Icon(Icons.Rounded.Edit, null)
                        Text(" New", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            item(key = "type-barcode") {
                TextButton(onClick = { typeBarcode = true }) { Text("Type a barcode instead") }
            }
            if (state.busy) {
                item(key = "busy") {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(Spacing.sm)) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        Text("  Looking up barcode…")
                    }
                }
            }

            if (state.query.isBlank()) {
                if (state.favorites.isNotEmpty()) {
                    item(key = "fav-h") { SectionHeader("Favourites") }
                    items(state.favorites, key = { "fav-" + it.id }) { FoodRow(it) { vm.pick(it) } }
                }
                item(key = "recent-h") { SectionHeader("Recent") }
                if (state.recent.isEmpty()) {
                    item(key = "recent-empty") {
                        Text(
                            "Foods you log show up here. Scan a packet's barcode, or search Open Food Facts by name.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                items(state.recent, key = { "recent-" + it.id }) { FoodRow(it) { vm.pick(it) } }
            } else {
                item(key = "mine-h") { SectionHeader("On your phone") }
                if (state.results.isEmpty()) {
                    item(key = "mine-empty") {
                        Text("No saved foods match.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                items(state.results, key = { "mine-" + it.id }) { FoodRow(it) { vm.pick(it) } }
                item(key = "online-h") { SectionHeader("Open Food Facts") }
                when (val online = state.online) {
                    OnlineState.Idle -> item(key = "online-go") {
                        OutlinedButton(onClick = vm::searchOnline, modifier = Modifier.fillMaxWidth().heightIn(min = Sizes.touch)) {
                            Icon(Icons.Rounded.Language, null)
                            Text("  Search online for \"${state.query.trim()}\"")
                        }
                    }
                    OnlineState.Loading -> item(key = "online-loading") {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(Spacing.sm)) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            Text("  Searching…")
                        }
                    }
                    is OnlineState.Error -> item(key = "online-error") {
                        Column {
                            Text(online.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                            TextButton(onClick = vm::searchOnline) { Text("Try again") }
                        }
                    }
                    is OnlineState.Results -> {
                        if (online.foods.isEmpty()) {
                            item(key = "online-none") {
                                Text(
                                    "Nothing found for \"${online.query}\". Try fewer words, or create it with New.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        // Positions keep keys unique even if two results share a barcode.
                        online.foods.forEachIndexed { i, info ->
                            item(key = "online-$i-${info.barcode}") { OnlineRow(info) { vm.pickOnline(info) } }
                        }
                    }
                }
            }
        }
    }

    selected?.let { food ->
        AmountSheet(
            food = food,
            initialMeal = vm.meal,
            onDismiss = { vm.selected.value = null },
            onFavorite = { vm.toggleFavorite(food) },
            onEdit = {
                vm.selected.value = null
                onEditFood(food.id)
            },
            onAdd = { grams, meal ->
                scope.launch {
                    vm.log(food, grams, meal)
                    haptics.success()
                    onBack()
                }
            },
        )
    }

    if (quickAdd) {
        QuickAddDialog(
            initialMeal = vm.meal,
            onDismiss = { quickAdd = false },
            onAdd = { name, nutrients, meal ->
                quickAdd = false
                scope.launch {
                    vm.quickAdd(name, nutrients, meal)
                    haptics.success()
                    onBack()
                }
            },
        )
    }

    if (typeBarcode) {
        var code by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { typeBarcode = false },
            title = { Text("Barcode") },
            text = {
                OutlinedTextField(
                    value = code,
                    onValueChange = { v -> code = v.filter(Char::isDigit).take(14) },
                    placeholder = { Text("e.g. 9300633603205") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            },
            confirmButton = {
                TextButton(onClick = { typeBarcode = false; vm.onScanned(code) }, enabled = code.length >= 8) { Text("Look up") }
            },
            dismissButton = { TextButton(onClick = { typeBarcode = false }) { Text("Cancel") } },
        )
    }

    when (val outcome = scanOutcome) {
        is ScanOutcome.NotFound -> AlertDialog(
            onDismissRequest = { vm.scanOutcome.value = null },
            title = { Text("Not found") },
            text = {
                Text(
                    "Barcode ${outcome.barcode} isn't in Open Food Facts yet. Add it yourself from the label: " +
                        "it's saved on your phone for next time.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.scanOutcome.value = null
                    onCreateFood(outcome.barcode)
                }) { Text("Create food") }
            },
            dismissButton = { TextButton(onClick = { vm.scanOutcome.value = null }) { Text("Cancel") } },
        )
        is ScanOutcome.Problem -> AlertDialog(
            onDismissRequest = { vm.scanOutcome.value = null },
            title = { Text("Couldn't look that up") },
            text = { Text(outcome.message) },
            confirmButton = { TextButton(onClick = { vm.scanOutcome.value = null }) { Text("OK") } },
        )
        null -> Unit
    }
}

@Composable
private fun FoodRow(food: FoodEntity, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(food.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Text(
                listOfNotNull(food.brand, "${food.kcal.roundToInt()} kcal · P ${food.proteinG.roundToInt()} g per 100 g").joinToString(" · "),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        },
        trailingContent = {
            if (food.favorite) Icon(Icons.Rounded.Star, "Favourite", tint = MaterialTheme.colorScheme.primary)
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickableRow(onClick),
    )
}

@Composable
private fun OnlineRow(info: FoodInfo, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(info.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Text(
                listOfNotNull(info.brand, "${info.per100g.kcal.roundToInt()} kcal · P ${info.per100g.proteinG.roundToInt()} g per 100 g")
                    .joinToString(" · "),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickableRow(onClick),
    )
}

private fun Modifier.clickableRow(onClick: () -> Unit) = heightIn(min = Sizes.touch).clickable(onClick = onClick)

/** How much did you have? Serving and 100 g shortcuts, live nutrition, meal. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AmountSheet(
    food: FoodEntity,
    initialMeal: Meal,
    onDismiss: () -> Unit,
    onFavorite: () -> Unit,
    onEdit: () -> Unit,
    onAdd: (grams: Double, meal: Meal) -> Unit,
) {
    val serving = food.servingG
    var grams by remember(food.id) { mutableStateOf(gramsText(serving ?: 100.0).removeSuffix(" g")) }
    var meal by remember(food.id) { mutableStateOf(initialMeal) }
    val parsed = parseNumber(grams)?.takeIf { it > 0 && it < 10_000 }
    val preview = food.per100g.forGrams(parsed ?: 0.0)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = Spacing.screen).padding(bottom = Spacing.xl)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(food.name, style = MaterialTheme.typography.titleLarge)
                    food.brand?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                IconButton(onClick = onFavorite) {
                    Icon(
                        if (food.favorite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                        if (food.favorite) "Remove from favourites" else "Add to favourites",
                    )
                }
                IconButton(onClick = onEdit) { Icon(Icons.Rounded.Edit, "Edit food") }
            }
            Spacer(Modifier.height(Spacing.sm))
            Text(macroLine(preview), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(Spacing.md))
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                if (serving != null) {
                    FilterChip(
                        selected = parsed == serving,
                        onClick = { grams = gramsText(serving).removeSuffix(" g") },
                        label = { Text(food.servingLabel?.let { "1 serving ($it)" } ?: "1 serving (${gramsText(serving)})") },
                    )
                    FilterChip(
                        selected = parsed == serving * 2,
                        onClick = { grams = gramsText(serving * 2).removeSuffix(" g") },
                        label = { Text("2 servings") },
                    )
                }
                FilterChip(selected = parsed == 100.0, onClick = { grams = "100" }, label = { Text("100 g") })
            }
            OutlinedTextField(
                value = grams,
                onValueChange = { v -> grams = v.filter { it.isDigit() || it == '.' || it == ',' }.take(6) },
                label = { Text("Amount") },
                suffix = { Text("g") },
                singleLine = true,
                isError = parsed == null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm),
            )
            Spacer(Modifier.height(Spacing.md))
            MealChips(meal) { meal = it }
            Spacer(Modifier.height(Spacing.lg))
            Button(
                onClick = { parsed?.let { onAdd(it, meal) } },
                enabled = parsed != null,
                modifier = Modifier.fillMaxWidth().heightIn(min = Sizes.bigTouch),
            ) { Text("Add to ${meal.label.lowercase()}") }
            Text(
                "Per 100 g: ${macroLine(food.per100g)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.sm),
            )
        }
    }
}

/** Log calories and macros without a food, e.g. a meal out. */
@Composable
private fun QuickAddDialog(initialMeal: Meal, onDismiss: () -> Unit, onAdd: (String, Nutrients, Meal) -> Unit) {
    var name by remember { mutableStateOf("") }
    var kcal by remember { mutableStateOf("") }
    var protein by remember { mutableStateOf("") }
    var carbs by remember { mutableStateOf("") }
    var fat by remember { mutableStateOf("") }
    var meal by remember { mutableStateOf(initialMeal) }
    val p = parseNumber(protein) ?: 0.0
    val c = parseNumber(carbs) ?: 0.0
    val f = parseNumber(fat) ?: 0.0
    // Calories can be left blank and worked out from the macros.
    val energy = parseNumber(kcal) ?: (p * 4 + c * 4 + f * 9).takeIf { it > 0 }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Quick add") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                OutlinedTextField(name, { name = it }, label = { Text("What was it? (optional)") }, singleLine = true)
                NumberField("Calories", "kcal", kcal) { kcal = it }
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    NumberField("Protein", "g", protein, Modifier.weight(1f)) { protein = it }
                    NumberField("Carbs", "g", carbs, Modifier.weight(1f)) { carbs = it }
                    NumberField("Fat", "g", fat, Modifier.weight(1f)) { fat = it }
                }
                MealChips(meal) { meal = it }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onAdd(name.trim(), Nutrients(energy ?: 0.0, p, c, f), meal) },
                enabled = energy != null,
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
internal fun NumberField(label: String, suffix: String, value: String, modifier: Modifier = Modifier, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { v -> onChange(v.filter { it.isDigit() || it == '.' || it == ',' }.take(7)) },
        label = { Text(label, maxLines = 1) },
        suffix = { Text(suffix) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier,
    )
}

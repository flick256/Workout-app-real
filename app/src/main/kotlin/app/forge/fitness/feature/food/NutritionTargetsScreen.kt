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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.forge.domain.calc.Units
import app.forge.domain.nutrition.ActivityLevel
import app.forge.domain.nutrition.NutritionGoal
import app.forge.domain.nutrition.Sex
import app.forge.fitness.data.nutrition.TargetsState
import app.forge.fitness.data.prefs.CustomTargets
import app.forge.fitness.ui.components.ForgeCard
import app.forge.fitness.ui.components.SectionHeader
import app.forge.fitness.ui.components.TextInputDialog
import app.forge.fitness.ui.format.Format
import app.forge.fitness.ui.theme.Sizes
import app.forge.fitness.ui.theme.Spacing
import java.time.LocalDate

/** Your details, goal and the daily targets worked out from them. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NutritionTargetsScreen(
    onBack: () -> Unit,
    onOpenBody: () -> Unit,
    vm: NutritionTargetsViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    var editBirthYear by remember { mutableStateOf(false) }
    var editHeight by remember { mutableStateOf(false) }
    var editCustom by remember { mutableStateOf(false) }
    val prefs = state.prefs

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Nutrition targets") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
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
            item(key = "result") { TargetsCard(state.targets) }

            item(key = "about-h") { SectionHeader("About you") }
            item(key = "about") {
                ForgeCard {
                    DetailRow("Weight", state.weightKg?.let { Format.weight(it, prefs.weightUnit) } ?: "Not logged yet", onOpenBody)
                    DetailRow("Height", prefs.heightCm?.let { "${Units.format(it)} cm" } ?: "Not set") { editHeight = true }
                    DetailRow(
                        "Birth year",
                        prefs.birthYear?.let { "$it (age ${LocalDate.now().year - it})" } ?: "Not set",
                    ) { editBirthYear = true }
                    Text("Sex (the formula differs slightly)", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = Spacing.sm))
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        Sex.entries.forEachIndexed { i, sex ->
                            SegmentedButton(
                                selected = prefs.sex == sex,
                                onClick = { vm.setSex(sex) },
                                shape = SegmentedButtonDefaults.itemShape(i, Sex.entries.size),
                            ) { Text(if (sex == Sex.UNSPECIFIED) "Skip" else sex.label, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        }
                    }
                }
            }

            item(key = "activity-h") { SectionHeader("How active a normal week is") }
            item(key = "activity") {
                ForgeCard {
                    ActivityLevel.entries.forEach { level ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = Sizes.touch)
                                .selectable(selected = prefs.activityLevel == level, role = Role.RadioButton) { vm.setActivity(level) },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = prefs.activityLevel == level, onClick = null)
                            Column(Modifier.padding(start = Spacing.sm)) {
                                Text(level.label, style = MaterialTheme.typography.bodyLarge)
                                Text(level.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }

            item(key = "goal-h") { SectionHeader("Goal") }
            item(key = "goal") {
                ForgeCard {
                    NutritionGoal.entries.forEach { goal ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = Sizes.touch)
                                .selectable(selected = prefs.nutritionGoal == goal, role = Role.RadioButton) { vm.setGoal(goal) },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = prefs.nutritionGoal == goal, onClick = null)
                            Text(goal.label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = Spacing.sm))
                        }
                    }
                    Text(
                        "Changes are kept gentle on purpose: fast cuts cost muscle and training quality, and under 18 " +
                            "the deficit is capped at 250 kcal because you're still growing.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item(key = "custom-h") { SectionHeader("Use your own numbers") }
            item(key = "custom") {
                ForgeCard {
                    // Whole row toggles, so TalkBack reads the label with the switch.
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = Sizes.touch)
                            .toggleable(value = prefs.customTargets != null, role = Role.Switch) { on ->
                                if (on) editCustom = true else vm.setCustom(null)
                            },
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Custom targets", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                prefs.customTargets?.let { "${kcalText(it.kcal)} kcal · P ${it.proteinG} · C ${it.carbsG} · F ${it.fatG} g" }
                                    ?: "e.g. from a dietitian",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(checked = prefs.customTargets != null, onCheckedChange = null)
                    }
                    if (prefs.customTargets != null) TextButton(onClick = { editCustom = true }) { Text("Edit numbers") }
                }
            }
        }
    }

    if (editBirthYear) {
        TextInputDialog(
            title = "Birth year",
            initial = prefs.birthYear?.toString().orEmpty(),
            keyboardType = KeyboardType.Number,
            placeholder = "2009",
            onConfirm = { text ->
                val year = text.trim().toIntOrNull()?.takeIf { it in 1920..LocalDate.now().year - 10 }
                vm.setBirthYear(year)
                editBirthYear = false
            },
            onDismiss = { editBirthYear = false },
        )
    }
    if (editHeight) {
        TextInputDialog(
            title = "Height",
            initial = prefs.heightCm?.let { Units.format(it) }.orEmpty(),
            keyboardType = KeyboardType.Decimal,
            suffix = "cm",
            onConfirm = { text ->
                vm.setHeight(parseNumber(text)?.takeIf { it in 100.0..250.0 })
                editHeight = false
            },
            onDismiss = { editHeight = false },
        )
    }
    if (editCustom) {
        val start = prefs.customTargets ?: state.calculated.targetsOrNull?.let { CustomTargets(it.kcal, it.proteinG, it.carbsG, it.fatG) }
        CustomTargetsDialog(
            initial = start,
            onDismiss = { editCustom = false },
            onSave = { vm.setCustom(it); editCustom = false },
        )
    }
}

@Composable
private fun TargetsCard(targets: TargetsState) {
    ForgeCard {
        when (targets) {
            is TargetsState.Missing -> {
                Text("Almost there", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Add ${targets.needed.joinToString()} below and Forge works out your daily calories and protein.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            else -> {
                val t = targets.targetsOrNull!!
                Text("${kcalText(t.kcal)} kcal a day", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "Protein ${t.proteinG} g · Carbs ${t.carbsG} g · Fat ${t.fatG} g",
                    style = MaterialTheme.typography.titleSmall,
                )
                if (targets is TargetsState.Calculated) {
                    var why by remember { mutableStateOf(false) }
                    TextButton(onClick = { why = !why }) { Text(if (why) "Hide working" else "How is this worked out?") }
                    if (why) {
                        t.explanation.forEach {
                            Text("• $it", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 2.dp))
                        }
                    }
                } else {
                    Text("Your own targets", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = Sizes.touch).clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun CustomTargetsDialog(initial: CustomTargets?, onDismiss: () -> Unit, onSave: (CustomTargets) -> Unit) {
    var kcal by remember { mutableStateOf(initial?.kcal?.toString().orEmpty()) }
    var protein by remember { mutableStateOf(initial?.proteinG?.toString().orEmpty()) }
    var carbs by remember { mutableStateOf(initial?.carbsG?.toString().orEmpty()) }
    var fat by remember { mutableStateOf(initial?.fatG?.toString().orEmpty()) }
    val k = kcal.toIntOrNull()?.takeIf { it in 800..8000 }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Your targets") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                NumberField("Calories", "kcal", kcal, Modifier.fillMaxWidth()) { kcal = it.filter(Char::isDigit) }
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    NumberField("Protein", "g", protein, Modifier.weight(1f)) { protein = it.filter(Char::isDigit) }
                    NumberField("Carbs", "g", carbs, Modifier.weight(1f)) { carbs = it.filter(Char::isDigit) }
                    NumberField("Fat", "g", fat, Modifier.weight(1f)) { fat = it.filter(Char::isDigit) }
                }
                Spacer(Modifier.height(Spacing.xs))
                Text("Calories between 800 and 8,000.", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(CustomTargets(k!!, protein.toIntOrNull() ?: 0, carbs.toIntOrNull() ?: 0, fat.toIntOrNull() ?: 0)) },
                enabled = k != null,
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

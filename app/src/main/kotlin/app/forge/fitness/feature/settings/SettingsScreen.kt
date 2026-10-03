package app.forge.fitness.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Edit
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.rememberCoroutineScope
import app.forge.fitness.ui.components.LocalSnackbarHostState
import kotlinx.coroutines.launch
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import app.forge.fitness.ui.format.Format
import app.forge.domain.calc.Units
import app.forge.fitness.ui.components.TextInputDialog
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.forge.domain.model.Equipment
import app.forge.domain.model.ThemeMode
import app.forge.domain.model.WeightUnit
import app.forge.fitness.data.prefs.UserPreferences
import app.forge.fitness.ui.components.ForgeCard
import app.forge.fitness.ui.components.ScreenScaffold
import app.forge.fitness.ui.components.SectionHeader
import app.forge.fitness.ui.components.rememberHaptics
import app.forge.fitness.ui.theme.Sizes
import app.forge.fitness.ui.theme.Spacing

@Composable
fun SettingsScreen(
    onOpenHealth: () -> Unit,
    onOpenNutrition: () -> Unit,
    onOpenAi: () -> Unit,
    onOpenBackup: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val prefs by viewModel.preferences.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val versionName = remember(context) {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
    }

    val snackbar = LocalSnackbarHostState.current
    val scope = rememberCoroutineScope()
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) scope.launch { snackbar.showSnackbar(viewModel.export(uri)) }
    }

    val bodyweight by viewModel.latestBodyweight.collectAsStateWithLifecycle()
    var bodyDialog by rememberSaveable { mutableStateOf<String?>(null) }
    when (bodyDialog) {
        "weight" -> TextInputDialog(
            title = "Log bodyweight",
            message = "Used to work out the load of bodyweight exercises. Log it whenever it changes.",
            initial = bodyweight?.value?.let { Format.weightNumber(it, prefs.weightUnit) }.orEmpty(),
            keyboardType = KeyboardType.Decimal,
            suffix = prefs.weightUnit.symbol,
            onConfirm = { text ->
                Format.parseWeight(text, prefs.weightUnit)?.takeIf { it in 20.0..400.0 }?.let(viewModel::logBodyweight)
                bodyDialog = null
            },
            onDismiss = { bodyDialog = null },
        )
        "height" -> TextInputDialog(
            title = "Height",
            message = "Used to scale incline and decline push-ups: the same bench tilts a shorter person more.",
            initial = prefs.heightCm?.let { Units.format(it) }.orEmpty(),
            keyboardType = KeyboardType.Decimal,
            suffix = "cm",
            onConfirm = { text ->
                viewModel.setHeight(text.replace(',', '.').toDoubleOrNull()?.takeIf { it in 100.0..250.0 })
                bodyDialog = null
            },
            onDismiss = { bodyDialog = null },
        )
    }

    var editing by rememberSaveable { mutableStateOf<Equipment?>(null) }
    editing?.let { item ->
        OwnedWeightsSheet(
            equipment = item,
            weightsKg = prefs.weightsFor(item),
            unit = prefs.weightUnit,
            onChange = { viewModel.setOwnedWeights(item, it) },
            onDismiss = { editing = null },
        )
    }

    ScreenScaffold(title = "Settings") {
        item { SectionHeader("Appearance") }
        item {
            ForgeCard {
                SettingLabel("Theme")
                ChoiceRow(
                    options = ThemeMode.entries,
                    selected = prefs.themeMode,
                    label = { it.name.lowercase().replaceFirstChar(Char::uppercase) },
                    onSelect = viewModel::setThemeMode,
                )
            }
        }

        item { SectionHeader("Body") }
        item {
            ForgeCard {
                SettingRow(
                    title = "Bodyweight",
                    value = bodyweight?.let {
                        Format.weight(it.value, prefs.weightUnit) + " · " +
                            java.time.Instant.ofEpochMilli(it.measuredAt).atZone(java.time.ZoneId.systemDefault())
                                .format(java.time.format.DateTimeFormatter.ofPattern("d MMM"))
                    } ?: "Not set: tap to add",
                    onClick = { bodyDialog = "weight" },
                )
                HorizontalDivider(Modifier.padding(vertical = Spacing.xs))
                SettingRow(
                    title = "Height",
                    value = prefs.heightCm?.let { "${Units.format(it)} cm" } ?: "Not set: tap to add",
                    onClick = { bodyDialog = "height" },
                )
            }
        }

        item { SectionHeader("Workout") }
        item {
            ForgeCard {
                SettingLabel("Weight unit")
                ChoiceRow(
                    options = WeightUnit.entries,
                    selected = prefs.weightUnit,
                    label = { it.symbol },
                    onSelect = viewModel::setWeightUnit,
                )
                Spacer(Modifier.height(Spacing.xl))
                SettingLabel("Default rest timer")
                ChipFlow {
                    UserPreferences.REST_OPTIONS.forEach { seconds ->
                        SelectChip(
                            label = formatRest(seconds),
                            selected = prefs.defaultRestSeconds == seconds,
                            onClick = { viewModel.setDefaultRest(seconds) },
                        )
                    }
                }
            }
        }

        item { SectionHeader("Equipment I own") }
        item {
            ForgeCard {
                Text(
                    "Exercises and programs are filtered to what you have.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(Spacing.md))
                ChipFlow {
                    Equipment.entries.filter { it != Equipment.OTHER }.forEach { item ->
                        SelectChip(
                            label = item.label,
                            selected = item in prefs.equipment || item == Equipment.BODY_ONLY,
                            onClick = { viewModel.toggleEquipment(item) },
                        )
                    }
                }
            }
        }

        val weighted = prefs.equipment.filter { it.hasWeights }.sortedBy { it.ordinal }
        if (weighted.isNotEmpty()) {
            item { SectionHeader("Weights I own") }
            item {
                ForgeCard {
                    weighted.forEachIndexed { index, item ->
                        if (index > 0) HorizontalDivider(Modifier.padding(vertical = Spacing.xs))
                        OwnedWeightsRow(
                            equipment = item,
                            weightsKg = prefs.weightsFor(item),
                            unit = prefs.weightUnit,
                            onClick = { editing = item },
                        )
                    }
                }
            }
        }

        item { SectionHeader("Nutrition") }
        item {
            ForgeCard {
                SettingRow(
                    title = "Calorie & protein targets",
                    value = prefs.customTargets?.let { "Your own: ${it.kcal} kcal" }
                        ?: "Goal: ${prefs.nutritionGoal.label.lowercase()} · ${prefs.activityLevel.label.lowercase()}",
                    onClick = onOpenNutrition,
                )
            }
        }

        item { SectionHeader("Health & watch") }
        item {
            ForgeCard {
                SettingRow(
                    title = "Health Connect",
                    value = if (prefs.healthConnectEnabled) "On: syncing your watch/strap" else "Off",
                    onClick = onOpenHealth,
                )
                Text(
                    "Import sports, runs, workout heart rate, sleep and HRV from Zepp (Amazfit) or any app " +
                        "that shares with Health Connect.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item { SectionHeader("On-device AI") }
        item {
            ForgeCard {
                SettingRow(title = "AI model", value = "Optional · runs on your phone", onClick = onOpenAi)
                Text(
                    "Weekly summaries, plateau explanations and smarter quick logging, written from your own numbers.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item { SectionHeader("Your data") }
        item {
            ForgeCard {
                SettingRow(
                    title = "Backup & restore",
                    value = prefs.driveBackupUri?.let { "Google Drive · nightly" } ?: "Drive backup off · snapshots on the phone",
                    onClick = onOpenBackup,
                )
                Text(
                    "Everything lives on this phone. Back up to Google Drive nightly, export a copy or spreadsheets, " +
                        "and restore without losing anything.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item { SectionHeader("Demo data") }
        item {
            ForgeCard {
                Text(
                    "Load 12 weeks of sample training to explore the charts and suggestions. It's flagged as demo " +
                        "data and \"Remove\" deletes exactly that, nothing of yours.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(Spacing.sm))
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    FilledTonalButton(onClick = { scope.launch { snackbar.showSnackbar(viewModel.loadDemo()) } }) { Text("Load demo data") }
                    TextButton(onClick = { scope.launch { snackbar.showSnackbar(viewModel.removeDemo()) } }) { Text("Remove demo data") }
                }
            }
        }

        item { SectionHeader("About") }
        item {
            ForgeCard {
                Text("Forge $versionName", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    "Personal, offline-first, free. No accounts, ads or analytics. Internet is only " +
                        "used to look up foods on Open Food Facts.\n" +
                        "Exercise data: free-exercise-db (public domain).",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SettingRow(title: String, value: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(value) },
        trailingContent = { Icon(Icons.Rounded.Edit, contentDescription = "Edit $title") },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clip(MaterialTheme.shapes.medium).clickable(onClick = onClick),
    )
}

@Composable
private fun OwnedWeightsRow(
    equipment: Equipment,
    weightsKg: List<Double>,
    unit: WeightUnit,
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(equipment.label) },
        supportingContent = {
            Text(
                if (weightsKg.isEmpty()) "Tap to add the weights you have"
                else weightsKg.joinToString(", ") { Format.weightNumber(it, unit) } + " ${unit.symbol}",
                maxLines = 2,
            )
        },
        trailingContent = { Icon(Icons.Rounded.Edit, contentDescription = "Edit") },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick),
    )
}

@Composable
private fun SettingLabel(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(Spacing.md))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> ChoiceRow(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
) {
    val haptics = rememberHaptics()
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, option ->
            SegmentedButton(
                selected = option == selected,
                onClick = { haptics.tick(); onSelect(option) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
                modifier = Modifier.heightIn(min = Sizes.touch),
            ) {
                Text(label(option))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipFlow(content: @Composable () -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) { content() }
}

@Composable
private fun SelectChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val haptics = rememberHaptics()
    FilterChip(
        selected = selected,
        onClick = { haptics.tick(); onClick() },
        label = { Text(label) },
        leadingIcon = if (selected) {
            { Icon(Icons.Rounded.Check, null, Modifier.heightIn(max = FilterChipDefaults.IconSize)) }
        } else {
            null
        },
        modifier = Modifier.heightIn(min = Sizes.touch),
    )
}

private fun formatRest(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return when {
        m == 0 -> "${s}s"
        s == 0 -> "${m} min"
        else -> "$m:${s.toString().padStart(2, '0')}"
    }
}

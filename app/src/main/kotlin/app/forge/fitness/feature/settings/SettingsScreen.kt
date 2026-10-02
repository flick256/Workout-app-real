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
fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    val prefs by viewModel.preferences.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val versionName = remember(context) {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
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

        item { SectionHeader("About") }
        item {
            ForgeCard {
                Text("Forge $versionName", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    "Personal, offline-first, free. No accounts, ads or analytics.\n" +
                        "Exercise data: free-exercise-db (public domain).",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
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

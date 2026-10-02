package app.forge.fitness.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import app.forge.domain.model.Equipment
import app.forge.domain.model.WeightUnit
import app.forge.domain.workout.AvailableWeights
import app.forge.fitness.ui.components.rememberHaptics
import app.forge.fitness.ui.format.Format
import app.forge.fitness.ui.theme.Sizes
import app.forge.fitness.ui.theme.Spacing

/**
 * Edit the weights you own for one item, e.g. "Weighted bag: 10, 15, 20 kg" or an
 * adjustable dumbbell from 2.5 to 24 kg in 2.5 kg steps.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun OwnedWeightsSheet(
    equipment: Equipment,
    weightsKg: List<Double>,
    unit: WeightUnit,
    onChange: (List<Double>) -> Unit,
    onDismiss: () -> Unit,
) {
    val haptics = rememberHaptics()
    var single by rememberSaveable { mutableStateOf("") }
    var from by rememberSaveable { mutableStateOf("") }
    var to by rememberSaveable { mutableStateOf("") }
    var step by rememberSaveable { mutableStateOf("") }
    var rangeError by rememberSaveable { mutableStateOf<String?>(null) }

    fun addSingle() {
        val kg = Format.parseWeight(single, unit)?.takeIf { it > 0 } ?: return
        haptics.tick()
        onChange(weightsKg + kg)
        single = ""
    }

    fun addRange() {
        val a = Format.parseWeight(from, unit)
        val b = Format.parseWeight(to, unit)
        val s = Format.parseWeight(step, unit)
        if (a == null || b == null || s == null || s <= 0 || b < a) {
            rangeError = "Enter from ≤ to and a step above 0"
            return
        }
        val range = AvailableWeights.range(a, b, s)
        if (range.size > 200) {
            rangeError = "That's ${range.size} weights; use a bigger step"
            return
        }
        rangeError = null
        haptics.success()
        onChange(weightsKg + range)
        from = ""; to = ""; step = ""
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.screen)
                .navigationBarsPadding()
                .padding(bottom = Spacing.xl),
        ) {
            Text("${equipment.label}: weights I own", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(Spacing.xs))
            Text(
                "These show up as quick picks when you log a set, and suggestions will " +
                    "only recommend weights from this list.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Spacing.lg))

            if (weightsKg.isEmpty()) {
                Text(
                    "No weights yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    weightsKg.forEach { kg ->
                        InputChip(
                            selected = false,
                            onClick = { haptics.reject(); onChange(weightsKg - kg) },
                            label = { Text(Format.weight(kg, unit)) },
                            trailingIcon = { Icon(Icons.Rounded.Close, "Remove") },
                            modifier = Modifier.heightIn(min = Sizes.touch),
                        )
                    }
                }
                TextButton(onClick = { haptics.reject(); onChange(emptyList()) }) {
                    Text("Clear all")
                }
            }

            Spacer(Modifier.height(Spacing.lg))
            Text("Add one weight", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(Spacing.sm))
            Row(verticalAlignment = Alignment.CenterVertically) {
                NumberField(single, { single = it }, unit.symbol, Modifier.weight(1f), ImeAction.Done)
                FilledTonalButton(
                    onClick = ::addSingle,
                    modifier = Modifier.padding(start = Spacing.sm).heightIn(min = Sizes.bigTouch),
                ) { Text("Add") }
            }

            Spacer(Modifier.height(Spacing.xl))
            Text("Add a range (adjustable equipment)", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(Spacing.sm))
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                NumberField(from, { from = it }, "From", Modifier.weight(1f))
                NumberField(to, { to = it }, "To", Modifier.weight(1f))
                NumberField(step, { step = it }, "Step", Modifier.weight(1f), ImeAction.Done)
            }
            if (rangeError != null) {
                Text(
                    rangeError!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = Spacing.xs),
                )
            }
            Spacer(Modifier.height(Spacing.sm))
            FilledTonalButton(
                onClick = ::addRange,
                modifier = Modifier.fillMaxWidth().heightIn(min = Sizes.bigTouch),
            ) { Text("Add range") }
        }
    }
}

@Composable
private fun NumberField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    imeAction: ImeAction = ImeAction.Next,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { text -> onValueChange(text.filter { it.isDigit() || it == '.' || it == ',' }) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = imeAction),
        modifier = modifier,
    )
}

package app.forge.fitness.feature.exercises

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.forge.domain.bodyweight.BodyweightProfile
import app.forge.domain.model.Equipment
import app.forge.domain.model.LogType
import app.forge.domain.model.Muscle
import app.forge.fitness.ui.components.SectionHeader
import app.forge.fitness.ui.components.rememberHaptics
import app.forge.fitness.ui.theme.Sizes
import app.forge.fitness.ui.theme.Spacing
import kotlinx.coroutines.launch

private val logTypeLabels = listOf(
    LogType.WEIGHT_REPS to "Weight × reps",
    LogType.REPS to "Reps",
    LogType.DURATION to "Time",
    LogType.DISTANCE_DURATION to "Distance",
)

/** Create or edit one of your own exercises. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ExerciseEditScreen(
    onClose: () -> Unit,
    onSaved: (String) -> Unit,
    vm: ExerciseEditViewModel = hiltViewModel(),
) {
    val form by vm.form.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()

    fun save() = scope.launch {
        val id = vm.save()
        if (id != null) { haptics.success(); onSaved(id) } else haptics.reject()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(if (form.isNew) "New exercise" else "Edit exercise") },
                navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, "Cancel") } },
                actions = { Button(onClick = { save() }, modifier = Modifier.padding(end = Spacing.sm)) { Text("Save") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        if (!form.loaded) return@Scaffold
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screen)
                .padding(bottom = Spacing.xxl),
        ) {
            OutlinedTextField(
                value = form.name,
                onValueChange = { v -> vm.update { it.copy(name = v) } },
                label = { Text("Name") },
                placeholder = { Text("e.g. Backpack Row") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                isError = form.error != null && form.name.isBlank(),
                modifier = Modifier.fillMaxWidth(),
            )
            form.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = Spacing.xs))
            }

            SectionHeader("How you log it")
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                logTypeLabels.forEachIndexed { i, (type, label) ->
                    SegmentedButton(
                        selected = form.logType == type,
                        onClick = {
                            vm.update {
                                it.copy(logType = type, bodyweightProfile = it.bodyweightProfile.takeIf { type == LogType.REPS })
                            }
                        },
                        shape = SegmentedButtonDefaults.itemShape(i, logTypeLabels.size),
                        modifier = Modifier.heightIn(min = Sizes.touch),
                    ) { Text(label, maxLines = 1) }
                }
            }

            if (form.logType == LogType.REPS) {
                SectionHeader("Bodyweight type")
                Text(
                    "Pick the closest movement and Forge works out how much of your bodyweight you lift.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(Spacing.sm))
                ProfilePicker(form.bodyweightProfile, vm::setBodyweightProfile)
                form.bodyweightProfile?.let { profile ->
                    Text(
                        "${(profile.fraction * 100).toInt()}% of bodyweight${if (profile.unilateral) ", one side" else ""}. ${profile.note}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = Spacing.xs),
                    )
                }
                if (form.needsElevation) {
                    Spacer(Modifier.height(Spacing.sm))
                    OutlinedTextField(
                        value = form.elevationCm,
                        onValueChange = { v -> vm.update { it.copy(elevationCm = v.filter { c -> c.isDigit() || c == '.' || c == ',' }) } },
                        label = { Text("Height of the bench or box") },
                        suffix = { Text("cm") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            SectionHeader("Equipment")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Equipment.entries.forEach { item ->
                    FilterChip(
                        selected = form.equipment == item,
                        onClick = { vm.update { it.copy(equipment = if (it.equipment == item) null else item) } },
                        label = { Text(item.label) },
                    )
                }
            }

            SectionHeader("Main muscles")
            MuscleChips(form.primary) { m -> vm.update { it.copy(primary = it.primary.toggle(m)) } }
            SectionHeader("Also works (optional)")
            MuscleChips(form.secondary) { m -> vm.update { it.copy(secondary = it.secondary.toggle(m)) } }

            SectionHeader("Instructions (optional)")
            OutlinedTextField(
                value = form.instructions,
                onValueChange = { v -> vm.update { it.copy(instructions = v) } },
                placeholder = { Text("One step per line") },
                minLines = 4,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(Spacing.xl))
            Button(onClick = { save() }, modifier = Modifier.fillMaxWidth().heightIn(min = Sizes.bigTouch)) {
                Text("Save exercise")
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MuscleChips(selected: Set<Muscle>, onToggle: (Muscle) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Muscle.entries.sortedBy { it.label }.forEach { m ->
            FilterChip(selected = m in selected, onClick = { onToggle(m) }, label = { Text(m.label) })
        }
    }
}

@Composable
private fun ProfilePicker(selected: BodyweightProfile?, onPick: (BodyweightProfile?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth().heightIn(min = Sizes.touch)) {
            Text(selected?.label ?: "None: only count added weight")
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text("None") }, onClick = { open = false; onPick(null) })
            BodyweightProfile.entries.forEach { p ->
                DropdownMenuItem(
                    text = { Text("${p.label}  ·  ${(p.fraction * 100).toInt()}%") },
                    onClick = { open = false; onPick(p) },
                )
            }
        }
    }
}

private fun <T> Set<T>.toggle(item: T): Set<T> = if (item in this) this - item else this + item

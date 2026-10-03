package app.forge.fitness.feature.activity

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.forge.domain.activity.ActivityFatigue
import app.forge.domain.activity.ActivityKind
import app.forge.domain.activity.Sport
import app.forge.domain.calc.Units
import app.forge.fitness.ui.components.ConfirmDialog
import app.forge.fitness.ui.components.ForgeCard
import app.forge.fitness.ui.components.SectionHeader
import app.forge.fitness.ui.components.rememberHaptics
import app.forge.fitness.ui.theme.Sizes
import app.forge.fitness.ui.theme.Spacing
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/** Log (or edit) a sport, cardio or mobility session. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ActivityEditScreen(
    onClose: () -> Unit,
    vm: ActivityEditViewModel = hiltViewModel(),
) {
    val form by vm.form.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    val is24Hour = android.text.format.DateFormat.is24HourFormat(LocalContext.current)
    var pickDate by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    fun save() = scope.launch {
        if (vm.save() != null) { haptics.success(); onClose() } else haptics.reject()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(if (form.isNew) "Log activity" else "Edit activity") },
                navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, "Cancel") } },
                actions = {
                    if (!form.isNew) {
                        IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Rounded.Delete, "Delete") }
                    }
                    Button(onClick = { save() }, modifier = Modifier.padding(end = Spacing.sm)) { Text("Save") }
                },
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
            if (form.fromHealthConnect) {
                StrapDetails(form)
            }

            ActivityKind.entries.forEach { kind ->
                SectionHeader(kind.label)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Sport.entries.filter { it.kind == kind }.forEach { sport ->
                        FilterChip(
                            selected = form.sport == sport,
                            onClick = { vm.setSport(sport) },
                            label = { Text(sport.label) },
                        )
                    }
                }
            }

            SectionHeader("When")
            val today = LocalDate.now()
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.Center) {
                FilterChip(selected = form.date == today, onClick = { vm.update { it.copy(date = today) } }, label = { Text("Today") })
                FilterChip(
                    selected = form.date == today.minusDays(1),
                    onClick = { vm.update { it.copy(date = today.minusDays(1)) } },
                    label = { Text("Yesterday") },
                )
                FilterChip(
                    selected = form.date < today.minusDays(1),
                    onClick = { pickDate = true },
                    label = {
                        Text(if (form.date < today.minusDays(1)) form.date.format(DateTimeFormatter.ofPattern("EEE d MMM")) else "Other day")
                    },
                )
            }
            OutlinedButton(
                onClick = { pickTime = true },
                modifier = Modifier.fillMaxWidth().heightIn(min = Sizes.touch).padding(top = Spacing.xs),
            ) {
                Text("Started at " + form.startTime.format(DateTimeFormatter.ofPattern(if (is24Hour) "HH:mm" else "h:mm a")))
            }

            SectionHeader("How long and how hard")
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                OutlinedTextField(
                    value = form.minutes,
                    onValueChange = { v -> vm.update { it.copy(minutes = v.filter(Char::isDigit).take(4)) } },
                    label = { Text("Duration") },
                    suffix = { Text("min") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
                if (form.sport.hasDistance || form.distanceKm.isNotBlank()) {
                    OutlinedTextField(
                        value = form.distanceKm,
                        onValueChange = { v -> vm.update { it.copy(distanceKm = v.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(6)) } },
                        label = { Text("Distance") },
                        suffix = { Text("km") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Spacer(Modifier.height(Spacing.sm))
            Text("Effort: ${form.intensity}/10 · ${effortWord(form.intensity)}", style = MaterialTheme.typography.titleSmall)
            Slider(
                value = form.intensity.toFloat(),
                onValueChange = { v -> vm.update { it.copy(intensity = v.roundToInt()) } },
                valueRange = 1f..10f,
                steps = 8,
            )
            RecoveryPreview(form)

            form.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = Spacing.sm))
            }

            SectionHeader("Details (optional)")
            OutlinedTextField(
                value = form.title,
                onValueChange = { v -> vm.update { it.copy(title = v) } },
                label = { Text("Name") },
                placeholder = { Text("e.g. Saturday match") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(Spacing.sm))
            OutlinedTextField(
                value = form.notes,
                onValueChange = { v -> vm.update { it.copy(notes = v) } },
                label = { Text("Notes") },
                minLines = 3,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(Spacing.xl))
            Button(onClick = { save() }, modifier = Modifier.fillMaxWidth().heightIn(min = Sizes.bigTouch)) {
                Text(if (form.isNew) "Save activity" else "Save changes")
            }
        }
    }

    if (pickDate) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = form.date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { ms ->
                        val day = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()
                        vm.update { it.copy(date = minOf(day, LocalDate.now())) }
                    }
                    pickDate = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { pickDate = false }) { Text("Cancel") } },
        ) { DatePicker(state) }
    }
    if (pickTime) {
        val state = rememberTimePickerState(form.startTime.hour, form.startTime.minute, is24Hour)
        AlertDialog(
            onDismissRequest = { pickTime = false },
            title = { Text("Start time") },
            text = { TimePicker(state) },
            confirmButton = {
                TextButton(onClick = {
                    vm.update { it.copy(startTime = LocalTime.of(state.hour, state.minute)) }
                    pickTime = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { pickTime = false }) { Text("Cancel") } },
        )
    }
    if (confirmDelete) {
        ConfirmDialog(
            title = "Delete this activity?",
            message = if (form.fromHealthConnect) {
                "It won't be imported again from Health Connect."
            } else {
                "This removes it from your history and recovery."
            },
            confirmLabel = "Delete",
            destructive = true,
            onConfirm = {
                confirmDelete = false
                scope.launch { vm.delete(); onClose() }
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

/** Heart rate and calories your watch/strap recorded (not editable). */
@Composable
private fun StrapDetails(form: ActivityForm) {
    ForgeCard(modifier = Modifier.padding(top = Spacing.sm)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Favorite, null, tint = MaterialTheme.colorScheme.tertiary)
            Text("  From your watch/strap", style = MaterialTheme.typography.titleSmall)
        }
        val parts = listOfNotNull(
            form.avgHeartRate?.let { "Avg HR $it bpm" },
            form.maxHeartRate?.let { "Max $it bpm" },
            form.calories?.let { "${it.roundToInt()} kcal" },
        )
        Text(
            parts.joinToString(" · ").ifEmpty { "Imported from Health Connect" },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** "Counts like about 4 hard sets for Quads, Hamstrings…" so the recovery effect is visible. */
@Composable
private fun RecoveryPreview(form: ActivityForm) {
    val minutes = form.minutes.toIntOrNull() ?: return
    val work = ActivityFatigue.muscleWork(form.sport, 0, minutes, form.intensity)
    val text = if (work == null) {
        "Doesn't affect muscle recovery: a good rest-day activity."
    } else {
        val muscles = work.primary.ifEmpty { work.secondary }.joinToString { it.label }
        "For recovery this counts like about ${Units.format(work.sets)} hard sets for $muscles."
    }
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

private fun effortWord(intensity: Int): String = when (intensity) {
    in 1..2 -> "very easy"
    in 3..4 -> "easy"
    in 5..6 -> "moderate"
    in 7..8 -> "hard"
    else -> "all-out"
}

package app.forge.fitness.feature.goals

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import app.forge.domain.calc.Units
import app.forge.domain.goals.DayMask
import app.forge.domain.goals.GoalKind
import app.forge.domain.goals.HabitKind
import app.forge.fitness.data.db.ExerciseEntity
import app.forge.fitness.data.db.HabitEntity
import app.forge.fitness.ui.theme.Sizes
import app.forge.fitness.ui.theme.Spacing
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

private fun parse(text: String) = text.trim().replace(',', '.').toDoubleOrNull()

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun HabitEditor(
    habit: HabitEntity?,
    onDismiss: () -> Unit,
    onSave: (id: String?, kind: HabitKind, name: String, target: Double?, due: DayMask, reminderMinutes: Int?) -> Unit,
    onDelete: (String) -> Unit,
) {
    val is24h = android.text.format.DateFormat.is24HourFormat(LocalContext.current)
    var kind by remember { mutableStateOf(habit?.let { h -> HabitKind.entries.firstOrNull { it.name == h.kind } } ?: HabitKind.CUSTOM) }
    var name by remember { mutableStateOf(habit?.name.orEmpty()) }
    var target by remember { mutableStateOf(habit?.target?.let { Units.format(it) }.orEmpty()) }
    var due by remember { mutableStateOf(DayMask(habit?.dayMask ?: DayMask.ALL.bits)) }
    var remind by remember { mutableStateOf(habit?.reminderMinutes != null) }
    var pickTime by remember { mutableStateOf(false) }
    var minutes by remember { mutableStateOf(habit?.reminderMinutes ?: (20 * 60)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (habit == null) "New habit" else "Edit habit") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                if (habit == null) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        HabitKind.entries.forEach { k ->
                            FilterChip(
                                selected = kind == k,
                                onClick = {
                                    kind = k
                                    val default = k.defaultTarget
                                    if (default != null && target.isBlank()) target = Units.format(default)
                                },
                                label = { Text(k.label) },
                            )
                        }
                    }
                }
                if (kind.auto) {
                    Text(
                        "Ticks itself from your " + when (kind) {
                            HabitKind.TRAIN -> "workouts and activities."
                            HabitKind.PROTEIN -> "food log (within 5% of your protein target)."
                            HabitKind.LOG_FOOD -> "food log (2+ items in a day)."
                            HabitKind.STEPS, HabitKind.SLEEP -> "watch or strap, through Health Connect."
                            HabitKind.CUSTOM -> ""
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    placeholder = { Text(if (kind == HabitKind.CUSTOM) "e.g. Stretch 10 min" else kind.label) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (kind.unit != null) {
                    OutlinedTextField(
                        value = target,
                        onValueChange = { v -> target = v.filter { it.isDigit() || it == '.' || it == ',' }.take(6) },
                        label = { Text("At least") },
                        suffix = { Text(kind.unit!!) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Text("Days", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    DayOfWeek.entries.forEach { d ->
                        FilterChip(
                            selected = due.isDue(d),
                            onClick = { due = due.toggle(d) },
                            label = { Text(d.getDisplayName(TextStyle.SHORT, Locale.getDefault())) },
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.heightIn(min = Sizes.touch)) {
                    Column(Modifier.weight(1f).clickable(enabled = remind) { pickTime = true }) {
                        Text("Reminder", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            if (remind) "%s (tap to change)".format(timeText(minutes, is24h)) else "Off",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = remind, onCheckedChange = { remind = it; if (it) pickTime = true })
                }
                if (habit != null) {
                    TextButton(onClick = { onDelete(habit.id) }) { Text("Remove habit", color = MaterialTheme.colorScheme.error) }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(habit?.id, kind, name.trim(), parse(target), due, minutes.takeIf { remind })
                },
                enabled = due.count > 0 && (kind != HabitKind.CUSTOM || name.isNotBlank()),
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )

    if (pickTime) {
        val state = rememberTimePickerState(minutes / 60, minutes % 60, is24h)
        AlertDialog(
            onDismissRequest = { pickTime = false },
            title = { Text("Remind me at") },
            text = { TimePicker(state) },
            confirmButton = { TextButton(onClick = { minutes = state.hour * 60 + state.minute; pickTime = false }) { Text("OK") } },
            dismissButton = { TextButton(onClick = { pickTime = false }) { Text("Cancel") } },
        )
    }
}

private fun timeText(minutes: Int, is24h: Boolean): String {
    val h = minutes / 60
    val m = minutes % 60
    return if (is24h) "%02d:%02d".format(h, m) else "%d:%02d %s".format(if (h % 12 == 0) 12 else h % 12, m, if (h < 12) "am" else "pm")
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun GoalEditor(
    exercises: List<ExerciseEntity>,
    onDismiss: () -> Unit,
    onSave: (GoalKind, Double, String?) -> Unit,
) {
    var kind by remember { mutableStateOf(GoalKind.WORKOUTS_PER_WEEK) }
    var target by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }
    var exercise by remember { mutableStateOf<ExerciseEntity?>(null) }
    val needsExercise = kind == GoalKind.LIFT || kind == GoalKind.REPS
    val value = parse(target)?.takeIf { it > 0 }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New goal") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    GoalKind.entries.forEach { k ->
                        FilterChip(selected = kind == k, onClick = { kind = k }, label = { Text(k.label) })
                    }
                }
                Text(kind.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (needsExercise) {
                    if (exercise == null) {
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            label = { Text("Exercise") },
                            placeholder = { Text("Search, e.g. squat") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        if (query.length >= 2) {
                            exercises.filter { it.name.contains(query.trim(), ignoreCase = true) }.take(6).forEach { e ->
                                Text(
                                    e.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.fillMaxWidth().heightIn(min = Sizes.touch).clickable { exercise = e }.padding(vertical = Spacing.xs),
                                )
                            }
                        }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(exercise!!.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                            TextButton(onClick = { exercise = null }) { Text("Change") }
                        }
                    }
                }
                OutlinedTextField(
                    value = target,
                    onValueChange = { v -> target = v.filter { it.isDigit() || it == '.' || it == ',' }.take(6) },
                    label = { Text("Target") },
                    suffix = {
                        Text(
                            when (kind) {
                                GoalKind.WORKOUTS_PER_WEEK, GoalKind.PROTEIN_DAYS -> "per week"
                                GoalKind.BODYWEIGHT, GoalKind.LIFT -> "kg"
                                GoalKind.REPS -> "reps"
                            },
                        )
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (kind == GoalKind.BODYWEIGHT) {
                    Text(
                        "Progress is measured from your latest logged weight. Slow and steady (about 0.25–0.5 kg a " +
                            "week) keeps your training going.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(kind, value!!, exercise?.id) },
                enabled = value != null && (!needsExercise || exercise != null) &&
                    (kind != GoalKind.WORKOUTS_PER_WEEK && kind != GoalKind.PROTEIN_DAYS || value <= 7),
            ) { Text("Add goal") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

package app.forge.fitness.feature.routines

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import app.forge.fitness.ui.components.BigButton
import app.forge.fitness.ui.components.ConfirmDialog
import app.forge.fitness.ui.components.rememberHaptics
import app.forge.fitness.ui.theme.Sizes
import app.forge.fitness.ui.theme.Spacing
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.coroutines.launch

/** "Mon, Wed, Fri" or "Any day". */
fun daysLabel(days: Set<DayOfWeek>): String =
    if (days.isEmpty()) "Any day" else days.sorted().joinToString { it.getDisplayName(TextStyle.SHORT, Locale.getDefault()) }

/**
 * Starting a routine while another workout is running asks first: you can resume the
 * running one instead. Returns a function that starts [routineId].
 */
@Composable
fun rememberRoutineStarter(
    start: suspend (String) -> StartResult?,
    onStarted: () -> Unit,
    onResume: () -> Unit,
): (String) -> Unit {
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    var blocked by remember { mutableStateOf(false) }
    if (blocked) {
        ConfirmDialog(
            title = "Workout already running",
            message = "Finish or discard the workout in progress before starting another.",
            confirmLabel = "Go to workout",
            dismissLabel = "Cancel",
            onConfirm = { blocked = false; onResume() },
            onDismiss = { blocked = false },
        )
    }
    return { id ->
        scope.launch {
            when (start(id)) {
                is StartResult.Started -> { haptics.success(); onStarted() }
                StartResult.WorkoutInProgress -> { haptics.reject(); blocked = true }
                null -> Unit
            }
        }
    }
}

/** "Today: Full Body B" with a start button, or a rest-day message. */
@Composable
fun ProgramPlanCard(
    plan: ProgramPlan,
    onStart: (String) -> Unit,
    onEditDays: (() -> Unit)? = null,
    onStop: (() -> Unit)? = null,
) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(Modifier.fillMaxWidth().padding(Spacing.lg)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.CalendarMonth, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                Text(
                    "  ${plan.program.name}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.weight(1f),
                )
                if (onEditDays != null || onStop != null) {
                    var open by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { open = true }) {
                            Icon(Icons.Rounded.MoreVert, "Program options", tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                            onEditDays?.let { DropdownMenuItem(text = { Text("Training days") }, onClick = { open = false; it() }) }
                            onStop?.let { DropdownMenuItem(text = { Text("Stop following program") }, onClick = { open = false; it() }) }
                        }
                    }
                }
            }
            val next = plan.next
            val p = plan.plan
            val headline = when {
                next == null -> "This program has no routines"
                p.doneToday -> "Done for today. Next: ${next.name}"
                !p.isTrainingDay -> "Rest day. Next up: ${next.name}"
                else -> "Today: ${next.name}"
            }
            Text(headline, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
            val whenText = p.nextTrainingDate?.let { "Next training day: ${relativeDay(it)}" }
            Text(
                listOfNotNull(next?.let { "~${it.minutes} min · ${it.data.active.size} exercises" }, whenText, "Days: ${daysLabel(plan.days)}")
                    .joinToString("\n"),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            if (next != null) {
                Spacer(Modifier.height(Spacing.md))
                BigButton(
                    text = if (p.isTrainingDay && !p.doneToday) "Start ${next.name}" else "Train anyway: ${next.name}",
                    icon = Icons.Rounded.PlayArrow,
                    onClick = { onStart(next.id) },
                )
            }
        }
    }
}

private fun relativeDay(date: LocalDate): String {
    val today = LocalDate.now()
    return when (date) {
        today -> "today"
        today.plusDays(1) -> "tomorrow"
        else -> date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
    }
}

/** One routine in a list: name, exercises, time, start, and a ⋮ menu. */
@Composable
fun RoutineCard(
    routine: RoutineCardData,
    onStart: () -> Unit,
    onOpen: () -> Unit,
    menu: List<Pair<String, () -> Unit>> = emptyList(),
) {
    Card(
        onClick = onOpen,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(Modifier.fillMaxWidth().padding(start = Spacing.lg, top = Spacing.md, bottom = Spacing.md), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(routine.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    routine.summary.ifEmpty { "No exercises yet" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "~${routine.minutes} min · ${routine.data.active.size} exercises",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(top = Spacing.xs),
                )
            }
            FilledTonalButton(
                onClick = onStart,
                enabled = routine.data.active.isNotEmpty(),
                modifier = Modifier.heightIn(min = Sizes.touch),
            ) { Text("Start") }
            if (menu.isNotEmpty()) {
                var open by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { open = true }) { Icon(Icons.Rounded.MoreVert, "Routine options") }
                    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                        menu.forEach { (label, action) ->
                            if (label == "-") HorizontalDivider()
                            else DropdownMenuItem(text = { Text(label) }, onClick = { open = false; action() })
                        }
                    }
                }
            } else {
                Spacer(Modifier.padding(end = Spacing.lg))
            }
        }
    }
}

/** Pick training days; none selected = any day. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TrainingDaysDialog(initial: Set<DayOfWeek>, onConfirm: (Set<DayOfWeek>) -> Unit, onDismiss: () -> Unit) {
    var days by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Training days") },
        text = {
            Column {
                Text(
                    "Routines rotate in order on these days. Miss one and the next routine just waits. " +
                        "Pick none to train whenever you like.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(Spacing.md))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    DayOfWeek.entries.forEach { d ->
                        FilterChip(
                            selected = d in days,
                            onClick = { days = if (d in days) days - d else days + d },
                            label = { Text(d.getDisplayName(TextStyle.SHORT, Locale.getDefault())) },
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(days) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

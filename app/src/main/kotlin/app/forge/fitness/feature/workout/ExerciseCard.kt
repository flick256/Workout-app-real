package app.forge.fitness.feature.workout

import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.forge.domain.model.WeightUnit
import app.forge.fitness.ui.components.ForgeCard
import app.forge.fitness.ui.format.Format
import app.forge.fitness.ui.theme.Sizes
import app.forge.fitness.ui.theme.Spacing

/** Everything you can do from an exercise's ⋮ menu. */
internal data class ExerciseActions(
    val onAddWarmups: () -> Unit,
    val onNotes: () -> Unit,
    val onRest: () -> Unit,
    val onSupersetNext: (() -> Unit)?,
    val onLeaveSuperset: (() -> Unit)?,
    val onMoveUp: (() -> Unit)?,
    val onMoveDown: (() -> Unit)?,
    val onRemove: () -> Unit,
)

@Composable
internal fun ExerciseCard(
    block: ExerciseBlock,
    unit: WeightUnit,
    actions: ExerciseActions,
    vm: ActiveWorkoutViewModel,
    onInfo: () -> Unit,
) {
    val supersetColor = MaterialTheme.colorScheme.secondary
    ForgeCard(
        modifier = if (block.supersetLetter != null) {
            Modifier.border(2.dp, supersetColor.copy(alpha = 0.6f), MaterialTheme.shapes.large)
        } else {
            Modifier
        },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                if (block.supersetLetter != null) {
                    Text(
                        "SUPERSET ${block.supersetLetter}",
                        style = MaterialTheme.typography.labelSmall,
                        color = supersetColor,
                    )
                }
                TextButton(
                    onClick = onInfo,
                    contentPadding = PaddingValues(0.dp),
                ) {
                    Text(
                        block.exercise.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Text(
                    "Rest ${Format.duration(block.restSeconds.toLong())}" +
                        (block.exercise.equipment?.let { " · ${it.label}" } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            ExerciseMenu(actions)
        }

        block.item.notes?.let {
            Surface(
                onClick = actions.onNotes,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm),
            ) {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(Spacing.md),
                )
            }
        }

        if (block.ownedWeights.isNotEmpty()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(top = Spacing.sm),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                block.ownedWeights.forEach { kg ->
                    AssistChip(
                        onClick = { vm.fillNextWeight(block, kg) },
                        label = { Text(Format.weight(kg, unit)) },
                    )
                }
            }
        }

        Spacer(Modifier.height(Spacing.sm))
        SetHeader(block.exercise.logType, unit)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            block.rows.forEach { row ->
                // key() keeps each row's typing state attached to its set when rows move.
                key(row.set.id) {
                    SetRowView(
                        row = row,
                        logType = block.exercise.logType,
                        unit = unit,
                        onWeight = { vm.setWeight(row.set.id, it) },
                        onReps = { vm.setReps(row.set.id, it) },
                        onDuration = { vm.setDuration(row.set.id, it) },
                        onDistance = { vm.setDistance(row.set.id, it) },
                        onRpe = { vm.setRpe(row.set.id, it) },
                        onType = { vm.setType(row.set.id, it) },
                        onDelete = { vm.deleteSet(row.set.id) },
                        onToggleDone = { vm.toggleComplete(block, row) },
                    )
                }
            }
        }
        TextButton(
            onClick = { vm.addSet(block) },
            modifier = Modifier.fillMaxWidth().heightIn(min = Sizes.touch),
        ) {
            Icon(Icons.Rounded.Add, null)
            Text("  Add set")
        }
    }
}

@Composable
private fun ExerciseMenu(actions: ExerciseActions) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }, modifier = Modifier.heightIn(min = Sizes.touch)) {
            Icon(Icons.Rounded.MoreVert, contentDescription = "Exercise options")
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            MenuItem("Add warm-up sets") { open = false; actions.onAddWarmups() }
            MenuItem("Notes") { open = false; actions.onNotes() }
            MenuItem("Rest timer") { open = false; actions.onRest() }
            actions.onSupersetNext?.let { MenuItem("Superset with next") { open = false; it() } }
            actions.onLeaveSuperset?.let { MenuItem("Remove from superset") { open = false; it() } }
            actions.onMoveUp?.let { MenuItem("Move up") { open = false; it() } }
            actions.onMoveDown?.let { MenuItem("Move down") { open = false; it() } }
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text("Remove exercise", color = MaterialTheme.colorScheme.error) },
                onClick = { open = false; actions.onRemove() },
            )
        }
    }
}

@Composable
private fun MenuItem(text: String, onClick: () -> Unit) {
    DropdownMenuItem(text = { Text(text) }, onClick = onClick)
}

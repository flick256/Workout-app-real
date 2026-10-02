package app.forge.fitness.feature.exercises

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.foundation.layout.Row
import app.forge.fitness.ui.components.ExerciseThumb
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import app.forge.domain.model.Muscle
import app.forge.fitness.data.db.ExerciseEntity
import app.forge.fitness.ui.components.EmptyState
import app.forge.fitness.ui.components.SectionHeader
import app.forge.fitness.ui.theme.Spacing

/**
 * Search + filters + the list. Used by the exercise picker (multi-select) and by the
 * Exercises tab (tap for instructions).
 */
@Composable
fun ExerciseList(
    state: ExerciseListState,
    onQuery: (String) -> Unit,
    onMuscle: (Muscle?) -> Unit,
    onMyEquipment: (Boolean) -> Unit,
    onClick: (ExerciseEntity) -> Unit,
    onCustomOnly: (Boolean) -> Unit = {},
    /** If set, rows get an ⓘ button (used in the picker, where tapping selects). */
    onInfo: ((ExerciseEntity) -> Unit)? = null,
    selected: Set<String> = emptySet(),
    contentPadding: PaddingValues = PaddingValues(),
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize()) {
        OutlinedTextField(
            value = state.query,
            onValueChange = onQuery,
            placeholder = { Text("Search exercises") },
            leadingIcon = { Icon(Icons.Rounded.Search, null) },
            trailingIcon = if (state.query.isNotEmpty()) {
                { IconButton(onClick = { onQuery("") }) { Icon(Icons.Rounded.Clear, "Clear search") } }
            } else null,
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.screen),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = Spacing.screen, vertical = Spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            item {
                FilterChip(
                    selected = state.customOnly,
                    onClick = { onCustomOnly(!state.customOnly) },
                    label = { Text("My exercises") },
                )
            }
            item {
                FilterChip(
                    selected = state.myEquipmentOnly && !state.customOnly,
                    onClick = { onMyEquipment(!state.myEquipmentOnly) },
                    label = { Text("My equipment") },
                )
            }
            items(Muscle.entries.sortedBy { it.label }) { m ->
                FilterChip(
                    selected = state.muscle == m,
                    onClick = { onMuscle(if (state.muscle == m) null else m) },
                    label = { Text(m.label) },
                )
            }
        }

        when {
            state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Text("Preparing the exercise library…", modifier = Modifier.padding(top = Spacing.md))
                }
            }
            state.all.isEmpty() -> EmptyState(
                icon = Icons.Rounded.SearchOff,
                title = "No matches",
                body = if (state.myEquipmentOnly) {
                    "Try turning off \"My equipment\" or a different search."
                } else {
                    "Try a different search, or create your own exercise."
                },
            )
            else -> LazyColumn(contentPadding = contentPadding, modifier = Modifier.fillMaxSize()) {
                if (state.recent.isNotEmpty() && state.query.isEmpty()) {
                    item(key = "h-recent") { SectionHeader("Recent", Modifier.padding(horizontal = Spacing.screen)) }
                    items(state.recent, key = { "r-" + it.id }) { e -> ExerciseRow(e, e.id in selected, onClick, onInfo) }
                    item(key = "h-all") { SectionHeader("All exercises", Modifier.padding(horizontal = Spacing.screen)) }
                }
                items(state.all, key = { it.id }) { e -> ExerciseRow(e, e.id in selected, onClick, onInfo) }
            }
        }
    }
}

@Composable
private fun ExerciseRow(
    exercise: ExerciseEntity,
    selected: Boolean,
    onClick: (ExerciseEntity) -> Unit,
    onInfo: ((ExerciseEntity) -> Unit)?,
) {
    ListItem(
        leadingContent = { ExerciseThumb(exercise.images, 48.dp) },
        headlineContent = { Text(exercise.name) },
        supportingContent = {
            Text(
                listOfNotNull(
                    exercise.primaryMuscles.firstOrNull()?.label,
                    exercise.equipment?.label,
                    "Bodyweight load".takeIf { exercise.bodyweightProfile != null },
                    "Custom".takeIf { exercise.isCustom },
                    "Archived".takeIf { exercise.archived },
                ).joinToString(" · "),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (selected) {
                    Icon(Icons.Rounded.CheckCircle, "Selected", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                }
                if (onInfo != null) {
                    IconButton(onClick = { onInfo(exercise) }) { Icon(Icons.Outlined.Info, "About ${exercise.name}") }
                }
            }
        },
        colors = ListItemDefaults.colors(
            containerColor = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.10f) else Color.Transparent,
        ),
        modifier = Modifier.clickable { onClick(exercise) },
    )
}

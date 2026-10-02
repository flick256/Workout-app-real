package app.forge.fitness.feature.exercises

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.forge.fitness.data.db.ExerciseEntity
import app.forge.fitness.ui.theme.Spacing

/** How to do an exercise: muscles, equipment and step-by-step instructions. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ExerciseInfoSheet(exercise: ExerciseEntity, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screen)
                .navigationBarsPadding()
                .padding(bottom = Spacing.xl),
        ) {
            Text(exercise.name, style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(Spacing.sm))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                exercise.primaryMuscles.forEach { SuggestionChip(onClick = {}, label = { Text(it.label) }) }
                exercise.equipment?.let { SuggestionChip(onClick = {}, label = { Text(it.label) }) }
                exercise.level?.let { SuggestionChip(onClick = {}, label = { Text(it.replaceFirstChar(Char::uppercase)) }) }
            }
            if (exercise.secondaryMuscles.isNotEmpty()) {
                Text(
                    "Also works: " + exercise.secondaryMuscles.joinToString { it.label.lowercase() },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(Spacing.lg))
            if (exercise.instructions.isEmpty()) {
                Text("No instructions for this exercise.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Text("How to", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(Spacing.sm))
                exercise.instructions.forEachIndexed { i, step ->
                    Row(Modifier.padding(vertical = Spacing.xs)) {
                        Text(
                            "${i + 1}.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(Spacing.sm))
                        Text(step, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
    }
}

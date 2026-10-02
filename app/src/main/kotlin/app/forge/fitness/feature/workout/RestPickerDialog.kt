package app.forge.fitness.feature.workout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import app.forge.fitness.data.prefs.UserPreferences
import app.forge.fitness.ui.format.Format
import app.forge.fitness.ui.theme.Spacing

/** Pick the rest time for one exercise in this workout. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RestPickerDialog(current: Int, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rest timer") },
        text = {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                (listOf(0) + UserPreferences.REST_OPTIONS).forEach { seconds ->
                    FilterChip(
                        selected = seconds == current,
                        onClick = { onPick(seconds) },
                        label = { Text(if (seconds == 0) "Off" else Format.duration(seconds.toLong())) },
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

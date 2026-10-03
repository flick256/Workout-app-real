package app.forge.fitness.feature.workout

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import app.forge.domain.model.WeightUnit
import app.forge.fitness.ui.format.Format
import app.forge.fitness.ui.theme.Spacing
import kotlinx.coroutines.launch

/**
 * Log sets by typing or speaking: "3x8 bench at 60", "squat 100 for 5", "plank 3x45s".
 * Speech uses your phone's own recogniser (offline where supported). Nothing is saved
 * until you confirm what Forge understood.
 */
@Composable
internal fun QuickLogDialog(
    unit: WeightUnit,
    interpret: suspend (String) -> ActiveWorkoutViewModel.QuickLogPreview?,
    onConfirm: (ActiveWorkoutViewModel.QuickLogPreview) -> Unit,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf("") }
    var preview by remember { mutableStateOf<ActiveWorkoutViewModel.QuickLogPreview?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun understand(input: String) {
        if (input.isBlank()) return
        busy = true; error = null; preview = null
        scope.launch {
            val p = interpret(input)
            busy = false
            if (p == null) error = "Couldn't read that. Try e.g. \"3x8 bench at 60\" or \"squat 100 for 5\"." else preview = p
        }
    }

    val speech = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let {
                text = it
                understand(it)
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Quick log") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it; preview = null; error = null },
                    placeholder = { Text("3x8 bench at 60") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { understand(text) }),
                    trailingIcon = {
                        IconButton(onClick = {
                            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                                .putExtra(RecognizerIntent.EXTRA_PROMPT, "Say a set, e.g. \"three by eight bench at sixty\"")
                            try {
                                speech.launch(intent)
                            } catch (_: ActivityNotFoundException) {
                                error = "No speech recogniser on this phone; type it instead."
                            }
                        }) { Icon(Icons.Rounded.Mic, "Speak") }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (busy) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Text("  Reading…", style = MaterialTheme.typography.bodySmall)
                    }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                preview?.let { p ->
                    val c = p.command
                    Text(p.exercise.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        buildString {
                            append("${c.sets} × ")
                            append(c.reps?.toString() ?: "${c.seconds}s")
                            c.weightKg?.let { append(" @ ${Format.weight(it, unit)}") }
                            c.rpe?.let { append(" · RPE ${Format.rpe(it)}") }
                        },
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        if (p.byAi) "Understood by the on-device AI. Check it's right." else "Ticks these sets off in this workout.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            val p = preview
            if (p != null) TextButton(onClick = { onConfirm(p) }) { Text("Log it") }
            else TextButton(onClick = { understand(text) }, enabled = text.isNotBlank() && !busy) { Text("Read") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

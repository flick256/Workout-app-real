package app.forge.fitness.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import app.forge.fitness.CrashLog
import app.forge.fitness.ui.theme.Spacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Shown once after a crash: the report stays on the phone unless you copy it. */
@Composable
fun CrashReportDialog() {
    val context = LocalContext.current
    var report by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { report = withContext(Dispatchers.IO) { CrashLog.read(context) } }
    val text = report ?: return
    fun close() {
        CrashLog.clear(context)
        report = null
    }
    AlertDialog(
        onDismissRequest = ::close,
        title = { Text("Forge closed unexpectedly") },
        text = {
            Column {
                Text(
                    "Sorry about that. Your data is safe. The details below stay on your phone; copy them if you want " +
                        "to report the problem.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.heightIn(max = 240.dp).verticalScroll(rememberScrollState()).padding(top = Spacing.sm),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Forge crash report", text))
                Toast.makeText(context, "Crash report copied", Toast.LENGTH_SHORT).show()
                close()
            }) { Text("Copy report") }
        },
        dismissButton = { TextButton(onClick = ::close) { Text("Dismiss") } },
    )
}

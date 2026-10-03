package app.forge.fitness.feature.ai

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.forge.fitness.data.ai.LlmEngine
import app.forge.fitness.data.ai.ModelManager
import app.forge.fitness.data.ai.ModelState
import app.forge.fitness.ui.components.ConfirmDialog
import app.forge.fitness.ui.components.ForgeCard
import app.forge.fitness.ui.components.SectionHeader
import app.forge.fitness.ui.theme.Sizes
import app.forge.fitness.ui.theme.Spacing
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

@HiltViewModel
class AiSettingsViewModel @Inject constructor(
    val models: ModelManager,
    private val engine: LlmEngine,
) : ViewModel() {
    val state = models.state

    suspend fun delete() {
        engine.unload()
        models.delete()
    }
}

/** Download, import or remove the optional on-device AI model, and what it's used for. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiSettingsScreen(onBack: () -> Unit, vm: AiSettingsViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var confirmDownload by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let(vm.models::importFrom)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("On-device AI") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = Spacing.screen, end = Spacing.screen, top = padding.calculateTopPadding(), bottom = Spacing.xxl),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            item(key = "status") {
                ForgeCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Memory, null, tint = MaterialTheme.colorScheme.primary)
                        Text("  Gemma 4 E2B", style = MaterialTheme.typography.titleLarge)
                    }
                    when (val s = state) {
                        ModelState.NotInstalled -> {
                            Text(
                                "Not installed. It's about 2.6 GB and runs entirely on your phone: nothing you log is sent anywhere.",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Button(onClick = { confirmDownload = true }, modifier = Modifier.fillMaxWidth().heightIn(min = Sizes.touch).padding(top = Spacing.sm)) {
                                Text("Download (Wi-Fi)")
                            }
                            OutlinedButton(
                                onClick = { importer.launch(arrayOf("*/*")) },
                                modifier = Modifier.fillMaxWidth().heightIn(min = Sizes.touch),
                            ) { Text("Import a model file") }
                        }
                        is ModelState.Downloading -> {
                            Text("Downloading… ${s.downloadedMb} MB" + (s.progress?.let { " (${(it * 100).toInt()}%)" } ?: ""))
                            if (s.progress != null) {
                                LinearProgressIndicator(progress = { s.progress ?: 0f }, modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xs))
                            } else {
                                LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = Spacing.xs))
                            }
                            Text(
                                "It keeps going in the background (see the notification). Waiting for Wi-Fi if you're on mobile data.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            TextButton(onClick = vm.models::cancelDownload) { Text("Cancel download") }
                        }
                        is ModelState.Importing -> {
                            Text("Copying the model into Forge…")
                            LinearProgressIndicator(progress = { s.progress ?: 0f }, modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xs))
                        }
                        is ModelState.Installed -> {
                            Text("Installed (${s.sizeMb} MB). Loads when needed and frees its memory after.", style = MaterialTheme.typography.bodyMedium)
                            TextButton(onClick = { confirmDelete = true }) { Text("Delete model", color = MaterialTheme.colorScheme.error) }
                        }
                        is ModelState.Failed -> {
                            Text(s.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.padding(top = Spacing.sm)) {
                                Button(onClick = { confirmDownload = true }) { Text("Try again") }
                                OutlinedButton(onClick = { importer.launch(arrayOf("*/*")) }) { Text("Import file") }
                            }
                            TextButton(onClick = {
                                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(ModelManager.MODEL_PAGE))) }
                            }) { Text("Open the model's page in your browser") }
                        }
                    }
                }
            }
            item(key = "uses-h") { SectionHeader("What it does") }
            item(key = "uses") {
                ForgeCard {
                    listOf(
                        "Weekly summary (Progress tab): turns your week's numbers into a short, friendly recap.",
                        "\"Why has it stalled?\" (any exercise's progress page): explains Forge's plateau check in plain words.",
                        "Quick log in a workout: understands messier phrasings than the built-in parser.",
                    ).forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = Spacing.xs)) }
                }
            }
            item(key = "safety-h") { SectionHeader("How it's kept honest") }
            item(key = "safety") {
                ForgeCard {
                    listOf(
                        "Forge's own rules work out every number and finding. The AI only puts them into words.",
                        "Before you see anything, Forge checks it: if it mentions a number that isn't in your data, or gives risky diet advice (fasting, cutting, supplements), Forge shows its own plain version instead.",
                        "Calorie and protein targets never come from the AI.",
                        "Everything works without it: no model means you just see the plain versions.",
                    ).forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = Spacing.xs)) }
                }
            }
        }
    }

    if (confirmDownload) {
        ConfirmDialog(
            title = "Download 2.6 GB?",
            message = "The model downloads over Wi-Fi only and needs about 3 GB free. It's Gemma 4 E2B by Google (Apache 2.0 licence), from Hugging Face.",
            confirmLabel = "Download",
            onConfirm = { confirmDownload = false; vm.models.startDownload(wifiOnly = true) },
            onDismiss = { confirmDownload = false },
        )
    }
    if (confirmDelete) {
        ConfirmDialog(
            title = "Delete the AI model?",
            message = "Frees about 2.6 GB. Summaries and explanations go back to Forge's plain versions. You can download it again any time.",
            confirmLabel = "Delete",
            destructive = true,
            onConfirm = { confirmDelete = false; scope.launch { vm.delete() } },
            onDismiss = { confirmDelete = false },
        )
    }
}

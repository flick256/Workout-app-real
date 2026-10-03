package app.forge.fitness.feature.backup

import app.forge.fitness.ui.components.ConfirmDialog
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.forge.fitness.ui.components.ForgeCard
import app.forge.fitness.ui.components.SectionHeader
import app.forge.fitness.ui.theme.Sizes
import app.forge.fitness.ui.theme.Spacing
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val whenFormat = DateTimeFormatter.ofPattern("EEE d MMM yyyy, h:mm a").withZone(ZoneId.systemDefault())

/** Export, Google Drive backup, on-phone snapshots and restore. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun BackupScreen(onBack: () -> Unit, vm: BackupViewModel = hiltViewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val prefs by vm.prefs.collectAsStateWithLifecycle()
    val stamp = LocalDate.now().toString()

    val exportJson = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { it?.let(vm::exportJson) }
    val exportCsv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { it?.let(vm::exportCsv) }
    val driveFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { it?.let(vm::chooseDriveFile) }
    val existingDriveFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(vm::chooseDriveFile) }
    val openFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(vm::openBackup) }
    var confirmStop by rememberSaveable { mutableStateOf(false) }
    if (confirmStop) {
        ConfirmDialog(
            title = "Turn off Drive backup?",
            message = "Forge stops updating your Drive file. The file itself stays in your Drive.",
            confirmLabel = "Turn off",
            destructive = true,
            onConfirm = { confirmStop = false; vm.stopDrive() },
            onDismiss = { confirmStop = false },
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Backup & restore") },
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
            ui.busy?.let { label ->
                item(key = "busy") {
                    Column {
                        Text(label, style = MaterialTheme.typography.bodyMedium)
                        LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = Spacing.xs))
                    }
                }
            }
            ui.message?.let { m -> item(key = "msg") { Text(m, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary) } }

            item(key = "drive-h") { SectionHeader("Google Drive") }
            item(key = "drive") {
                ForgeCard {
                    val uri = prefs.driveBackupUri
                    if (uri == null) {
                        Text(
                            "Choose a file in Google Drive and Forge saves a full backup to it every night, " +
                                "plus whenever you tap Back up now. Nothing to set up on Google's side.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Button(
                            onClick = { driveFile.launch("forge-backup.json") },
                            modifier = Modifier.fillMaxWidth().heightIn(min = Sizes.touch).padding(top = Spacing.sm),
                        ) { Text("Create a Drive backup file") }
                        OutlinedButton(
                            onClick = { existingDriveFile.launch(arrayOf("application/json", "application/octet-stream", "*/*")) },
                            modifier = Modifier.fillMaxWidth().heightIn(min = Sizes.touch),
                        ) { Text("Use my existing backup file") }
                        Text(
                            "In the picker, tap ☰ and choose Google Drive. Already have a Forge backup there (new phone, " +
                                "reset)? Use it: Forge offers to restore it before backing up over it.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.CloudDone, null, tint = MaterialTheme.colorScheme.primary)
                            Text("  Backing up nightly", style = MaterialTheme.typography.titleMedium)
                        }
                        Text(
                            prefs.lastDriveBackupAt?.let { "Last backup: ${whenFormat.format(Instant.ofEpochMilli(it))}" } ?: "Not backed up yet",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        prefs.lastDriveBackupError?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.padding(top = Spacing.sm)) {
                            FilledTonalButton(onClick = vm::backUpNow, enabled = ui.busy == null) { Text("Back up now") }
                            OutlinedButton(onClick = vm::openDriveBackup, enabled = ui.busy == null) { Text("Restore") }
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            TextButton(onClick = { driveFile.launch("forge-backup.json") }) { Text("Change file") }
                            TextButton(onClick = { confirmStop = true }) { Text("Turn off") }
                        }
                    }
                }
            }

            item(key = "export-h") { SectionHeader("Export") }
            item(key = "export") {
                ForgeCard {
                    Text("A full backup you can keep anywhere, or spreadsheets of your data.", style = MaterialTheme.typography.bodyMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.padding(top = Spacing.sm)) {
                        FilledTonalButton(onClick = { exportJson.launch("forge-backup-$stamp.json") }, modifier = Modifier.weight(1f)) { Text("Backup (JSON)") }
                        OutlinedButton(onClick = { exportCsv.launch("forge-csv-$stamp.zip") }, modifier = Modifier.weight(1f)) { Text("Spreadsheets") }
                    }
                    Text(
                        "Progress photos stay on the phone; backups include everything else.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = Spacing.xs),
                    )
                }
            }

            item(key = "restore-h") { SectionHeader("Restore") }
            item(key = "restore") {
                ForgeCard {
                    Text(
                        "Restoring merges: anything newer in the backup is added or updated, and nothing on the phone " +
                            "is deleted. A snapshot is saved first, so you can always go back.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    OutlinedButton(
                        onClick = { openFile.launch(arrayOf("application/json", "application/octet-stream", "*/*")) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = Sizes.touch).padding(top = Spacing.sm),
                    ) { Text("Restore from a file") }
                }
            }

            item(key = "snap-h") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionHeader("Snapshots on this phone", Modifier.weight(1f))
                    TextButton(onClick = vm::snapshotNow, enabled = ui.busy == null) { Text("Save now") }
                }
            }
            if (ui.snapshots.isEmpty()) {
                item(key = "snap-empty") {
                    Text("Forge saves one every night (the last 14 are kept).", style = MaterialTheme.typography.bodySmall)
                }
            }
            items(ui.snapshots, key = { "s-" + it.name }) { s ->
                ForgeCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(whenFormat.format(Instant.ofEpochMilli(s.takenAt)), style = MaterialTheme.typography.bodyLarge)
                            Text(
                                s.reason.replace('-', ' ') + " · ${s.sizeKb} KB",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        TextButton(onClick = { vm.openSnapshot(s) }, enabled = ui.busy == null) { Text("Restore") }
                    }
                }
            }
        }
    }

    ui.pending?.let { p ->
        var includeSettings by remember(p) { mutableStateOf(true) }
        AlertDialog(
            onDismissRequest = vm::cancelRestore,
            title = { Text(if (p.exact) "Go back to this snapshot?" else "Restore this backup?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Text("From ${p.from}, made ${whenFormat.format(Instant.ofEpochMilli(p.preview.exportedAt))}.")
                    Text("${p.preview.workouts} workouts, ${p.preview.foodEntries} food entries, ${p.preview.total} records in all.")
                    Text(
                        if (p.exact) {
                            "Your data goes back to exactly how it was then: anything added or changed since is undone " +
                                "(progress photos stay). A snapshot of now is saved first."
                        } else {
                            "Newer records are added or updated; nothing on this phone is deleted. A snapshot is saved first."
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .heightIn(min = Sizes.touch)
                            .toggleable(value = includeSettings, role = Role.Checkbox, onValueChange = { includeSettings = it }),
                    ) {
                        Checkbox(checked = includeSettings, onCheckedChange = null)
                        Text("Also restore settings (units, equipment, body details)")
                    }
                }
            },
            confirmButton = { TextButton(onClick = { vm.confirmRestore(includeSettings) }) { Text("Restore") } },
            dismissButton = { TextButton(onClick = vm::cancelRestore) { Text("Cancel") } },
        )
    }
    ui.report?.let { r ->
        AlertDialog(
            onDismissRequest = vm::dismissReport,
            title = { Text("Restored") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Text(r.summary)
                    r.lines.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
                    if (r.skipped > 0) {
                        Text(
                            "Skipped ones refer to exercises or photos this phone doesn't have.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    r.snapshotName?.let { Text("To undo, restore the snapshot \"$it\".", style = MaterialTheme.typography.bodySmall) }
                }
            },
            confirmButton = { TextButton(onClick = vm::dismissReport) { Text("OK") } },
        )
    }
}

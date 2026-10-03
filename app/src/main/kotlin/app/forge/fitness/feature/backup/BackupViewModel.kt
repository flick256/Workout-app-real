package app.forge.fitness.feature.backup

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.forge.fitness.data.backup.BackupPreview
import app.forge.fitness.data.backup.BackupRestorer
import app.forge.fitness.data.backup.BackupScheduler
import app.forge.fitness.data.backup.CsvExporter
import app.forge.fitness.data.backup.ForgeExport
import app.forge.fitness.data.backup.JsonExporter
import app.forge.fitness.data.backup.RestoreReport
import app.forge.fitness.data.backup.Snapshot
import app.forge.fitness.data.backup.SnapshotStore
import app.forge.fitness.data.prefs.UserPreferences
import app.forge.fitness.data.prefs.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A backup read and waiting for you to confirm the restore. */
data class PendingRestore(val export: ForgeExport, val preview: BackupPreview, val from: String)

data class BackupUiState(
    val busy: String? = null,
    val message: String? = null,
    val pending: PendingRestore? = null,
    val report: RestoreReport? = null,
    val snapshots: List<Snapshot> = emptyList(),
)

@HiltViewModel
class BackupViewModel @Inject constructor(
    private val exporter: JsonExporter,
    private val csv: CsvExporter,
    private val restorer: BackupRestorer,
    private val snapshots: SnapshotStore,
    private val scheduler: BackupScheduler,
    preferences: UserPreferencesRepository,
) : ViewModel() {
    private val _ui = MutableStateFlow(BackupUiState())
    val ui: StateFlow<BackupUiState> = _ui.asStateFlow()

    val prefs: StateFlow<UserPreferences> = preferences.preferences
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserPreferences())

    init {
        refreshSnapshots()
    }

    private fun refreshSnapshots() {
        viewModelScope.launch { _ui.update { it.copy(snapshots = withContext(Dispatchers.IO) { snapshots.list() }) } }
    }

    private fun work(label: String, block: suspend () -> String) {
        if (_ui.value.busy != null) return
        _ui.update { it.copy(busy = label, message = null) }
        viewModelScope.launch {
            val message = runCatching { block() }.getOrElse { it.message ?: "Something went wrong" }
            _ui.update { it.copy(busy = null, message = message) }
            refreshSnapshots()
        }
    }

    fun exportJson(uri: Uri) = work("Exporting…") {
        val r = exporter.exportTo(uri)
        "Exported ${r.workouts} workouts and ${r.sets} sets (${r.bytes / 1024} KB)."
    }

    fun exportCsv(uri: Uri) = work("Exporting CSV…") { "Exported ${csv.exportTo(uri)} rows in 8 CSV files." }

    fun snapshotNow() = work("Saving a snapshot…") { "Saved ${snapshots.save("manual").name}" }

    fun chooseDriveFile(uri: Uri) = work("Setting up Drive backup…") {
        scheduler.setDriveFile(uri)
        scheduler.run() ?: "Backed up to your Drive file. Forge will update it every night."
    }

    fun stopDrive() = work("…") {
        scheduler.setDriveFile(null)
        "Drive backup turned off. The file stays in your Drive."
    }

    fun backUpNow() = work("Backing up…") { scheduler.run() ?: "Backed up to the phone and to Drive." }

    // ---- Restore ------------------------------------------------------------------------

    fun openBackup(uri: Uri) = load("that file") { restorer.read(uri) }

    fun openDriveBackup() {
        val uri = prefs.value.driveBackupUri ?: return
        load("your Drive backup") { restorer.read(Uri.parse(uri)) }
    }

    fun openSnapshot(snapshot: Snapshot) = load(snapshot.name) { restorer.decode(withContext(Dispatchers.IO) { snapshots.read(snapshot) }) }

    private fun load(from: String, read: suspend () -> ForgeExport) {
        _ui.update { it.copy(busy = "Reading…", message = null) }
        viewModelScope.launch {
            runCatching { read() }
                .onSuccess { export -> _ui.update { it.copy(busy = null, pending = PendingRestore(export, restorer.preview(export), from)) } }
                .onFailure { e -> _ui.update { it.copy(busy = null, message = e.message) } }
        }
    }

    fun cancelRestore() = _ui.update { it.copy(pending = null) }

    fun confirmRestore(includeSettings: Boolean) {
        val pending = _ui.value.pending ?: return
        _ui.update { it.copy(pending = null, busy = "Restoring…") }
        viewModelScope.launch {
            runCatching { restorer.restore(pending.export, includeSettings) }
                .onSuccess { r -> _ui.update { it.copy(busy = null, report = r) } }
                .onFailure { e -> _ui.update { it.copy(busy = null, message = "Restore failed, nothing was changed: ${e.message}") } }
            refreshSnapshots()
        }
    }

    fun dismissReport() = _ui.update { it.copy(report = null) }
}

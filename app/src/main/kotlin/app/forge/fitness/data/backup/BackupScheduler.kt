package app.forge.fitness.data.backup

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.forge.fitness.data.prefs.UserPreferencesRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Nightly backups: a snapshot on the phone, and (if you've picked one) a copy written to
 * your Google Drive file. Android runs it about once a day when the battery isn't low.
 */
@Singleton
class BackupScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val snapshots: SnapshotStore,
    private val exporter: JsonExporter,
    private val preferences: UserPreferencesRepository,
) {
    fun scheduleDaily() {
        val request = PeriodicWorkRequestBuilder<BackupWorker>(1, TimeUnit.DAYS)
            .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(DAILY, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    fun backUpNow() {
        WorkManager.getInstance(context).enqueueUniqueWork(NOW, ExistingWorkPolicy.REPLACE, OneTimeWorkRequestBuilder<BackupWorker>().build())
    }

    /** Remembers the Drive file you picked (and keeps permission to write it after a restart). */
    suspend fun setDriveFile(uri: Uri?) {
        val resolver = context.contentResolver
        preferences.preferences.first().driveBackupUri?.let { old ->
            if (old != uri?.toString()) runCatching {
                resolver.releasePersistableUriPermission(Uri.parse(old), Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            }
        }
        if (uri != null) {
            resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }
        preferences.setDriveBackup(uri?.toString(), null, null)
    }

    /**
     * One backup run. Returns an error message, or null when everything worked. The phone
     * snapshot is skipped if one was taken in the last 20 hours (retries don't pile them up).
     */
    suspend fun run(): String? = withContext(Dispatchers.IO) {
        val recent = snapshots.latestAt()?.let { System.currentTimeMillis() - it < SNAPSHOT_GAP_MS } == true
        val snapshotError = if (recent) null else runCatching { snapshots.save("daily") }.exceptionOrNull()
        val prefs = preferences.preferences.first()
        val drive = prefs.driveBackupUri ?: return@withContext snapshotError?.let { "Phone backup failed: ${it.message}" }
        val result = runCatching { exporter.writeTo(Uri.parse(drive), exporter.encode(exporter.build())) }
        val error = result.exceptionOrNull()?.let { "Couldn't write to your Drive file (${it.message}). Pick the file again in Backup." }
        preferences.recordDriveResult(drive, if (error == null) System.currentTimeMillis() else prefs.lastDriveBackupAt, error)
        error
    }

    private companion object {
        const val DAILY = "daily-backup"
        const val NOW = "backup-now"
        const val SNAPSHOT_GAP_MS = 20 * 60 * 60 * 1000L
    }
}

class BackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Deps {
        fun backupScheduler(): BackupScheduler
    }

    override suspend fun doWork(): Result {
        val scheduler = EntryPointAccessors.fromApplication(applicationContext, Deps::class.java).backupScheduler()
        return if (scheduler.run() == null) Result.success() else if (runAttemptCount < 2) Result.retry() else Result.failure()
    }
}

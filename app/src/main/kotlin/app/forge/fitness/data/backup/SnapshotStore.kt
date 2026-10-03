package app.forge.fitness.data.backup

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class Snapshot(val file: File, val takenAt: Long, val reason: String, val sizeKb: Long = file.length() / 1024) {
    val name: String get() = file.name
}

/**
 * Full backups kept on the phone (in Forge's private storage): one a day automatically,
 * plus one before every restore. The newest [KEEP] are kept.
 */
@Singleton
class SnapshotStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val exporter: JsonExporter,
) {
    private val dir: File get() = File(context.filesDir, "snapshots").apply { mkdirs() }
    private val stamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneId.systemDefault())

    fun list(): List<Snapshot> = (dir.listFiles { f -> f.name.endsWith(".json") } ?: emptyArray())
        .map { f -> Snapshot(f, f.lastModified(), f.name.substringAfter("forge-").substringAfter('-').substringAfter('-').removeSuffix(".json")) }
        .sortedByDescending { it.takenAt }

    suspend fun save(reason: String = "daily"): Snapshot = withContext(Dispatchers.IO) {
        val bytes = exporter.encode(exporter.build())
        val now = System.currentTimeMillis()
        val file = File(dir, "forge-${stamp.format(Instant.ofEpochMilli(now))}-$reason.json")
        val tmp = File(dir, file.name + ".tmp")
        tmp.writeBytes(bytes)
        tmp.renameTo(file)
        list().drop(KEEP).forEach { it.file.delete() }
        Snapshot(file, now, reason)
    }

    fun read(snapshot: Snapshot): String = snapshot.file.readText()

    fun latestAt(): Long? = list().firstOrNull()?.takenAt

    companion object {
        const val KEEP = 14
    }
}

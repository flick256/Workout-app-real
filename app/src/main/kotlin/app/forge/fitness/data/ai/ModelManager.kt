package app.forge.fitness.data.ai

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import app.forge.fitness.di.ApplicationScope
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

sealed interface ModelState {
    data object NotInstalled : ModelState
    /** [progress] 0..1, or null while the size isn't known yet. */
    data class Downloading(val progress: Float?, val downloadedMb: Long) : ModelState
    data class Importing(val progress: Float?) : ModelState
    data class Installed(val sizeMb: Long) : ModelState
    data class Failed(val message: String) : ModelState
}

/**
 * The optional on-device AI model (Gemma 4 E2B, Apache-2.0, ~2.6 GB). It's stored in
 * Forge's own storage on the phone, is never uploaded anywhere, and deleting it (or
 * uninstalling Forge) removes it.
 */
@Singleton
class ModelManager @Inject constructor(
    @ApplicationContext private val context: Context,
    @param:ApplicationScope private val scope: CoroutineScope,
) {
    private val downloads = context.getSystemService(DownloadManager::class.java)
    private val prefs = context.getSharedPreferences("ai_model", Context.MODE_PRIVATE)

    /** App-specific storage: no permission needed, removed with the app. */
    private val dir: File = (context.getExternalFilesDir(DIR) ?: File(context.filesDir, DIR)).apply { mkdirs() }
    val modelFile: File get() = File(dir, FILE_NAME)
    private val partFile: File get() = File(dir, "$FILE_NAME.part")

    private val _state = MutableStateFlow(initialState())
    val state: StateFlow<ModelState> = _state.asStateFlow()
    private var watcher: Job? = null

    val isInstalled: Boolean get() = modelFile.length() > MIN_BYTES

    init {
        prefs.getLong(KEY_DOWNLOAD, -1L).takeIf { it >= 0 }?.let(::watch)
    }

    private fun initialState(): ModelState = if (isInstalled) ModelState.Installed(modelFile.length() / MB) else ModelState.NotInstalled

    /** Downloads the model with Android's download manager (keeps going if you leave the app). */
    fun startDownload(wifiOnly: Boolean) {
        if (context.getExternalFilesDir(DIR) == null) {
            _state.value = ModelState.Failed("No storage for downloads; download it in your browser and use Import instead.")
            return
        }
        partFile.delete()
        val request = DownloadManager.Request(Uri.parse(MODEL_URL))
            .setTitle("Forge AI model")
            .setDescription("Gemma 4 E2B, about 2.6 GB")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(context, DIR, partFile.name)
            .setAllowedOverMetered(!wifiOnly)
            .setAllowedOverRoaming(false)
        val id = downloads.enqueue(request)
        prefs.edit().putLong(KEY_DOWNLOAD, id).apply()
        _state.value = ModelState.Downloading(null, 0)
        watch(id)
    }

    fun cancelDownload() {
        val id = prefs.getLong(KEY_DOWNLOAD, -1L)
        if (id >= 0) downloads.remove(id)
        prefs.edit().remove(KEY_DOWNLOAD).apply()
        watcher?.cancel()
        partFile.delete()
        _state.value = initialState()
    }

    private fun watch(id: Long) {
        watcher?.cancel()
        watcher = scope.launch(Dispatchers.IO) {
            while (isActive) {
                val query = DownloadManager.Query().setFilterById(id)
                val next: ModelState? = downloads.query(query)?.use { c ->
                    if (!c.moveToFirst()) return@use initialState()
                    val status = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                    val done = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                    val total = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                    when (status) {
                        DownloadManager.STATUS_SUCCESSFUL -> finishDownload()
                        DownloadManager.STATUS_FAILED -> {
                            val reason = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                            ModelState.Failed("Download failed (code $reason). You can download it in your browser and use Import.")
                        }
                        else -> ModelState.Downloading(if (total > 0) done.toFloat() / total else null, done / MB)
                    }
                } ?: initialState()
                _state.value = next ?: initialState()
                if (next !is ModelState.Downloading) {
                    prefs.edit().remove(KEY_DOWNLOAD).apply()
                    break
                }
                delay(1_000)
            }
        }
    }

    private fun finishDownload(): ModelState {
        if (partFile.length() < MIN_BYTES) return ModelState.Failed("The download looks incomplete. Try again.")
        modelFile.delete()
        return if (partFile.renameTo(modelFile)) ModelState.Installed(modelFile.length() / MB)
        else ModelState.Failed("Couldn't save the model file.")
    }

    /** Copies a model file you downloaded yourself (e.g. in a browser) into Forge. */
    fun importFrom(uri: Uri) {
        watcher?.cancel()
        watcher = scope.launch(Dispatchers.IO) {
            _state.value = ModelState.Importing(null)
            val result = runCatching {
                val size = context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c ->
                    if (c.moveToFirst()) c.getLong(0) else -1L
                } ?: -1L
                if (size in 0..MIN_BYTES) error("That file is too small to be the AI model.")
                partFile.delete()
                context.contentResolver.openInputStream(uri)!!.use { input ->
                    partFile.outputStream().use { output ->
                        val buffer = ByteArray(1 shl 20)
                        var copied = 0L
                        while (isActive) {
                            val n = input.read(buffer)
                            if (n < 0) break
                            output.write(buffer, 0, n)
                            copied += n
                            if (size > 0) _state.value = ModelState.Importing(copied.toFloat() / size)
                        }
                    }
                }
                finishDownload()
            }
            _state.value = result.getOrElse { ModelState.Failed(it.message ?: "Import failed") }
        }
    }

    fun delete() {
        modelFile.delete()
        partFile.delete()
        _state.value = ModelState.NotInstalled
    }

    companion object {
        const val MODEL_URL = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm"
        const val MODEL_PAGE = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm"
        const val FILE_NAME = "gemma-4-E2B-it.litertlm"
        private const val DIR = "models"
        private const val KEY_DOWNLOAD = "download_id"
        private const val MB = 1_048_576L
        private const val MIN_BYTES = 200L * MB
    }
}

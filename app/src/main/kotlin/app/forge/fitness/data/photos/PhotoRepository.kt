package app.forge.fitness.data.photos

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import app.forge.fitness.data.db.PhotoDao
import app.forge.fitness.data.db.ProgressPhotoEntity
import app.forge.fitness.di.TimeSource
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Progress photos are copied into the app's private storage (scaled to 1600 px and
 * saved as JPEG), so they don't clutter your gallery and no other app can read them.
 */
@Singleton
class PhotoRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: PhotoDao,
    private val time: TimeSource,
) {
    private val dir: File get() = File(context.filesDir, "progress_photos").apply { mkdirs() }

    fun observePhotos() = dao.observeAll()

    fun fileFor(photo: ProgressPhotoEntity): File = File(dir, photo.fileName)

    /** A temporary file the camera can write into (shared via FileProvider). */
    fun newCameraFile(): File = File(File(context.cacheDir, "camera").apply { mkdirs() }, "capture-${UUID.randomUUID()}.jpg")

    /** Imports an image (from the photo picker or the camera) and returns the new photo's id. */
    suspend fun import(uri: Uri, pose: String? = null): String = withContext(Dispatchers.IO) {
        val bitmap = decodeScaled(uri)
        val id = UUID.randomUUID().toString()
        val name = "$id.jpg"
        File(dir, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 88, it) }
        bitmap.recycle()
        val now = time.now()
        dao.insert(ProgressPhotoEntity(id, now, name, pose, null, now, now))
        id
    }

    suspend fun setPose(id: String, pose: String?) = edit(id) { it.copy(pose = pose) }

    suspend fun setNote(id: String, note: String?) = edit(id) { it.copy(note = note?.ifBlank { null }) }

    /** Hides the photo (undo-able). The file is kept so undo works. */
    suspend fun delete(id: String) = edit(id) { it.copy(deletedAt = time.now()) }

    suspend fun restore(id: String) = edit(id) { it.copy(deletedAt = null) }

    private suspend fun edit(id: String, change: (ProgressPhotoEntity) -> ProgressPhotoEntity) {
        val current = dao.get(id) ?: return
        dao.update(change(current).copy(updatedAt = time.now()))
    }

    private fun decodeScaled(uri: Uri): Bitmap {
        if (Build.VERSION.SDK_INT >= 28) {
            // ImageDecoder also applies the photo's rotation (EXIF), so portraits stay upright.
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                val longest = max(info.size.width, info.size.height)
                if (longest > MAX_SIDE) {
                    val scale = MAX_SIDE.toFloat() / longest
                    decoder.setTargetSize((info.size.width * scale).toInt(), (info.size.height * scale).toInt())
                }
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        return context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
            ?: error("Couldn't read that image")
    }

    private companion object {
        const val MAX_SIDE = 1600
    }
}

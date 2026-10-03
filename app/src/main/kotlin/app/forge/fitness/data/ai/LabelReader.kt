package app.forge.fitness.data.ai

import android.content.Context
import android.net.Uri
import app.forge.domain.parse.LabelReading
import app.forge.domain.parse.NutritionLabelParser
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Reads a photo of a nutrition panel with Google's on-device text recognition (no
 * internet, no AI model download), then [NutritionLabelParser] picks out the numbers.
 */
@Singleton
class LabelReader @Inject constructor(@ApplicationContext private val context: Context) {

    suspend fun read(uri: Uri): LabelReading? {
        val text = recognize(InputImage.fromFilePath(context, uri))
        return NutritionLabelParser.parse(rows(text))
    }

    private suspend fun recognize(image: InputImage): Text = suspendCancellableCoroutine { cont ->
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        recognizer.process(image)
            .addOnSuccessListener { if (cont.isActive) cont.resume(it) }
            .addOnFailureListener { if (cont.isActive) cont.resumeWithException(it) }
            .addOnCompleteListener { recognizer.close() }
    }

    /**
     * Tables come back as separate pieces of text; put pieces at the same height back into
     * rows, left to right, so "Protein | 4.6g | 11.6g" reads as one line.
     */
    private fun rows(text: Text): List<String> {
        val lines = text.textBlocks.flatMap { it.lines }.mapNotNull { line -> line.boundingBox?.let { line.text to it } }
        val sorted = lines.sortedBy { it.second.centerY() }
        val rows = mutableListOf<MutableList<Pair<String, android.graphics.Rect>>>()
        for (line in sorted) {
            val row = rows.lastOrNull()
            val box = line.second
            if (row != null) {
                val ref = row.first().second
                if (kotlin.math.abs(box.centerY() - ref.centerY()) < maxOf(ref.height(), box.height()) * 0.6) {
                    row += line
                    continue
                }
            }
            rows += mutableListOf(line)
        }
        return rows.map { row -> row.sortedBy { it.second.left }.joinToString(" ") { it.first } }
    }
}

package app.forge.fitness.feature.food

import android.content.Context
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

sealed interface ScanResult {
    data class Code(val value: String) : ScanResult
    data object Cancelled : ScanResult
    data class Failed(val message: String) : ScanResult
}

/**
 * Google's code scanner (part of Play services): it shows its own camera screen, so
 * Forge never needs the camera permission, and the scanning happens on the phone.
 */
object BarcodeScanner {
    suspend fun scan(context: Context): ScanResult = suspendCancellableCoroutine { cont ->
        val options = GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8, Barcode.FORMAT_UPC_A, Barcode.FORMAT_UPC_E)
            .enableAutoZoom()
            .build()
        GmsBarcodeScanning.getClient(context, options).startScan()
            .addOnSuccessListener { barcode ->
                val value = barcode.rawValue
                if (cont.isActive) cont.resume(if (value.isNullOrBlank()) ScanResult.Cancelled else ScanResult.Code(normalize(value)))
            }
            .addOnCanceledListener { if (cont.isActive) cont.resume(ScanResult.Cancelled) }
            .addOnFailureListener { e ->
                if (cont.isActive) cont.resume(ScanResult.Failed(e.message ?: "The scanner couldn't start"))
            }
    }

    /** UPC-A codes are often stored as EAN-13 with a leading 0. */
    fun normalize(raw: String): String {
        val digits = raw.trim()
        return if (digits.length == 12 && digits.all(Char::isDigit)) "0$digits" else digits
    }
}

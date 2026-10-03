package app.forge.domain.heart

import kotlin.math.roundToInt

/** One Heart Rate Measurement (Bluetooth characteristic 0x2A37) from a strap or watch. */
data class HrReading(
    val bpm: Int,
    /** Null when the strap doesn't report skin contact. */
    val contact: Boolean? = null,
    val energyKj: Int? = null,
    /** Beat-to-beat intervals in milliseconds, if the strap sends them. */
    val rrMs: List<Int> = emptyList(),
)

/**
 * Parses the standard Bluetooth Heart Rate Measurement. Byte 0 is flags:
 * bit 0 = 16-bit heart rate, bits 1–2 = skin contact, bit 3 = energy expended present,
 * bit 4 = RR intervals present (in 1/1024 s).
 */
object HeartRateParser {
    fun parse(bytes: ByteArray): HrReading? {
        if (bytes.size < 2) return null
        val flags = bytes[0].toInt() and 0xFF
        var i = 1
        val wide = flags and 0x01 != 0
        val bpm = if (wide) {
            if (bytes.size < 3) return null
            u16(bytes, i).also { i += 2 }
        } else {
            (bytes[i].toInt() and 0xFF).also { i += 1 }
        }
        val contact = when ((flags shr 1) and 0x03) {
            0x02 -> false
            0x03 -> true
            else -> null
        }
        var energy: Int? = null
        if (flags and 0x08 != 0 && i + 1 < bytes.size) {
            energy = u16(bytes, i); i += 2
        }
        val rr = mutableListOf<Int>()
        if (flags and 0x10 != 0) {
            while (i + 1 < bytes.size) {
                rr += (u16(bytes, i) * 1000.0 / 1024).roundToInt()
                i += 2
            }
        }
        if (bpm !in 20..250) return null
        return HrReading(bpm, contact, energy, rr)
    }

    private fun u16(b: ByteArray, i: Int) = (b[i].toInt() and 0xFF) or ((b[i + 1].toInt() and 0xFF) shl 8)
}

enum class HrZone(val label: String, val low: Double) {
    Z1("Warm-up", 0.50), Z2("Easy", 0.60), Z3("Aerobic", 0.70), Z4("Threshold", 0.80), Z5("Max", 0.90);

    companion object {
        /** Null below 50% of max (resting between sets counts as no zone). */
        fun of(bpm: Int, maxHr: Int): HrZone? {
            val f = bpm.toDouble() / maxHr
            return entries.lastOrNull { f >= it.low }
        }
    }
}

data class HrSample(val atMillis: Long, val bpm: Int)

data class HrSummary(
    val avg: Int,
    val max: Int,
    val min: Int,
    /** Seconds spent in each zone. */
    val zoneSeconds: Map<HrZone, Long>,
)

object HeartRateMath {
    /** Tanaka (2001): 208 − 0.7 × age; more accurate than 220 − age, especially for young people. */
    fun maxHr(age: Int?): Int = age?.let { (208 - 0.7 * it).roundToInt() } ?: 195

    /**
     * Time-weighted summary: each sample counts until the next one (capped at 30 s, so a
     * dropout doesn't stretch one reading over minutes).
     */
    fun summarize(samples: List<HrSample>, maxHr: Int): HrSummary? {
        if (samples.isEmpty()) return null
        val sorted = samples.sortedBy { it.atMillis }
        val zones = mutableMapOf<HrZone, Long>()
        var weighted = 0.0
        var totalMs = 0L
        sorted.forEachIndexed { i, s ->
            val next = sorted.getOrNull(i + 1)?.atMillis ?: (s.atMillis + 1_000)
            val ms = (next - s.atMillis).coerceIn(0, MAX_GAP_MS)
            weighted += s.bpm * ms.toDouble()
            totalMs += ms
            HrZone.of(s.bpm, maxHr)?.let { zones.merge(it, ms, Long::plus) }
        }
        val avg = if (totalMs > 0) (weighted / totalMs).roundToInt() else sorted.map { it.bpm }.average().roundToInt()
        return HrSummary(avg, sorted.maxOf { it.bpm }, sorted.minOf { it.bpm }, zones.mapValues { it.value / 1_000 })
    }

    private const val MAX_GAP_MS = 30_000L
}

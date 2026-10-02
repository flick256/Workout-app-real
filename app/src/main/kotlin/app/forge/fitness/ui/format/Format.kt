package app.forge.fitness.ui.format

import app.forge.domain.calc.Units
import app.forge.domain.model.WeightUnit

/** Display and input helpers. Storage is always kg; these convert at the edges. */
object Format {
    /** "62.5" in the user's unit, no unit suffix. */
    fun weightNumber(kg: Double, unit: WeightUnit): String = Units.format(Units.fromKg(kg, unit))

    /** "62.5 kg" / "137.8 lb". */
    fun weight(kg: Double, unit: WeightUnit): String = "${weightNumber(kg, unit)} ${unit.symbol}"

    /** Parses what was typed (accepts "62,5" too) and returns kg, or null if invalid. */
    fun parseWeight(text: String, unit: WeightUnit): Double? =
        text.trim().replace(',', '.').toDoubleOrNull()
            ?.takeIf { it >= 0 && it < 10_000 }
            ?.let { Units.toKg(it, unit) }

    /** "1:05:12" or "42:07" or "0:45". */
    fun duration(totalSeconds: Long): String {
        val s = totalSeconds.coerceAtLeast(0)
        val h = s / 3600
        val m = (s % 3600) / 60
        val sec = s % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
    }

    /** "45 min", "1 h 5 min". */
    fun durationWords(totalSeconds: Long): String {
        val minutes = (totalSeconds / 60).coerceAtLeast(0)
        return if (minutes >= 60) "${minutes / 60} h ${minutes % 60} min" else "$minutes min"
    }

    /** Volume reads better rounded: "4,320 kg". */
    fun volume(kg: Double, unit: WeightUnit): String =
        "%,d %s".format(Units.fromKg(kg, unit).toLong(), unit.symbol)

    fun rpe(rpe: Double): String = Units.format(rpe)

    /** Parses "90", "1:30" or "1:30.5" into seconds. */
    fun parseDuration(text: String): Int? {
        val parts = text.trim().split(':')
        return when (parts.size) {
            1 -> parts[0].toIntOrNull()
            2 -> {
                val m = parts[0].toIntOrNull() ?: return null
                val s = parts[1].toIntOrNull()?.takeIf { it in 0..59 } ?: return null
                m * 60 + s
            }
            else -> null
        }?.takeIf { it >= 0 }
    }
}

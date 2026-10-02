package app.forge.fitness.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

/** Named haptics so the same action always feels the same everywhere in the app. */
class Haptics(private val feedback: HapticFeedback) {
    /** A set completed, a workout saved: something good happened. */
    fun success() = feedback.performHapticFeedback(HapticFeedbackType.Confirm)

    /** A selection changed (chips, segmented buttons, steppers). */
    fun tick() = feedback.performHapticFeedback(HapticFeedbackType.SegmentTick)

    /** Something was refused or deleted. */
    fun reject() = feedback.performHapticFeedback(HapticFeedbackType.Reject)
}

@Composable
fun rememberHaptics(): Haptics {
    val feedback = LocalHapticFeedback.current
    return remember(feedback) { Haptics(feedback) }
}

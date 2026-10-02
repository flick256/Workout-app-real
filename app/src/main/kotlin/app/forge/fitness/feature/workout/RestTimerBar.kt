package app.forge.fitness.feature.workout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.forge.fitness.timer.RestState
import app.forge.fitness.ui.components.rememberHaptics
import app.forge.fitness.ui.format.Format
import app.forge.fitness.ui.theme.Sizes
import app.forge.fitness.ui.theme.Spacing
import app.forge.fitness.ui.theme.tabular
import kotlinx.coroutines.delay

/** Pinned to the bottom of the workout while you rest. */
@Composable
internal fun RestTimerBar(
    rest: RestState,
    onAdjust: (Int) -> Unit,
    onSkip: () -> Unit,
) {
    val haptics = rememberHaptics()
    // Recomputed from the end time a few times a second; nothing is "counted", so it
    // stays accurate even after the app was in the background.
    val remainingMs by produceState(rest.endAt - System.currentTimeMillis(), rest.endAt) {
        while (true) {
            value = (rest.endAt - System.currentTimeMillis()).coerceAtLeast(0)
            delay(200)
        }
    }
    val progress = (remainingMs / (rest.totalSeconds * 1000f)).coerceIn(0f, 1f)

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 3.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.navigationBarsPadding().padding(horizontal = Spacing.lg, vertical = Spacing.sm)) {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                Modifier.fillMaxWidth().padding(top = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        Format.duration((remainingMs + 999) / 1000),
                        style = MaterialTheme.typography.headlineMedium.tabular(),
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        rest.label,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                FilledTonalButton(
                    onClick = { haptics.tick(); onAdjust(-15) },
                    modifier = Modifier.heightIn(min = Sizes.touch),
                ) { Text("−15") }
                FilledTonalButton(
                    onClick = { haptics.tick(); onAdjust(15) },
                    modifier = Modifier.heightIn(min = Sizes.touch),
                ) { Text("+15") }
                TextButton(
                    onClick = { haptics.tick(); onSkip() },
                    modifier = Modifier.heightIn(min = Sizes.touch),
                ) { Text("Skip") }
            }
        }
    }
}

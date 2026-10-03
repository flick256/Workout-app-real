package app.forge.fitness.feature.heart

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.forge.domain.heart.HeartRateMath
import app.forge.domain.heart.HrSample
import app.forge.domain.heart.HrZone
import app.forge.fitness.heart.StrapState
import app.forge.fitness.ui.charts.BarRow
import app.forge.fitness.ui.charts.LineChart
import app.forge.fitness.ui.charts.TargetBars
import app.forge.fitness.ui.components.ForgeCard
import app.forge.fitness.ui.theme.Sizes
import app.forge.fitness.ui.theme.Spacing
import app.forge.fitness.ui.theme.tabular
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** The live heart-rate readout in a workout's top bar. Tap to open strap setup. */
@Composable
fun LiveHeartRate(state: StrapState, maxHr: Int, onClick: () -> Unit) {
    // A problem shows as "!" so it isn't missed mid-workout; the label says what's wrong.
    val (text, live, description) = when (state) {
        is StrapState.Live -> {
            val zone = HrZone.of(state.bpm, maxHr)
            Triple("${state.bpm}", true, "Heart rate ${state.bpm} beats per minute" + (zone?.let { ", ${it.label} zone" } ?: ""))
        }
        is StrapState.Connecting, is StrapState.Reconnecting -> Triple("…", false, "Connecting to heart-rate strap")
        StrapState.BluetoothOff -> Triple("!", false, "Heart rate paused: Bluetooth is off")
        StrapState.NoPermission -> Triple("!", false, "Heart rate paused: allow Nearby devices")
        is StrapState.Failed -> Triple("!", false, "Heart rate problem: ${state.message}")
        StrapState.Off -> return
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .heightIn(min = Sizes.touch)
            .clickable(role = Role.Button, onClickLabel = "Heart-rate strap settings", onClick = onClick)
            .padding(horizontal = Spacing.sm)
            .semantics(mergeDescendants = true) { contentDescription = description },
    ) {
        Icon(
            if (live) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
            null,
            tint = if (text == "!") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.size(20.dp),
        )
        Text(" $text", style = MaterialTheme.typography.titleMedium.tabular())
    }
}

private val clock = DateTimeFormatter.ofPattern("h:mm a").withZone(ZoneId.systemDefault())

/** Heart rate over a finished workout: chart, average/peak and time in each zone. */
@Composable
fun HeartRateCard(samples: List<HrSample>, maxHr: Int) {
    val summary = HeartRateMath.summarize(samples, maxHr) ?: return
    ForgeCard {
        Text("Heart rate", style = MaterialTheme.typography.titleMedium)
        Text(
            "Avg ${summary.avg} · peak ${summary.max} · low ${summary.min} bpm",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LineChart(
            points = samples.map { it.atMillis to it.bpm.toDouble() },
            formatValue = { "${it.toInt()} bpm" },
            formatTime = { clock.format(Instant.ofEpochMilli(it)) },
            description = "Heart rate during the workout",
            modifier = Modifier.padding(top = Spacing.sm),
        )
        val total = summary.zoneSeconds.values.sum().coerceAtLeast(1)
        TargetBars(
            HrZone.entries.reversed().map { z ->
                val secs = summary.zoneSeconds[z] ?: 0
                BarRow("${z.name} ${z.label}", secs.toDouble(), total.toDouble(), "${secs / 60} min")
            },
            modifier = Modifier.padding(top = Spacing.sm),
        )
        Text(
            "Zones use an estimated max of $maxHr bpm (208 − 0.7 × age). Lifting is mostly short bursts, so lots of time in the lower zones is normal.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Spacing.xs),
        )
    }
}

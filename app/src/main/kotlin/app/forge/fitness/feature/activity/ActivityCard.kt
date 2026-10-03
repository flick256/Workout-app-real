package app.forge.fitness.feature.activity

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsRun
import androidx.compose.material.icons.rounded.SelfImprovement
import androidx.compose.material.icons.rounded.SportsSoccer
import androidx.compose.material.icons.rounded.Watch
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import app.forge.domain.activity.ActivityKind
import app.forge.domain.activity.Sport
import app.forge.domain.calc.Units
import app.forge.fitness.data.db.ActivitySessionEntity
import app.forge.fitness.feature.history.Stat
import app.forge.fitness.ui.format.Format
import app.forge.fitness.ui.theme.Spacing
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

val ActivityKind.icon: ImageVector
    get() = when (this) {
        ActivityKind.SPORT -> Icons.Rounded.SportsSoccer
        ActivityKind.CARDIO -> Icons.AutoMirrored.Rounded.DirectionsRun
        ActivityKind.MOBILITY -> Icons.Rounded.SelfImprovement
    }

private val dateFormat = DateTimeFormatter.ofPattern("EEE d MMM · h:mm a")

/** A sport/cardio session in History. */
@Composable
fun ActivityCard(activity: ActivitySessionEntity, onClick: () -> Unit) {
    val sport = Sport.fromKey(activity.sport)
    Card(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.fillMaxWidth().padding(Spacing.lg)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(sport.kind.icon, null, tint = MaterialTheme.colorScheme.secondary)
                Spacer(Modifier.width(Spacing.sm))
                Text(activity.title ?: sport.label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (activity.externalId != null) {
                    Icon(
                        Icons.Rounded.Watch,
                        contentDescription = "From your watch/strap",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = Spacing.xs),
                    )
                }
            }
            Text(
                (if (activity.title != null) "${sport.label} · " else "") +
                    Instant.ofEpochMilli(activity.startedAt).atZone(ZoneId.systemDefault()).format(dateFormat),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Spacing.sm))
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.lg)) {
                Stat("Time", Format.durationWords(activity.durationMinutes * 60L))
                Stat("Effort", "${activity.intensity}/10")
                activity.distanceMeters?.let { Stat("Distance", "${Units.format(it / 1000.0)} km") }
                activity.avgHeartRate?.let { Stat("Avg HR", "$it") }
            }
            activity.notes?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

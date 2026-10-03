package app.forge.fitness.feature.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.forge.domain.suggest.DeloadHint
import app.forge.domain.suggest.MuscleStatus
import app.forge.domain.suggest.QuickPlan
import app.forge.domain.suggest.RoutineChoice
import app.forge.domain.suggest.TrainToday
import app.forge.fitness.ui.components.ForgeCard
import app.forge.fitness.ui.theme.Spacing
import kotlin.math.roundToInt

/**
 * "What should I train?": pick how long you've got, see which muscles are ready,
 * and start the best routine or a generated quick workout.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TrainTodayCard(
    minutes: Int,
    muscles: List<MuscleStatus>,
    bestRoutine: RoutineChoice?,
    quickPlan: QuickPlan?,
    readinessLow: Boolean,
    onMinutes: (Int) -> Unit,
    onStartRoutine: (String) -> Unit,
    onStartQuick: (QuickPlan) -> Unit,
) {
    var showRecovery by remember { mutableStateOf(false) }
    ForgeCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("What should I train?", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            Icon(Icons.Rounded.AutoAwesome, null, tint = MaterialTheme.colorScheme.secondary)
        }
        Text("Time I've got:", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = Spacing.sm))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            listOf(15, 30, 45, 60).forEach { m ->
                FilterChip(selected = minutes == m, onClick = { onMinutes(m) }, label = { Text("$m min") })
            }
        }

        val trainable = muscles.filter { it.muscle in TrainToday.TRAINABLE }
        val recovering = trainable.filter { it.readiness < 0.6 }.sortedBy { it.readiness }
        val ready = trainable.filter { it.readiness >= 0.9 && it.deficit > 0.3 }.sortedByDescending { it.priority }
        Spacer(Modifier.height(Spacing.sm))
        if (ready.isNotEmpty()) {
            Text(
                "Ready & under-trained: " + ready.take(4).joinToString { it.muscle.label },
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        if (recovering.isNotEmpty()) {
            Text(
                "Still recovering: " + recovering.take(4).joinToString { "${it.muscle.label} ${(it.readiness * 100).roundToInt()}%" },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.tertiary,
            )
        }

        if (bestRoutine != null) {
            Spacer(Modifier.height(Spacing.md))
            Text("Best fit: ${bestRoutine.option.name}", style = MaterialTheme.typography.titleMedium)
            Text(
                "~${bestRoutine.option.minutes} min. ${bestRoutine.reason}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FilledTonalButton(onClick = { onStartRoutine(bestRoutine.option.id) }, modifier = Modifier.padding(top = Spacing.xs)) {
                Text("Start ${bestRoutine.option.name}")
            }
        }

        if (quickPlan != null) {
            Spacer(Modifier.height(Spacing.md))
            Text("Or a quick ${quickPlan.minutes}-min workout", style = MaterialTheme.typography.titleMedium)
            Text(quickPlan.reason, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (readinessLow) {
                Text(
                    "Your readiness is low today, so it's lighter: 2 sets per exercise.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
            FilledTonalButton(onClick = { onStartQuick(quickPlan) }, modifier = Modifier.padding(top = Spacing.xs)) {
                Icon(Icons.Rounded.Bolt, null)
                Text(" Build & start")
            }
        } else if (bestRoutine == null) {
            Spacer(Modifier.height(Spacing.sm))
            Text(
                "Everything's still recovering. A walk, some stretching, or a rest day is the smart call.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        TextButton(onClick = { showRecovery = !showRecovery }) {
            Text(if (showRecovery) "Hide recovery details" else "Show recovery for every muscle")
        }
        if (showRecovery) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                trainable.sortedBy { it.muscle.label }.forEach { m ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(m.muscle.label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(0.3f))
                        LinearProgressIndicator(
                            progress = { m.readiness.toFloat() },
                            modifier = Modifier.weight(0.4f).padding(horizontal = Spacing.sm),
                        )
                        Text(
                            "${m.weeklySets.roundToInt()}/${m.weeklyTarget} sets",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(0.3f),
                        )
                    }
                }
                Text(
                    "Bars show recovery: hard sets take 2–3 days to wear off. Weekly targets: ~10 sets for " +
                        "big muscles, 6–8 for small ones.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Suggests a lighter week, with the reasons and a 7-day dismiss. */
@Composable
fun DeloadCard(hint: DeloadHint, onDismiss: () -> Unit) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f)),
    ) {
        Column(Modifier.fillMaxWidth().padding(Spacing.lg)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Bedtime, null, tint = MaterialTheme.colorScheme.tertiary)
                Text("  Time for a lighter week?", style = MaterialTheme.typography.titleMedium)
            }
            hint.reasons.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 2.dp)) }
            Text(hint.advice, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = Spacing.sm))
            TextButton(onClick = onDismiss) { Text("Got it: remind me in a week") }
        }
    }
}

/** Readiness, sleep and steps from your watch/strap. Tap for the full Health screen. */
@Composable
fun HealthTodayCard(health: app.forge.fitness.feature.today.HealthToday, onClick: () -> Unit) {
    val r = health.readiness
    val tint = when (r.level) {
        app.forge.domain.activity.ReadinessLevel.LOW -> MaterialTheme.colorScheme.tertiary
        app.forge.domain.activity.ReadinessLevel.GOOD -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.secondary
    }
    Card(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.fillMaxWidth().padding(Spacing.lg)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Favorite, null, tint = tint)
                Text(
                    "  Readiness: ${r.level.label}",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
            }
            r.reasons.take(3).forEach {
                Text("• $it", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 2.dp))
            }
            if (r.level == app.forge.domain.activity.ReadinessLevel.UNKNOWN) {
                Text(
                    "No sleep or heart data for today yet. Wear your strap overnight and open Forge in the morning.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            health.day?.steps?.let {
                Text(
                    "%,d steps today".format(it),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Spacing.xs),
                )
            }
            if (r.level == app.forge.domain.activity.ReadinessLevel.LOW) {
                Text(r.advice, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = Spacing.xs))
            }
        }
    }
}

/** Calories and protein so far today; tap for the food diary. */
@Composable
fun FoodTodayCard(food: app.forge.fitness.feature.today.FoodToday, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.fillMaxWidth().padding(Spacing.lg)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Restaurant, null, tint = MaterialTheme.colorScheme.secondary)
                Text("  Food today", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text("Log food", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
            val t = food.targets
            val kcal = food.total.kcal.roundToInt()
            val protein = food.total.proteinG.roundToInt()
            if (t != null) {
                Text(
                    "%,d / %,d kcal · protein %d / %d g".format(kcal, t.kcal, protein, t.proteinG),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = Spacing.xs),
                )
                LinearProgressIndicator(
                    progress = { (food.total.kcal / t.kcal).toFloat().coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().padding(top = Spacing.xs),
                )
            } else {
                Text(
                    if (food.entries == 0) "Scan a barcode or search to log what you eat." else "%,d kcal · protein %d g".format(kcal, protein),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Spacing.xs),
                )
            }
        }
    }
}

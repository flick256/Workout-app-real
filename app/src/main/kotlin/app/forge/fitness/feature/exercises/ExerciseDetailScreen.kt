package app.forge.fitness.feature.exercises

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Unarchive
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.forge.domain.bodyweight.BodyweightProfile
import app.forge.domain.calc.Units
import app.forge.domain.model.WeightUnit
import app.forge.fitness.data.db.ExerciseEntity
import app.forge.fitness.feature.history.describe
import app.forge.fitness.ui.components.ExerciseDemo
import app.forge.fitness.ui.components.ExerciseThumb
import app.forge.fitness.ui.components.ForgeCard
import app.forge.fitness.ui.components.SectionHeader
import app.forge.fitness.ui.format.Format
import app.forge.fitness.ui.theme.Spacing
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ExerciseDetailScreen(
    onBack: () -> Unit,
    onOpenExercise: (String) -> Unit,
    onEdit: (String) -> Unit,
    vm: ExerciseDetailViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val exercise = state.exercise

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(exercise?.name.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
                actions = {
                    if (exercise?.isCustom == true) {
                        IconButton(onClick = { onEdit(exercise.id) }) { Icon(Icons.Rounded.Edit, "Edit exercise") }
                        IconButton(onClick = { vm.setArchived(!exercise.archived) }) {
                            Icon(
                                if (exercise.archived) Icons.Rounded.Unarchive else Icons.Rounded.Inventory2,
                                if (exercise.archived) "Restore exercise" else "Archive exercise",
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        if (exercise == null) return@Scaffold
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = Spacing.screen,
                end = Spacing.screen,
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + Spacing.xxl,
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            item { ExerciseDemo(exercise.images, contentDescription = "${exercise.name} demonstration") }
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    exercise.primaryMuscles.forEach { SuggestionChip(onClick = {}, label = { Text(it.label) }) }
                    exercise.equipment?.let { SuggestionChip(onClick = {}, label = { Text(it.label) }) }
                    exercise.level?.let { SuggestionChip(onClick = {}, label = { Text(it.replaceFirstChar(Char::uppercase)) }) }
                    if (exercise.archived) SuggestionChip(onClick = {}, label = { Text("Archived") })
                }
                if (exercise.secondaryMuscles.isNotEmpty()) {
                    Text(
                        "Also works: " + exercise.secondaryMuscles.joinToString { it.label.lowercase() },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            BodyweightProfile.fromKey(exercise.bodyweightProfile)?.let { profile ->
                item { BodyweightCard(profile, state, exercise) }
            }

            if (state.chain != null && state.chainSteps.size > 1) {
                item { SectionHeader(state.chain!!.label) }
                item {
                    ForgeCard {
                        state.chainSteps.forEachIndexed { i, step ->
                            val current = step.id == exercise.id
                            ListItem(
                                leadingContent = { ExerciseThumb(step.images, Spacing.xxl + Spacing.lg) },
                                headlineContent = {
                                    Text(
                                        "${i + 1}. ${step.name}",
                                        color = if (current) MaterialTheme.colorScheme.primary else Color.Unspecified,
                                    )
                                },
                                supportingContent = if (current) ({ Text("You are here") }) else null,
                                trailingContent = if (!current) ({ Icon(Icons.Rounded.ChevronRight, null) }) else null,
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                modifier = if (current) Modifier else Modifier.clickable { onOpenExercise(step.id) },
                            )
                        }
                    }
                }
            }

            if (exercise.instructions.isNotEmpty()) {
                item { SectionHeader("How to") }
                item {
                    ForgeCard {
                        exercise.instructions.forEachIndexed { i, step ->
                            Row(Modifier.padding(vertical = Spacing.xs)) {
                                Text("${i + 1}.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(Spacing.sm))
                                Text(step, style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                }
            }

            item { SectionHeader("Your history") }
            if (state.history.isEmpty()) {
                item {
                    Text(
                        "You haven't done this one yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            state.history.take(MAX_HISTORY).forEach { session ->
                item(key = session.sessionId) { HistoryEntry(session, exercise, state.unit) }
            }
        }
    }
}

@Composable
private fun BodyweightCard(profile: BodyweightProfile, state: ExerciseDetailState, exercise: ExerciseEntity) {
    ForgeCard {
        Text("Bodyweight load", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(Spacing.xs))
        val load = state.bodyweightLoad
        if (load != null) {
            Text(
                "≈ ${Format.weight(Units.roundTo(load.loadKg, 0.5), state.unit)}",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                "${(load.fraction * 100).toInt()}% of your ${Format.weight(load.bodyweightKg, state.unit)}" +
                    if (profile.unilateral) ", on one arm or leg" else "",
                style = MaterialTheme.typography.bodyMedium,
            )
        } else {
            Text(
                "About ${(profile.fraction * 100).toInt()}% of your bodyweight. Add your bodyweight in " +
                    "Settings → Body to see it in ${state.unit.symbol}.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        exercise.bodyweightElevationCm?.let {
            Text(
                "Set up for a ${Units.format(it)} cm high surface.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(Spacing.sm))
        Text(profile.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            "${profile.evidence.label} · Added weight counts ${(profile.addedFactor * 100).toInt()}%",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Spacing.xs),
        )
    }
}

private val historyDate = DateTimeFormatter.ofPattern("EEE d MMM yyyy")

@Composable
private fun HistoryEntry(session: ExerciseSession, exercise: ExerciseEntity, unit: WeightUnit) {
    ForgeCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                Instant.ofEpochMilli(session.startedAt).atZone(ZoneId.systemDefault()).format(historyDate),
                style = MaterialTheme.typography.titleSmall,
            )
            session.bestE1rmKg?.let {
                Text(
                    "e1RM ${Format.weight(Units.roundTo(it, 0.5), unit)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
        }
        session.sets.forEach { set ->
            Text(describe(set, exercise.logType, unit), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

private const val MAX_HISTORY = 10

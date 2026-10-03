package app.forge.fitness.feature.goals

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.forge.domain.goals.Achievement
import app.forge.domain.goals.AchievementStats
import app.forge.fitness.data.db.HabitEntity
import app.forge.fitness.data.goals.GoalView
import app.forge.fitness.data.goals.HabitView
import app.forge.fitness.ui.components.ForgeCard
import app.forge.fitness.ui.components.LocalSnackbarHostState
import app.forge.fitness.ui.components.SectionHeader
import app.forge.fitness.ui.components.rememberHaptics
import app.forge.fitness.ui.components.showUndo
import app.forge.fitness.ui.theme.Sizes
import app.forge.fitness.ui.theme.Spacing
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/** Habits for today, goals with progress, and achievements. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalsScreen(
    onBack: () -> Unit,
    vm: GoalsViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val exercises by vm.exercises.collectAsStateWithLifecycle()
    val snackbar = LocalSnackbarHostState.current
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    var editingHabit by remember { mutableStateOf<HabitEntity?>(null) }
    var addingHabit by remember { mutableStateOf(false) }
    var addingGoal by remember { mutableStateOf(false) }
    var openGoal by remember { mutableStateOf<GoalView?>(null) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Goals & habits") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        if (!state.loaded) return@Scaffold
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = Spacing.screen, end = Spacing.screen, top = padding.calculateTopPadding(), bottom = Spacing.xxl),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            item(key = "habits-h") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionHeader("Habits", Modifier.weight(1f))
                    TextButton(onClick = { addingHabit = true }) { Icon(Icons.Rounded.Add, null); Text(" Add") }
                }
            }
            if (state.habits.isEmpty()) {
                item(key = "habits-empty") {
                    ForgeCard {
                        Text("Build a routine one day at a time", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Add habits like \"Stretch 10 min\" or \"Creatine\". Some tick themselves: training, " +
                                "hitting protein, logging food, steps and sleep (from your strap).",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            items(state.habits, key = { "h-" + it.habit.id }) { habit ->
                HabitRow(
                    habit = habit,
                    onToggle = { haptics.tick(); vm.toggleHabit(habit.habit.id) },
                    onEdit = { editingHabit = habit.habit },
                )
            }

            item(key = "goals-h") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionHeader("Goals", Modifier.weight(1f))
                    TextButton(onClick = { addingGoal = true }) { Icon(Icons.Rounded.Add, null); Text(" Add") }
                }
            }
            if (state.goals.isEmpty()) {
                item(key = "goals-empty") {
                    ForgeCard {
                        Text("Set something to aim for", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Train 4× a week, a 100 kg squat, 20 push-ups in a row, a bodyweight, or hitting protein most days.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            items(state.goals, key = { "g-" + it.goal.id }) { goal -> GoalCard(goal) { openGoal = goal } }

            item(key = "ach-h") { SectionHeader("Achievements") }
            item(key = "ach") { Achievements(state.stats) }
        }
    }

    if (addingHabit || editingHabit != null) {
        HabitEditor(
            habit = editingHabit,
            onDismiss = { addingHabit = false; editingHabit = null },
            onSave = { id, kind, name, target, due, reminder ->
                vm.saveHabit(id, kind, name, target, due, reminder)
                addingHabit = false; editingHabit = null
            },
            onDelete = { id ->
                vm.deleteHabit(id)
                editingHabit = null
                scope.launch { snackbar.showUndo("Habit removed") { vm.restoreHabit(id) } }
            },
        )
    }
    if (addingGoal) {
        GoalEditor(
            exercises = exercises,
            onDismiss = { addingGoal = false },
            onSave = { kind, target, exerciseId -> vm.addGoal(kind, target, exerciseId); addingGoal = false },
        )
    }
    openGoal?.let { goal ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { openGoal = null },
            title = { Text(goal.title) },
            text = { Text("${goal.detail}\n\n${goal.kind.description}.") },
            confirmButton = { TextButton(onClick = { openGoal = null }) { Text("OK") } },
            dismissButton = {
                TextButton(onClick = {
                    val id = goal.goal.id
                    openGoal = null
                    vm.deleteGoal(id)
                    scope.launch { snackbar.showUndo("Goal removed") { vm.restoreGoal(id) } }
                }) { Text("Remove goal", color = MaterialTheme.colorScheme.error) }
            },
        )
    }
}

@Composable
internal fun HabitRow(habit: HabitView, onToggle: () -> Unit, onEdit: () -> Unit) {
    ForgeCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            HabitCheck(habit, onToggle)
            Column(Modifier.weight(1f).clickable(onClick = onEdit).padding(start = Spacing.sm)) {
                Text(habit.habit.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    buildString {
                        if (habit.kind.auto) append("Ticks itself · ")
                        if (!habit.isDueToday) append("Not due today · ")
                        append(habit.completion30?.let { "${(it * 100).roundToInt()}% of the last 30 days" } ?: "New")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                WeekDots(habit.week)
            }
            if (habit.streak > 0) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Rounded.LocalFireDepartment, null, tint = MaterialTheme.colorScheme.tertiary)
                    Text("${habit.streak}", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

/** Custom habits are a tap target; auto ones show their state (and can't be ticked by hand). */
@Composable
internal fun HabitCheck(habit: HabitView, onToggle: () -> Unit) {
    val done = habit.doneToday == true
    val label = when {
        done -> "${habit.habit.name}: done today"
        habit.kind.auto -> "${habit.habit.name}: ticks itself when your data shows it"
        else -> "${habit.habit.name}: not done yet, tap to tick"
    }
    Box(
        Modifier
            .size(Sizes.touch)
            .clip(CircleShape)
            .then(if (habit.kind.auto) Modifier else Modifier.clickable(onClick = onToggle))
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            when {
                done -> Icons.Rounded.CheckCircle
                habit.kind.auto -> Icons.Rounded.Sync
                else -> Icons.Rounded.RadioButtonUnchecked
            },
            contentDescription = null,
            tint = if (done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(30.dp),
        )
    }
}

/** Last 7 days: filled = done, outlined = missed, faint = not due / no data. */
@Composable
private fun WeekDots(week: List<Boolean?>) {
    val described = week.joinToString { when (it) { true -> "done"; false -> "missed"; null -> "–" } }
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.padding(top = 4.dp).semantics(mergeDescendants = true) { contentDescription = "Last 7 days: $described" },
    ) {
        week.forEach { day ->
            val color = when (day) {
                true -> MaterialTheme.colorScheme.primary
                false -> MaterialTheme.colorScheme.outline
                null -> MaterialTheme.colorScheme.surfaceContainerHighest
            }
            Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        }
    }
}

@Composable
private fun GoalCard(goal: GoalView, onClick: () -> Unit) {
    ForgeCard(modifier = Modifier.clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(goal.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            if (goal.progress.done) Icon(Icons.Rounded.CheckCircle, "Reached", tint = MaterialTheme.colorScheme.primary)
        }
        LinearProgressIndicator(
            progress = { goal.progress.fraction.toFloat() },
            modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xs),
        )
        Text(goal.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Achievements(stats: AchievementStats) {
    val unlocked = Achievement.unlocked(stats)
    ForgeCard {
        Text("${unlocked.size} of ${Achievement.entries.size} unlocked", style = MaterialTheme.typography.titleSmall)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            modifier = Modifier.padding(top = Spacing.sm),
        ) {
            unlocked.forEach { a -> AchievementChip(a, true) }
        }
        val next = Achievement.upNext(stats)
        if (next.isNotEmpty()) {
            Text("Up next", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = Spacing.md))
            next.forEach { a ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = Spacing.xs)) {
                    Icon(Icons.Rounded.Lock, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(Spacing.sm))
                    Column(Modifier.weight(1f)) {
                        Text("${a.title} · ${a.description}", style = MaterialTheme.typography.bodyMedium)
                        LinearProgressIndicator(progress = { a.progress(stats).toFloat() }, modifier = Modifier.fillMaxWidth().padding(top = 2.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun AchievementChip(a: Achievement, unlocked: Boolean) {
    Row(
        Modifier
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = Spacing.sm, vertical = 6.dp)
            .heightIn(min = 32.dp)
            .semantics(mergeDescendants = true) { contentDescription = "${a.title}: ${a.description}" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Rounded.EmojiEvents,
            null,
            tint = if (unlocked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Text(" ${a.title}", style = MaterialTheme.typography.labelLarge)
    }
}

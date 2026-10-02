package app.forge.fitness.feature.workout

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.forge.fitness.ui.components.BigButton
import app.forge.fitness.ui.components.ConfirmDialog
import app.forge.fitness.ui.components.EmptyState
import app.forge.fitness.ui.components.ForgeCard
import app.forge.fitness.ui.components.LocalSnackbarHostState
import app.forge.fitness.ui.components.TextInputDialog
import app.forge.fitness.ui.components.rememberHaptics
import app.forge.fitness.ui.components.showUndo
import app.forge.fitness.ui.format.Format
import app.forge.fitness.ui.theme.Sizes
import app.forge.fitness.ui.theme.Spacing
import app.forge.fitness.ui.theme.tabular
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private sealed interface WorkoutDialog {
    data object Rename : WorkoutDialog
    data object SessionNotes : WorkoutDialog
    data object Finish : WorkoutDialog
    data object Discard : WorkoutDialog
    data class ExerciseNotes(val blockId: String) : WorkoutDialog
    data class Rest(val blockId: String) : WorkoutDialog
    data object Bodyweight : WorkoutDialog
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActiveWorkoutScreen(
    onBack: () -> Unit,
    onAddExercises: (sessionId: String) -> Unit,
    onFinished: (sessionId: String) -> Unit,
    onOpenExercise: (exerciseId: String) -> Unit,
    vm: ActiveWorkoutViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = LocalSnackbarHostState.current
    val haptics = rememberHaptics()
    var dialog by remember { mutableStateOf<WorkoutDialog?>(null) }
    var menuOpen by rememberSaveable { mutableStateOf(false) }

    BackHandler(onBack = onBack)

    LaunchedEffect(vm) {
        vm.events.collect { event ->
            when (event) {
                is WorkoutEvent.Finished -> onFinished(event.sessionId)
                WorkoutEvent.Discarded -> onBack()
                // Snackbars run in their own coroutine so they never hold up navigation.
                is WorkoutEvent.Message -> {
                    haptics.reject()
                    launch { snackbar.showSnackbar(event.text) }
                }
                is WorkoutEvent.SetDeleted -> {
                    haptics.reject()
                    launch { snackbar.showUndo("Set deleted") { vm.restoreSet(event.setId) } }
                }
                is WorkoutEvent.ExerciseRemoved -> {
                    haptics.reject()
                    launch { snackbar.showUndo("${event.name} removed") { vm.restoreExercise(event.sessionExerciseId) } }
                }
            }
        }
    }

    val session = state.session
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back (workout keeps running)")
                    }
                },
                title = {
                    if (session != null) {
                        Column(Modifier.padding(end = Spacing.sm)) {
                            TextButton(
                                onClick = { dialog = WorkoutDialog.Rename },
                                contentPadding = PaddingValues(0.dp),
                            ) {
                                Text(session.name, style = MaterialTheme.typography.titleLarge, maxLines = 1)
                            }
                            ElapsedTime(session.startedAt)
                        }
                    }
                },
                actions = {
                    if (session != null) {
                        Button(
                            onClick = { dialog = WorkoutDialog.Finish },
                            modifier = Modifier.heightIn(min = Sizes.touch),
                        ) { Text("Finish") }
                        Box {
                            IconButton(onClick = { menuOpen = true }) {
                                Icon(Icons.Rounded.MoreVert, contentDescription = "Workout options")
                            }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                DropdownMenuItem(
                                    text = { Text("Rename") },
                                    onClick = { menuOpen = false; dialog = WorkoutDialog.Rename },
                                )
                                DropdownMenuItem(
                                    text = { Text("Bodyweight today") },
                                    onClick = { menuOpen = false; dialog = WorkoutDialog.Bodyweight },
                                )
                                DropdownMenuItem(
                                    text = { Text("Workout notes") },
                                    onClick = { menuOpen = false; dialog = WorkoutDialog.SessionNotes },
                                )
                                DropdownMenuItem(
                                    text = { Text("Discard workout", color = MaterialTheme.colorScheme.error) },
                                    onClick = { menuOpen = false; dialog = WorkoutDialog.Discard },
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        bottomBar = {
            state.rest?.let { rest ->
                RestTimerBar(rest, onAdjust = vm::adjustRest, onSkip = vm::skipRest)
            }
        },
    ) { padding ->
        when {
            state.loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            session == null -> Box(Modifier.fillMaxSize().padding(padding)) {
                EmptyState(
                    icon = Icons.Rounded.FitnessCenter,
                    title = "No workout in progress",
                    body = "Start one from the Today tab.",
                    action = { TextButton(onClick = onBack) { Text("Go back") } },
                )
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().imePadding(),
                contentPadding = PaddingValues(
                    start = Spacing.screen,
                    end = Spacing.screen,
                    top = padding.calculateTopPadding() + Spacing.sm,
                    bottom = padding.calculateBottomPadding() + Spacing.xxl,
                ),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                session.notes?.let { notes ->
                    item(key = "notes") {
                        TextButton(onClick = { dialog = WorkoutDialog.SessionNotes }) {
                            Text(notes, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                if (state.needsBodyweight) {
                    item(key = "bodyweight") {
                        ForgeCard {
                            Text("How much do you weigh today?", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Forge uses it to work out how much you actually lift in push-ups, " +
                                    "pull-ups, squats and other bodyweight moves.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            TextButton(onClick = { dialog = WorkoutDialog.Bodyweight }) { Text("Add bodyweight") }
                        }
                    }
                }
                if (state.blocks.isEmpty()) {
                    item(key = "empty") {
                        EmptyState(
                            icon = Icons.Rounded.FitnessCenter,
                            title = "Add your first exercise",
                            body = "Sets save as you go, so nothing is lost if your phone dies mid-workout.",
                        )
                    }
                }
                items(state.blocks, key = { it.item.id }) { block ->
                    val index = state.blocks.indexOf(block)
                    val next = state.blocks.getOrNull(index + 1)
                    ExerciseCard(
                        block = block,
                        unit = state.unit,
                        vm = vm,
                        onInfo = { onOpenExercise(block.exercise.id) },
                        actions = ExerciseActions(
                            onAddWarmups = { vm.addWarmups(block) },
                            onNotes = { dialog = WorkoutDialog.ExerciseNotes(block.item.id) },
                            onRest = { dialog = WorkoutDialog.Rest(block.item.id) },
                            onEasier = if (block.exercise.progressionChain != null) ({ vm.swapVariation(block, -1) }) else null,
                            onHarder = if (block.exercise.progressionChain != null) ({ vm.swapVariation(block, +1) }) else null,
                            onSupersetNext = if (next != null && (block.item.supersetGroup == null ||
                                    next.item.supersetGroup != block.item.supersetGroup)
                            ) {
                                { vm.supersetWithNext(block) }
                            } else null,
                            onLeaveSuperset = if (block.item.supersetGroup != null) ({ vm.leaveSuperset(block) }) else null,
                            onMoveUp = if (index > 0) ({ vm.move(block, -1) }) else null,
                            onMoveDown = if (next != null) ({ vm.move(block, +1) }) else null,
                            onRemove = { vm.removeExercise(block) },
                        ),
                    )
                }
                item(key = "add") {
                    BigButton(
                        text = "Add exercises",
                        icon = Icons.Rounded.Add,
                        onClick = { onAddExercises(session.id) },
                    )
                }
                item(key = "end") {
                    OutlinedButton(
                        onClick = { dialog = WorkoutDialog.Finish },
                        modifier = Modifier.fillMaxWidth().heightIn(min = Sizes.bigTouch),
                    ) { Text("Finish workout") }
                }
            }
        }
    }

    when (val d = dialog) {
        null -> Unit
        WorkoutDialog.Rename -> TextInputDialog(
            title = "Workout name",
            initial = session?.name.orEmpty(),
            onConfirm = { vm.rename(it); dialog = null },
            onDismiss = { dialog = null },
        )
        WorkoutDialog.SessionNotes -> TextInputDialog(
            title = "Workout notes",
            initial = session?.notes.orEmpty(),
            singleLine = false,
            placeholder = "How did it feel? Sleep, energy, anything worth remembering.",
            onConfirm = { vm.setNotes(it); dialog = null },
            onDismiss = { dialog = null },
        )
        WorkoutDialog.Finish -> {
            val incomplete = state.incompleteSets
            if (state.completedSets == 0) {
                ConfirmDialog(
                    title = "Nothing logged yet",
                    message = "Tick off at least one set to save this workout, or discard it.",
                    confirmLabel = "Discard workout",
                    destructive = true,
                    dismissLabel = "Keep going",
                    onConfirm = { dialog = null; vm.discard() },
                    onDismiss = { dialog = null },
                )
            } else {
                ConfirmDialog(
                    title = "Finish workout?",
                    message = if (incomplete > 0) {
                        "$incomplete set${if (incomplete == 1) "" else "s"} not ticked off will be removed."
                    } else {
                        "Nice work. This will save it to your history."
                    },
                    confirmLabel = "Finish",
                    dismissLabel = "Keep going",
                    onConfirm = { dialog = null; vm.finish() },
                    onDismiss = { dialog = null },
                )
            }
        }
        WorkoutDialog.Discard -> ConfirmDialog(
            title = "Discard workout?",
            message = "Everything logged in this workout will be thrown away.",
            confirmLabel = "Discard",
            destructive = true,
            onConfirm = { dialog = null; vm.discard() },
            onDismiss = { dialog = null },
        )
        is WorkoutDialog.ExerciseNotes -> {
            val block = state.blocks.firstOrNull { it.item.id == d.blockId }
            TextInputDialog(
                title = "Notes: ${block?.exercise?.name.orEmpty()}",
                initial = block?.item?.notes.orEmpty(),
                singleLine = false,
                placeholder = "Seat height, grip, form cues…",
                onConfirm = { text -> block?.let { vm.setExerciseNotes(it, text) }; dialog = null },
                onDismiss = { dialog = null },
            )
        }
        WorkoutDialog.Bodyweight -> TextInputDialog(
            title = "Bodyweight today",
            message = "Saved to your bodyweight log and used for this workout's bodyweight exercises.",
            initial = state.bodyweightKg?.let { Format.weightNumber(it, state.unit) }.orEmpty(),
            keyboardType = KeyboardType.Decimal,
            suffix = state.unit.symbol,
            onConfirm = { text ->
                Format.parseWeight(text, state.unit)?.takeIf { it in 20.0..400.0 }?.let(vm::setBodyweight)
                dialog = null
            },
            onDismiss = { dialog = null },
        )
        is WorkoutDialog.Rest -> {
            val block = state.blocks.firstOrNull { it.item.id == d.blockId }
            RestPickerDialog(
                current = block?.restSeconds ?: 90,
                onPick = { seconds -> block?.let { vm.setExerciseRest(it, seconds) }; dialog = null },
                onDismiss = { dialog = null },
            )
        }
    }
}

@Composable
private fun ElapsedTime(startedAt: Long) {
    val elapsed by produceState(System.currentTimeMillis() - startedAt, startedAt) {
        while (true) {
            value = System.currentTimeMillis() - startedAt
            delay(1_000)
        }
    }
    Text(
        Format.duration(elapsed / 1000),
        style = MaterialTheme.typography.labelMedium.tabular(),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

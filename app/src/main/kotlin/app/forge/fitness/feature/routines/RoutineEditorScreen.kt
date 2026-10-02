package app.forge.fitness.feature.routines

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.forge.domain.model.LogType
import app.forge.fitness.data.db.RoutineExerciseWithExercise
import app.forge.fitness.feature.workout.RestPickerDialog
import app.forge.fitness.ui.components.BigButton
import app.forge.fitness.ui.components.ExerciseThumb
import app.forge.fitness.ui.components.LocalSnackbarHostState
import app.forge.fitness.ui.components.TextInputDialog
import app.forge.fitness.ui.components.rememberHaptics
import app.forge.fitness.ui.components.showUndo
import app.forge.fitness.ui.format.Format
import app.forge.fitness.ui.theme.Sizes
import app.forge.fitness.ui.theme.Spacing
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

/** Build a routine: exercises in order (drag ☰ to reorder), each with sets, range and rest. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutineEditorScreen(
    onBack: () -> Unit,
    onAddExercises: (routineId: String) -> Unit,
    onOpenExercise: (exerciseId: String) -> Unit,
    onWorkoutStarted: () -> Unit,
    vm: RoutineEditorViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = LocalSnackbarHostState.current
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    val routine = state.routine
    var renaming by remember { mutableStateOf(false) }
    var editingNotes by remember { mutableStateOf(false) }
    var restFor by remember { mutableStateOf<RoutineExerciseWithExercise?>(null) }
    val start = rememberRoutineStarter(vm::start, onStarted = onWorkoutStarted, onResume = onWorkoutStarted)

    // Local copy so dragging is instant; saved when you let go.
    var items by remember { mutableStateOf(routine?.active.orEmpty()) }
    LaunchedEffect(routine) { items = routine?.active.orEmpty() }
    val listState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        val fromIndex = items.indexOfFirst { it.item.id == from.key }
        val toIndex = items.indexOfFirst { it.item.id == to.key }
        if (fromIndex >= 0 && toIndex >= 0) {
            items = items.toMutableList().apply { add(toIndex, removeAt(fromIndex)) }
            haptics.tick()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    TextButton(onClick = { renaming = true }, contentPadding = PaddingValues(0.dp)) {
                        Text(routine?.routine?.name.orEmpty(), style = MaterialTheme.typography.titleLarge, maxLines = 1)
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
                actions = {
                    if (routine != null && routine.active.isNotEmpty()) {
                        Button(onClick = { start(routine.routine.id) }, modifier = Modifier.padding(end = Spacing.sm)) {
                            Icon(Icons.Rounded.PlayArrow, null)
                            Text("Start")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        if (routine == null) return@Scaffold
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().imePadding(),
            contentPadding = PaddingValues(
                start = Spacing.screen,
                end = Spacing.screen,
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + Spacing.xxl,
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            item(key = "header") {
                Column {
                    Text(
                        "~${state.minutes} min · ${items.size} exercises",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                    TextButton(onClick = { editingNotes = true }, contentPadding = PaddingValues(0.dp)) {
                        Text(
                            routine.routine.notes ?: "Add notes",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            items(items, key = { it.item.id }) { entry ->
                ReorderableItem(reorderState, key = entry.item.id) { dragging ->
                    val index = items.indexOf(entry)
                    val letter = entry.item.supersetGroup?.let { g ->
                        items.mapNotNull { it.item.supersetGroup }.distinct().indexOf(g).let { ('A' + it).toString() }
                    }
                    RoutineItemCard(
                        entry = entry,
                        supersetLetter = letter,
                        dragging = dragging,
                        defaultRest = state.defaultRest,
                        handle = Modifier.draggableHandle(
                            onDragStopped = { vm.reorder(items.map { it.item.id }) },
                        ),
                        onChange = vm::update,
                        onRest = { restFor = entry },
                        onOpen = { onOpenExercise(entry.exercise.id) },
                        menu = buildList<Pair<String, () -> Unit>> {
                            val next = items.getOrNull(index + 1)
                            if (next != null && (entry.item.supersetGroup == null || next.item.supersetGroup != entry.item.supersetGroup)) {
                                add("Superset with next" to { vm.supersetWithNext(entry.item.id) })
                            }
                            if (entry.item.supersetGroup != null) add("Remove from superset" to { vm.leaveSuperset(entry.item.id) })
                            add("Exercise details" to { onOpenExercise(entry.exercise.id) })
                            add("Remove" to {
                                vm.remove(entry.item.id)
                                scope.launch { snackbar.showUndo("${entry.exercise.name} removed") { vm.restore(entry.item.id) } }
                                Unit
                            })
                        },
                    )
                }
            }
            item(key = "add") {
                BigButton(text = "Add exercises", icon = Icons.Rounded.Add, onClick = { onAddExercises(routine.routine.id) })
            }
        }
    }

    if (renaming && routine != null) {
        TextInputDialog(
            title = "Routine name",
            initial = routine.routine.name,
            onConfirm = { vm.rename(it); renaming = false },
            onDismiss = { renaming = false },
        )
    }
    if (editingNotes && routine != null) {
        TextInputDialog(
            title = "Routine notes",
            initial = routine.routine.notes.orEmpty(),
            singleLine = false,
            placeholder = "Warm-up reminders, how to progress…",
            onConfirm = { vm.setNotes(it); editingNotes = false },
            onDismiss = { editingNotes = false },
        )
    }
    restFor?.let { entry ->
        RestPickerDialog(
            current = entry.item.restSeconds ?: state.defaultRest,
            onPick = { seconds -> vm.update(entry.item.copy(restSeconds = seconds)); restFor = null },
            onDismiss = { restFor = null },
        )
    }
}

@Composable
private fun RoutineItemCard(
    entry: RoutineExerciseWithExercise,
    supersetLetter: String?,
    dragging: Boolean,
    defaultRest: Int,
    handle: Modifier,
    onChange: (app.forge.fitness.data.db.RoutineExerciseEntity) -> Unit,
    onRest: () -> Unit,
    onOpen: () -> Unit,
    menu: List<Pair<String, () -> Unit>>,
) {
    val item = entry.item
    val timed = entry.exercise.logType == LogType.DURATION || entry.exercise.logType == LogType.DISTANCE_DURATION
    val unitLabel = if (timed) "sec" else "reps"
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = if (dragging) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.surfaceContainer,
        ),
        modifier = if (supersetLetter != null) {
            Modifier.border(2.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.6f), MaterialTheme.shapes.large)
        } else Modifier,
    ) {
        Column(Modifier.padding(Spacing.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.DragHandle,
                    contentDescription = "Drag to reorder",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = handle.padding(end = Spacing.sm),
                )
                ExerciseThumb(entry.exercise.images, 40.dp)
                Column(Modifier.weight(1f).padding(start = Spacing.sm)) {
                    supersetLetter?.let {
                        Text("SUPERSET $it", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                    }
                    TextButton(onClick = onOpen, contentPadding = PaddingValues(0.dp)) {
                        Text(
                            entry.exercise.name,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                var open by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { open = true }) { Icon(Icons.Rounded.MoreVert, "Options") }
                    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                        menu.forEach { (label, action) ->
                            if (label == "Remove") HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text(label, color = if (label == "Remove") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface) },
                                onClick = { open = false; action() },
                            )
                        }
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(top = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                // Sets stepper
                IconButton(onClick = { if (item.targetSets > 1) onChange(item.copy(targetSets = item.targetSets - 1)) }) {
                    Icon(Icons.Rounded.Remove, "Fewer sets")
                }
                Text("${item.targetSets} sets", style = MaterialTheme.typography.titleSmall)
                IconButton(onClick = { if (item.targetSets < 10) onChange(item.copy(targetSets = item.targetSets + 1)) }) {
                    Icon(Icons.Rounded.Add, "More sets")
                }
                Spacer(Modifier.width(Spacing.xs))
                RangeField(item.targetMin, "min $unitLabel", Modifier.weight(1f)) { onChange(item.copy(targetMin = it)) }
                Text("–")
                RangeField(item.targetMax, "max", Modifier.weight(1f)) { onChange(item.copy(targetMax = it)) }
            }
            AssistChip(
                onClick = onRest,
                label = { Text("Rest ${Format.duration((item.restSeconds ?: defaultRest).toLong())}${if (item.restSeconds == null) " (default)" else ""}") },
                modifier = Modifier.heightIn(min = Sizes.touch),
            )
        }
    }
}

@Composable
private fun RangeField(value: Int?, label: String, modifier: Modifier, onChange: (Int?) -> Unit) {
    var text by remember { mutableStateOf(value?.toString().orEmpty()) }
    LaunchedEffect(value) { if (text.toIntOrNull() != value) text = value?.toString().orEmpty() }
    OutlinedTextField(
        value = text,
        onValueChange = { v ->
            text = v.filter(Char::isDigit).take(3)
            onChange(text.toIntOrNull())
        },
        label = { Text(label, maxLines = 1) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
    )
}

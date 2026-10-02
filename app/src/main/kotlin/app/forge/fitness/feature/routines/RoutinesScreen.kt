package app.forge.fitness.feature.routines

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.automirrored.rounded.ListAlt
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.forge.fitness.ui.components.EmptyState
import app.forge.fitness.ui.components.LocalSnackbarHostState
import app.forge.fitness.ui.components.SectionHeader
import app.forge.fitness.ui.components.TextInputDialog
import app.forge.fitness.ui.components.showUndo
import app.forge.fitness.ui.theme.Sizes
import app.forge.fitness.ui.theme.Spacing
import kotlinx.coroutines.launch

private sealed interface RoutinesDialog {
    data object New : RoutinesDialog
    data class Folder(val routineId: String, val current: String?) : RoutinesDialog
    data object Days : RoutinesDialog
}

/** All your routines (grouped by folder), the active program, and a way to add more. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutinesScreen(
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
    onBrowsePrograms: () -> Unit,
    onWorkoutStarted: () -> Unit,
    vm: RoutinesViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = LocalSnackbarHostState.current
    val scope = rememberCoroutineScope()
    var dialog by remember { mutableStateOf<RoutinesDialog?>(null) }
    val start = rememberRoutineStarter(vm::start, onStarted = onWorkoutStarted, onResume = onWorkoutStarted)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Routines & programs") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { dialog = RoutinesDialog.New },
                icon = { Icon(Icons.Rounded.Add, null) },
                text = { Text("New routine") },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = Spacing.screen,
                end = Spacing.screen,
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + Spacing.xxl * 3,
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            state.active?.let { plan ->
                item(key = "program") {
                    ProgramPlanCard(
                        plan = plan,
                        onStart = start,
                        onEditDays = { dialog = RoutinesDialog.Days },
                        onStop = { vm.stopProgram() },
                    )
                }
            }
            item(key = "browse") {
                OutlinedButton(
                    onClick = onBrowsePrograms,
                    modifier = Modifier.fillMaxWidth().heightIn(min = Sizes.bigTouch),
                ) {
                    Icon(Icons.Rounded.AutoAwesome, null)
                    Text("  Browse ready-made programs")
                }
            }
            if (!state.loading && state.routines.isEmpty()) {
                item(key = "empty") {
                    EmptyState(
                        icon = Icons.AutoMirrored.Rounded.ListAlt,
                        title = "No routines yet",
                        body = "Make your own, pick a ready-made program, or save a finished workout as a routine from its summary.",
                    )
                }
            }
            state.byFolder.forEach { (folder, routines) ->
                item(key = "f-$folder") { SectionHeader(folder) }
                items(routines, key = { it.id }) { routine ->
                    RoutineCard(
                        routine = routine,
                        onStart = { start(routine.id) },
                        onOpen = { onEdit(routine.id) },
                        menu = listOf(
                            "Edit" to { onEdit(routine.id) },
                            "Duplicate" to { vm.duplicate(routine.id) },
                            "Move to folder…" to { dialog = RoutinesDialog.Folder(routine.id, routine.data.routine.folder) },
                            "Move up" to { vm.move(routine.id, -1) },
                            "Move down" to { vm.move(routine.id, +1) },
                            "-" to {},
                            "Delete" to {
                                vm.delete(routine.id)
                                scope.launch { snackbar.showUndo("${routine.name} deleted") { vm.restore(routine.id) } }
                                Unit
                            },
                        ),
                    )
                }
            }
        }
    }

    when (val d = dialog) {
        null -> Unit
        RoutinesDialog.New -> TextInputDialog(
            title = "New routine",
            initial = "",
            placeholder = "e.g. Upper body",
            confirmLabel = "Create",
            onConfirm = { name ->
                dialog = null
                scope.launch { onEdit(vm.create(name)) }
            },
            onDismiss = { dialog = null },
        )
        is RoutinesDialog.Folder -> TextInputDialog(
            title = "Folder",
            message = "Type a folder name, or leave it empty to remove it from a folder.",
            initial = d.current.orEmpty(),
            onConfirm = { vm.setFolder(d.routineId, it); dialog = null },
            onDismiss = { dialog = null },
        )
        RoutinesDialog.Days -> state.active?.let { plan ->
            TrainingDaysDialog(
                initial = plan.days,
                onConfirm = { vm.setTrainingDays(plan.program.id, it); dialog = null },
                onDismiss = { dialog = null },
            )
        }
    }
}

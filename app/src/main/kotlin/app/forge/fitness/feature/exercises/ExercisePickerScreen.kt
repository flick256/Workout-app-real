package app.forge.fitness.feature.exercises

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.forge.fitness.ui.components.BigButton
import app.forge.fitness.ui.components.rememberHaptics
import app.forge.fitness.ui.theme.Spacing
import kotlinx.coroutines.launch

/** Pick one or more exercises to add to the current workout, in the order tapped. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExercisePickerScreen(
    sessionId: String,
    onDone: () -> Unit,
    onInfo: (String) -> Unit,
    onCreate: () -> Unit,
    vm: ExerciseListViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    // An ordered list (not a set), so exercises are added in the order you picked them.
    var selected by rememberSaveable { mutableStateOf(listOf<String>()) }
    var adding by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Add exercises") },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = { TextButton(onClick = onCreate) { Text("New") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        bottomBar = {
            if (selected.isNotEmpty()) {
                BigButton(
                    text = "Add ${selected.size} exercise${if (selected.size == 1) "" else "s"}",
                    icon = Icons.Rounded.Add,
                    enabled = !adding,
                    onClick = {
                        adding = true
                        scope.launch {
                            vm.addToSession(sessionId, selected)
                            onDone()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = Spacing.screen, vertical = Spacing.sm),
                )
            }
        },
    ) { padding ->
        ExerciseList(
            state = state,
            onQuery = vm::setQuery,
            onMuscle = vm::setMuscle,
            onMyEquipment = vm::setMyEquipmentOnly,
            onCustomOnly = vm::setCustomOnly,
            onInfo = { onInfo(it.id) },
            onClick = { e ->
                haptics.tick()
                selected = if (e.id in selected) selected - e.id else selected + e.id
            },
            selected = selected.toSet(),
            contentPadding = PaddingValues(bottom = padding.calculateBottomPadding() + Spacing.lg),
            modifier = Modifier.padding(top = padding.calculateTopPadding()),
        )
    }
}

package app.forge.fitness.feature.exercises

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.forge.fitness.ui.theme.Spacing

/** Browse the library; tap an exercise for its details, or create your own. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExercisesScreen(
    onOpen: (String) -> Unit,
    onCreate: () -> Unit,
    vm: ExerciseListViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0),
        topBar = {
            LargeTopAppBar(
                title = { Text("Exercises") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onCreate,
                icon = { Icon(Icons.Rounded.Add, null) },
                text = { Text("New exercise") },
            )
        },
    ) { padding ->
        ExerciseList(
            state = state,
            onQuery = vm::setQuery,
            onMuscle = vm::setMuscle,
            onMyEquipment = vm::setMyEquipmentOnly,
            onCustomOnly = vm::setCustomOnly,
            onClick = { onOpen(it.id) },
            contentPadding = PaddingValues(bottom = Spacing.xxl * 3),
            modifier = Modifier.padding(top = padding.calculateTopPadding()),
        )
    }
}

package app.forge.fitness.feature.exercises

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.forge.fitness.data.db.ExerciseEntity
import app.forge.fitness.ui.theme.Spacing

/** Browse the library; tap an exercise for instructions. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExercisesScreen(vm: ExerciseListViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    var info by remember { mutableStateOf<ExerciseEntity?>(null) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0),
        topBar = {
            LargeTopAppBar(
                title = { Text("Exercises") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        ExerciseList(
            state = state,
            onQuery = vm::setQuery,
            onMuscle = vm::setMuscle,
            onMyEquipment = vm::setMyEquipmentOnly,
            onClick = { info = it },
            contentPadding = PaddingValues(bottom = Spacing.xxl),
            modifier = Modifier.padding(top = padding.calculateTopPadding()),
        )
    }
    info?.let { ExerciseInfoSheet(it) { info = null } }
}

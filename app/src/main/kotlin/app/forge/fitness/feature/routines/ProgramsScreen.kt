package app.forge.fitness.feature.routines

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.forge.domain.model.Equipment
import app.forge.domain.program.ProgramTemplate
import app.forge.domain.program.ProgramTemplates
import app.forge.fitness.data.prefs.UserPreferencesRepository
import app.forge.fitness.data.routine.RoutineRepository
import app.forge.fitness.ui.components.BigButton
import app.forge.fitness.ui.components.ForgeCard
import app.forge.fitness.ui.theme.Spacing
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class ProgramsViewModel @Inject constructor(
    private val routines: RoutineRepository,
    preferences: UserPreferencesRepository,
) : ViewModel() {
    val owned: StateFlow<Set<Equipment>> = preferences.preferences.map { it.equipment + Equipment.BODY_ONLY }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), setOf(Equipment.BODY_ONLY))

    suspend fun install(template: ProgramTemplate) = routines.installTemplate(template)
}

/** Ready-made programs. Adding one copies its routines (so you can edit them) and makes it active. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgramsScreen(
    onBack: () -> Unit,
    onInstalled: () -> Unit,
    vm: ProgramsViewModel = hiltViewModel(),
) {
    val owned by vm.owned.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Programs") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
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
            items(ProgramTemplates.all, key = { it.key }) { template ->
                val missing = template.equipment - owned
                ForgeCard {
                    Text(template.name, style = MaterialTheme.typography.titleLarge)
                    Text(
                        "${template.level} · ${daysLabel(template.trainingDays)} · ~${template.minutes} min",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                    Spacer(Modifier.height(Spacing.sm))
                    Text(template.summary, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(Spacing.sm))
                    template.routines.forEach { r ->
                        Text(
                            "${r.name}: ${r.slots.size} exercises",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        "Equipment: " + template.equipment.sortedBy { it.ordinal }.joinToString { it.label },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (missing.isNotEmpty()) {
                        Text(
                            "You haven't marked ${missing.joinToString { it.label.lowercase() }} as owned: you can " +
                                "swap those exercises after adding.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                    }
                    Spacer(Modifier.height(Spacing.md))
                    BigButton(
                        text = "Add & follow this program",
                        icon = Icons.Rounded.Download,
                        onClick = {
                            scope.launch {
                                vm.install(template)
                                onInstalled()
                            }
                        },
                    )
                }
            }
        }
    }
}

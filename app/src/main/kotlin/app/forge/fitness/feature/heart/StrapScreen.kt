package app.forge.fitness.feature.heart

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.forge.fitness.data.prefs.UserPreferences
import app.forge.fitness.data.prefs.UserPreferencesRepository
import app.forge.fitness.heart.FoundStrap
import app.forge.fitness.heart.HeartRateMonitor
import app.forge.fitness.heart.HeartRateSession
import app.forge.fitness.heart.StrapState
import app.forge.fitness.ui.components.ConfirmDialog
import app.forge.fitness.ui.components.ForgeCard
import app.forge.fitness.ui.components.SectionHeader
import app.forge.fitness.ui.theme.Sizes
import app.forge.fitness.ui.theme.Spacing
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class StrapViewModel @Inject constructor(
    val monitor: HeartRateMonitor,
    private val preferences: UserPreferencesRepository,
    private val session: HeartRateSession,
) : ViewModel() {
    val prefs: StateFlow<UserPreferences> = preferences.preferences.stateIn(viewModelScope, SharingStarted.Eagerly, UserPreferences())
    val found = MutableStateFlow<List<FoundStrap>>(emptyList())
    val scanning = MutableStateFlow(false)
    val scanError = MutableStateFlow<String?>(null)
    private var scanJob: Job? = null
    private var scanTimeout: Job? = null

    fun scan() {
        scanJob?.cancel()
        scanError.value = null
        scanning.value = true
        found.value = emptyList()
        scanJob = viewModelScope.launch {
            monitor.scan().catch { scanError.value = it.message }.collect { found.value = it }
            scanning.value = false
        }
        // Scanning drains battery: stop after 20 s.
        scanTimeout?.cancel()
        scanTimeout = viewModelScope.launch {
            kotlinx.coroutines.delay(20_000)
            scanJob?.cancel(); scanning.value = false
        }
    }

    fun choose(strap: FoundStrap) {
        scanJob?.cancel(); scanning.value = false
        viewModelScope.launch { preferences.setHrDevice(strap.address, strap.name) }
        monitor.connect(strap.address, strap.name)
    }

    fun test() {
        val p = prefs.value
        p.hrDeviceAddress?.let { monitor.connect(it, p.hrDeviceName) }
    }

    fun forget() {
        monitor.disconnect()
        viewModelScope.launch { preferences.setHrDevice(null, null) }
    }

    /** Leaving this screen (not just rotating it): keep the strap only if a workout needs it. */
    override fun onCleared() {
        session.releaseIfIdle()
    }
}

/** Set up live heart rate from a Bluetooth strap (Amazfit Helio Strap or any standard one). */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun StrapScreen(onBack: () -> Unit, vm: StrapViewModel = hiltViewModel()) {
    var confirmForget by rememberSaveable { mutableStateOf(false) }
    if (confirmForget) {
        ConfirmDialog(
            title = "Forget this strap?",
            message = "Forge will stop recording heart rate in workouts until you pick a strap again.",
            confirmLabel = "Forget",
            destructive = true,
            onConfirm = { confirmForget = false; vm.forget() },
            onDismiss = { confirmForget = false },
        )
    }
    val prefs by vm.prefs.collectAsStateWithLifecycle()
    val state by vm.monitor.state.collectAsStateWithLifecycle()
    val found by vm.found.collectAsStateWithLifecycle()
    val scanning by vm.scanning.collectAsStateWithLifecycle()
    val scanError by vm.scanError.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result.values.all { it }) vm.scan()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Heart-rate strap") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = Spacing.screen, end = Spacing.screen, top = padding.calculateTopPadding(), bottom = Spacing.xxl),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            item(key = "status") {
                ForgeCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Favorite, null, tint = MaterialTheme.colorScheme.tertiary)
                        Text(
                            "  " + when (val s = state) {
                                is StrapState.Live -> "${s.bpm} bpm"
                                is StrapState.Connecting -> "Connecting…"
                                is StrapState.Reconnecting -> "Reconnecting…"
                                StrapState.BluetoothOff -> "Bluetooth is off"
                                StrapState.NoPermission -> "Needs Nearby devices permission"
                                is StrapState.Failed -> "Not connected"
                                StrapState.Off -> prefs.hrDeviceName?.let { "Set up: $it" } ?: "No strap set up"
                            },
                            style = MaterialTheme.typography.headlineSmall,
                        )
                    }
                    (state as? StrapState.Failed)?.let { Text(it.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
                    Text(
                        if (prefs.hrDeviceAddress != null) {
                            "Forge connects automatically when you start a workout and records your heart rate until you finish."
                        } else {
                            "Pick your strap below. After that, starting a workout in Forge records heart rate live; no need to start anything in Zepp."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = Spacing.xs),
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.padding(top = Spacing.sm)) {
                        Button(onClick = {
                            if (vm.monitor.hasPermission()) vm.scan() else permissions.launch(vm.monitor.permissions)
                        }) {
                            Icon(Icons.Rounded.Bluetooth, null)
                            Text(if (prefs.hrDeviceAddress == null) " Find my strap" else " Find again")
                        }
                        if (prefs.hrDeviceAddress != null) {
                            OutlinedButton(onClick = vm::test) { Text("Test") }
                            TextButton(onClick = { confirmForget = true }) { Text("Forget") }
                        }
                    }
                }
            }
            if (scanning) item(key = "scanning") { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            scanError?.let { e -> item(key = "scan-error") { Text(e, color = MaterialTheme.colorScheme.error) } }
            if (found.isNotEmpty()) item(key = "found-h") { SectionHeader("Found nearby") }
            items(found, key = { "f-" + it.address }) { strap ->
                ForgeCard(modifier = Modifier.clickable { vm.choose(strap) }) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.heightIn(min = Sizes.touch)) {
                        Column(Modifier.weight(1f)) {
                            Text(strap.name, style = MaterialTheme.typography.titleMedium)
                            Text("Signal ${strap.rssi} dBm", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text("Use this", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
            if (scanning && found.isEmpty()) {
                item(key = "nothing") {
                    Text(
                        "Looking… If nothing shows up, make sure Heart Rate Push is on (below) and the strap is on your arm.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            item(key = "setup-h") { SectionHeader("One-time setup for the Helio Strap") }
            item(key = "setup") {
                ForgeCard {
                    listOf(
                        "In Zepp: Device → Amazfit Helio Strap → Health Monitoring → turn on Heart Rate Push. (Newer firmware has it on already; update the strap's firmware in Zepp first.)",
                        "Wear the strap, then tap Find my strap here and pick it.",
                        "Set Forge's battery use to Unrestricted, so Samsung doesn't cut the connection when the screen is off.",
                        "That's it: start any workout in Forge and your heart rate shows at the top and is saved with the workout. Zepp can stay installed; it keeps its own all-day data.",
                    ).forEachIndexed { i, step ->
                        Text("${i + 1}. $step", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = Spacing.xs))
                    }
                    OutlinedButton(
                        onClick = {
                            runCatching {
                                context.startActivity(
                                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                )
                            }
                        },
                        modifier = Modifier.padding(top = Spacing.sm),
                    ) { Text("Open Forge's battery settings") }
                    Text(
                        "Note: if your heart rate only appears while a workout is running in Zepp, the strap's firmware limits " +
                            "broadcasting to workouts; update the firmware and check Heart Rate Push is on.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = Spacing.sm),
                    )
                }
            }
        }
    }
}

package app.forge.fitness.feature.health

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.forge.domain.activity.Readiness
import app.forge.domain.activity.ReadinessLevel
import app.forge.fitness.data.activity.ActivityRepository
import app.forge.fitness.data.db.DailyHealthEntity
import app.forge.fitness.data.health.HealthAvailability
import app.forge.fitness.data.health.HealthConnectManager
import app.forge.fitness.data.health.SyncState
import app.forge.fitness.data.prefs.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HealthState(
    val loaded: Boolean = false,
    val availability: HealthAvailability = HealthAvailability.AVAILABLE,
    val enabled: Boolean = false,
    val granted: Set<String> = emptySet(),
    val lastSync: Long? = null,
    val sync: SyncState = SyncState.Idle,
    val days: List<DailyHealthEntity> = emptyList(),
    val readiness: Readiness = Readiness(ReadinessLevel.UNKNOWN, emptyList()),
) {
    val today: DailyHealthEntity? get() = days.lastOrNull()?.takeIf { it.epochDay == LocalDate.now().toEpochDay() }
    val connected: Boolean get() = enabled && granted.isNotEmpty()
    val missing: Int get() = HealthConnectManager.PERMISSIONS.count { it !in granted }
}

@HiltViewModel
class HealthViewModel @Inject constructor(
    val health: HealthConnectManager,
    activities: ActivityRepository,
    private val preferences: UserPreferencesRepository,
) : ViewModel() {

    private val access = MutableStateFlow(health.availability() to emptySet<String>())

    val state: StateFlow<HealthState> = combine(
        access,
        preferences.preferences,
        health.state,
        activities.observeDaily(LocalDate.now().minusDays(HISTORY_DAYS)),
        activities.observeReadiness(),
    ) { (availability, granted), prefs, sync, days, readiness ->
        HealthState(
            loaded = true,
            availability = availability,
            enabled = prefs.healthConnectEnabled,
            granted = granted,
            lastSync = prefs.lastHealthSyncMillis,
            sync = sync,
            days = days,
            readiness = readiness,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HealthState())

    init {
        refresh()
    }

    /** Re-checks availability and permissions (e.g. after visiting Health Connect's settings). */
    fun refresh() {
        viewModelScope.launch { access.value = health.availability() to health.grantedPermissions() }
    }

    /** Called with what you allowed in Health Connect's permission screen. */
    fun onPermissionsResult(granted: Set<String>) {
        viewModelScope.launch {
            val before = access.value.second
            access.value = health.availability() to health.grantedPermissions()
            val now = access.value.second
            if (granted.isNotEmpty() || now.isNotEmpty()) {
                preferences.setHealthConnectEnabled(true)
                // Newly allowed data (e.g. workouts) gets its full 30 days, not just the last week.
                if (now != before) health.resetSyncWindow()
                health.syncInBackground()
            }
        }
    }

    fun syncNow() {
        viewModelScope.launch {
            access.value = health.availability() to health.grantedPermissions()
            health.syncInBackground()
        }
    }

    /** Stops syncing. Imported activities and data stay until you delete them. */
    fun disconnect() {
        viewModelScope.launch {
            preferences.setHealthConnectEnabled(false)
            // Reconnecting later re-reads 30 days, so nothing in between is skipped.
            health.resetSyncWindow()
        }
    }

    private companion object {
        const val HISTORY_DAYS = 29L
    }
}

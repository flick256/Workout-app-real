package app.forge.fitness.feature.activity

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import app.forge.domain.activity.Sport
import app.forge.fitness.data.activity.ActivityRepository
import app.forge.fitness.data.db.ActivitySource
import app.forge.fitness.ui.navigation.ActivityEditRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** "An hour ago, on the hour", worked out once so just after midnight it's still last night. */
private fun defaultStart(): LocalDateTime = LocalDateTime.now().minusHours(1).truncatedTo(ChronoUnit.HOURS)

data class ActivityForm(
    val loaded: Boolean = false,
    val isNew: Boolean = true,
    val sport: Sport = Sport.FOOTBALL,
    val title: String = "",
    val date: LocalDate = defaultStart().toLocalDate(),
    val startTime: LocalTime = defaultStart().toLocalTime(),
    val minutes: String = "60",
    val intensity: Int = Sport.FOOTBALL.defaultIntensity,
    val distanceKm: String = "",
    val notes: String = "",
    /** Read-only details from your watch/strap. */
    val avgHeartRate: Int? = null,
    val maxHeartRate: Int? = null,
    val calories: Double? = null,
    val fromHealthConnect: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class ActivityEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: ActivityRepository,
) : ViewModel() {

    private val editingId = savedStateHandle.toRoute<ActivityEditRoute>().activityId
    private val zone = ZoneId.systemDefault()

    private val _form = MutableStateFlow(
        defaultStart().let { ActivityForm(loaded = editingId == null, date = it.toLocalDate(), startTime = it.toLocalTime()) },
    )

    /** Set after the first save, so a double tap updates instead of adding a second copy. */
    private var savedId: String? = null
    private var saving = false
    val form: StateFlow<ActivityForm> = _form.asStateFlow()

    init {
        if (editingId != null) viewModelScope.launch {
            val a = repository.get(editingId) ?: return@launch
            val start = Instant.ofEpochMilli(a.startedAt).atZone(zone)
            _form.value = ActivityForm(
                loaded = true,
                isNew = false,
                sport = Sport.fromKey(a.sport),
                title = a.title.orEmpty(),
                date = start.toLocalDate(),
                startTime = start.toLocalTime().truncatedTo(ChronoUnit.MINUTES),
                minutes = a.durationMinutes.toString(),
                intensity = a.intensity,
                distanceKm = a.distanceMeters?.let { app.forge.domain.calc.Units.format(it / 1000.0) }.orEmpty(),
                notes = a.notes.orEmpty(),
                avgHeartRate = a.avgHeartRate,
                maxHeartRate = a.maxHeartRate,
                calories = a.calories,
                fromHealthConnect = a.source == ActivitySource.HEALTH_CONNECT.name || a.externalId != null,
            )
        }
    }

    fun update(change: (ActivityForm) -> ActivityForm) = _form.update { change(it).copy(error = null) }

    /** Picking a sport also sets a typical effort for it, until you change the effort yourself. */
    fun setSport(sport: Sport) = update {
        val keepEffort = !it.isNew || it.intensity != it.sport.defaultIntensity
        it.copy(sport = sport, intensity = if (keepEffort) it.intensity else sport.defaultIntensity)
    }

    /** Saves and returns the id, or null (with an error shown) if something's missing. */
    suspend fun save(): String? {
        if (saving) return null
        val f = _form.value
        val minutes = f.minutes.trim().toIntOrNull()
        if (minutes == null || minutes !in 1..MAX_MINUTES) {
            _form.update { it.copy(error = "Enter how long it lasted, in minutes") }
            return null
        }
        val distance = f.distanceKm.trim().replace(',', '.').takeIf { it.isNotEmpty() }?.let {
            it.toDoubleOrNull() ?: run {
                _form.update { s -> s.copy(error = "Distance should be a number of kilometres") }
                return null
            }
        }
        val start = LocalDateTime.of(f.date, f.startTime).atZone(zone).toInstant().toEpochMilli()
        saving = true
        return try {
            repository.save(
                id = editingId ?: savedId,
                sport = f.sport,
                title = f.title,
                startedAt = start,
                durationMinutes = minutes,
                intensity = f.intensity,
                distanceMeters = distance?.takeIf { it > 0 }?.let { it * 1000 },
                notes = f.notes,
            ).also { savedId = it }
        } finally {
            saving = false
        }
    }

    suspend fun delete() {
        editingId?.let { repository.delete(it) }
    }

    private companion object {
        const val MAX_MINUTES = 24 * 60
    }
}

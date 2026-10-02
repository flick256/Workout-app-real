package app.forge.fitness.feature.exercises

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import app.forge.domain.bodyweight.BodyweightProfile
import app.forge.domain.bodyweight.Elevation
import app.forge.domain.model.Equipment
import app.forge.domain.model.ExerciseCategory
import app.forge.domain.model.LogType
import app.forge.domain.model.Muscle
import app.forge.fitness.data.db.ExerciseDao
import app.forge.fitness.data.db.ExerciseEntity
import app.forge.fitness.di.TimeSource
import app.forge.fitness.ui.navigation.ExerciseEditRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The editable fields of a custom exercise. */
data class ExerciseForm(
    val loaded: Boolean = false,
    val isNew: Boolean = true,
    val name: String = "",
    val logType: LogType = LogType.WEIGHT_REPS,
    val equipment: Equipment? = null,
    val primary: Set<Muscle> = emptySet(),
    val secondary: Set<Muscle> = emptySet(),
    val bodyweightProfile: BodyweightProfile? = null,
    val elevationCm: String = "",
    val instructions: String = "",
    val error: String? = null,
) {
    val needsElevation: Boolean get() = bodyweightProfile?.elevation?.let { it != Elevation.NONE } == true
}

@HiltViewModel
class ExerciseEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val exercises: ExerciseDao,
    private val time: TimeSource,
) : ViewModel() {

    private val editingId = savedStateHandle.toRoute<ExerciseEditRoute>().exerciseId
    private var original: ExerciseEntity? = null

    private val _form = MutableStateFlow(ExerciseForm(loaded = editingId == null))
    val form: StateFlow<ExerciseForm> = _form.asStateFlow()

    init {
        if (editingId != null) viewModelScope.launch {
            val e = exercises.getById(editingId) ?: return@launch
            original = e
            _form.value = ExerciseForm(
                loaded = true,
                isNew = false,
                name = e.name,
                logType = e.logType,
                equipment = e.equipment,
                primary = e.primaryMuscles.toSet(),
                secondary = e.secondaryMuscles.toSet(),
                bodyweightProfile = BodyweightProfile.fromKey(e.bodyweightProfile),
                elevationCm = e.bodyweightElevationCm?.let { app.forge.domain.calc.Units.format(it) }.orEmpty(),
                instructions = e.instructions.joinToString("\n"),
            )
        }
    }

    fun update(change: (ExerciseForm) -> ExerciseForm) = _form.update { change(it).copy(error = null) }

    fun setBodyweightProfile(profile: BodyweightProfile?) = update {
        it.copy(
            bodyweightProfile = profile,
            // A bodyweight move is logged as reps (+ optional added weight).
            logType = if (profile != null) LogType.REPS else it.logType,
            elevationCm = if (profile?.elevation != null && profile.elevation != Elevation.NONE && it.elevationCm.isBlank()) {
                app.forge.domain.calc.Units.format(profile.defaultElevationCm)
            } else it.elevationCm,
        )
    }

    /** Saves and returns the exercise id, or null (with [ExerciseForm.error] set) if invalid. */
    suspend fun save(): String? {
        val f = _form.value
        val name = f.name.trim()
        when {
            name.isEmpty() -> return fail("Give it a name")
            f.primary.isEmpty() -> return fail("Pick at least one main muscle")
        }
        val now = time.now()
        val profile = f.bodyweightProfile.takeIf { f.logType == LogType.REPS }
        val elevation = if (f.needsElevation) f.elevationCm.replace(',', '.').toDoubleOrNull() else null
        val base = original
        val entity = ExerciseEntity(
            id = base?.id ?: UUID.randomUUID().toString(),
            name = name,
            primaryMuscles = Muscle.entries.filter { it in f.primary },
            secondaryMuscles = Muscle.entries.filter { it in f.secondary && it !in f.primary },
            equipment = f.equipment,
            category = when (f.logType) {
                LogType.DISTANCE_DURATION -> ExerciseCategory.CARDIO
                else -> base?.category ?: ExerciseCategory.STRENGTH
            },
            logType = f.logType,
            bodyweightProfile = profile?.name,
            bodyweightElevationCm = elevation,
            progressionChain = base?.progressionChain,
            progressionStep = base?.progressionStep,
            mechanic = base?.mechanic,
            force = base?.force,
            level = base?.level,
            instructions = f.instructions.lines().map { it.trim() }.filter { it.isNotEmpty() },
            images = base?.images.orEmpty(),
            isCustom = true,
            sourceId = null,
            archived = base?.archived ?: false,
            createdAt = base?.createdAt ?: now,
            updatedAt = now,
        )
        if (base == null) exercises.insert(entity) else exercises.update(entity)
        return entity.id
    }

    private fun fail(message: String): String? {
        _form.update { it.copy(error = message) }
        return null
    }
}

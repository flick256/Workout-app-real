package app.forge.fitness.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import app.forge.domain.model.Equipment
import app.forge.domain.model.ThemeMode
import app.forge.domain.model.WeightUnit
import app.forge.domain.workout.AvailableWeights
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

data class UserPreferences(
    val themeMode: ThemeMode = ThemeMode.DARK,
    val weightUnit: WeightUnit = WeightUnit.KG,
    val defaultRestSeconds: Int = 90,
    /** What you train with. Exercises and programs are filtered to this. */
    val equipment: Set<Equipment> = DEFAULT_EQUIPMENT,
    /** Specific weights you own, in kg, for equipment where [Equipment.hasWeights]. */
    val ownedWeights: Map<Equipment, List<Double>> = emptyMap(),
    /** Used to scale incline/decline push-up loads; null = not entered yet. */
    val heightCm: Double? = null,
    /** Deload hint hidden until this day (epoch day), after you dismiss it. */
    val deloadDismissedUntilEpochDay: Long? = null,
    /** Read activities, sleep and heart data from Health Connect (M6). */
    val healthConnectEnabled: Boolean = false,
    val lastHealthSyncMillis: Long? = null,
) {
    fun weightsFor(equipment: Equipment?): List<Double> =
        equipment?.let { ownedWeights[it] }.orEmpty()

    companion object {
        val DEFAULT_EQUIPMENT = setOf(Equipment.BODY_ONLY, Equipment.BANDS, Equipment.DUMBBELL)
        val REST_OPTIONS = listOf(30, 60, 90, 120, 150, 180, 240, 300)
    }
}

@Singleton
class UserPreferencesRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    private object Keys {
        val THEME = stringPreferencesKey("theme_mode")
        val UNIT = stringPreferencesKey("weight_unit")
        val REST = intPreferencesKey("default_rest_seconds")
        val EQUIPMENT = stringSetPreferencesKey("equipment")
        val OWNED_WEIGHTS = stringPreferencesKey("owned_weights_json")
        val HEIGHT_CM = doublePreferencesKey("height_cm")
        val DELOAD_DISMISSED = longPreferencesKey("deload_dismissed_until")
        val HEALTH_ENABLED = booleanPreferencesKey("health_connect_enabled")
        val HEALTH_LAST_SYNC = longPreferencesKey("health_last_sync")
    }

    private val weightsSerializer = MapSerializer(String.serializer(), ListSerializer(Double.serializer()))

    val preferences: Flow<UserPreferences> = dataStore.data.map { p ->
        val defaults = UserPreferences()
        UserPreferences(
            themeMode = p[Keys.THEME].toEnumOr(defaults.themeMode),
            weightUnit = p[Keys.UNIT].toEnumOr(defaults.weightUnit),
            defaultRestSeconds = p[Keys.REST] ?: defaults.defaultRestSeconds,
            equipment = p[Keys.EQUIPMENT]
                ?.mapNotNull { name -> Equipment.entries.firstOrNull { it.name == name } }
                ?.toSet()
                ?: defaults.equipment,
            ownedWeights = p[Keys.OWNED_WEIGHTS]?.let(::decodeWeights).orEmpty(),
            heightCm = p[Keys.HEIGHT_CM],
            deloadDismissedUntilEpochDay = p[Keys.DELOAD_DISMISSED],
            healthConnectEnabled = p[Keys.HEALTH_ENABLED] ?: false,
            lastHealthSyncMillis = p[Keys.HEALTH_LAST_SYNC],
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) = dataStore.edit { it[Keys.THEME] = mode.name }

    suspend fun setWeightUnit(unit: WeightUnit) = dataStore.edit { it[Keys.UNIT] = unit.name }

    suspend fun setDefaultRestSeconds(seconds: Int) = dataStore.edit { it[Keys.REST] = seconds }

    suspend fun setEquipment(equipment: Set<Equipment>) =
        dataStore.edit { it[Keys.EQUIPMENT] = equipment.map(Equipment::name).toSet() }

    suspend fun dismissDeloadUntil(epochDay: Long) = dataStore.edit { it[Keys.DELOAD_DISMISSED] = epochDay }

    suspend fun setHealthConnectEnabled(enabled: Boolean) = dataStore.edit { it[Keys.HEALTH_ENABLED] = enabled }

    suspend fun setLastHealthSync(millis: Long?) = dataStore.edit {
        if (millis == null) it.remove(Keys.HEALTH_LAST_SYNC) else it[Keys.HEALTH_LAST_SYNC] = millis
    }

    suspend fun setHeightCm(cm: Double?) = dataStore.edit {
        if (cm == null) it.remove(Keys.HEIGHT_CM) else it[Keys.HEIGHT_CM] = cm
    }

    /** Replaces the list of owned weights (kg) for one item. An empty list clears it. */
    suspend fun setOwnedWeights(equipment: Equipment, weightsKg: List<Double>) =
        dataStore.edit { prefs ->
            val current = prefs[Keys.OWNED_WEIGHTS]?.let(::decodeWeights).orEmpty()
            val normalized = AvailableWeights.normalize(weightsKg)
            val updated = if (normalized.isEmpty()) current - equipment else current + (equipment to normalized)
            prefs[Keys.OWNED_WEIGHTS] = Json.encodeToString(
                weightsSerializer,
                updated.mapKeys { it.key.name },
            )
        }

    /** Unknown equipment names (e.g. from a newer version) are skipped, not fatal. */
    private fun decodeWeights(json: String): Map<Equipment, List<Double>> =
        runCatching { Json.decodeFromString(weightsSerializer, json) }
            .getOrDefault(emptyMap())
            .mapNotNull { (name, weights) ->
                Equipment.entries.firstOrNull { it.name == name }?.let { it to weights }
            }
            .toMap()
}

private inline fun <reified E : Enum<E>> String?.toEnumOr(default: E): E =
    this?.let { name -> enumValues<E>().firstOrNull { it.name == name } } ?: default

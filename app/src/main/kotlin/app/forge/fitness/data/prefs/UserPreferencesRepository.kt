package app.forge.fitness.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import app.forge.domain.model.Equipment
import app.forge.domain.model.ThemeMode
import app.forge.domain.model.WeightUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class UserPreferences(
    val themeMode: ThemeMode = ThemeMode.DARK,
    val weightUnit: WeightUnit = WeightUnit.KG,
    val defaultRestSeconds: Int = 90,
    /** What you train with. Exercises and programs are filtered to this. */
    val equipment: Set<Equipment> = DEFAULT_EQUIPMENT,
) {
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
    }

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
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) = dataStore.edit { it[Keys.THEME] = mode.name }

    suspend fun setWeightUnit(unit: WeightUnit) = dataStore.edit { it[Keys.UNIT] = unit.name }

    suspend fun setDefaultRestSeconds(seconds: Int) = dataStore.edit { it[Keys.REST] = seconds }

    suspend fun setEquipment(equipment: Set<Equipment>) =
        dataStore.edit { it[Keys.EQUIPMENT] = equipment.map(Equipment::name).toSet() }
}

private inline fun <reified E : Enum<E>> String?.toEnumOr(default: E): E =
    this?.let { name -> enumValues<E>().firstOrNull { it.name == name } } ?: default

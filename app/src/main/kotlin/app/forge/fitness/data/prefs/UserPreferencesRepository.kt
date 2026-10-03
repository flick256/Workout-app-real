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
import app.forge.domain.nutrition.ActivityLevel
import app.forge.domain.nutrition.NutritionGoal
import app.forge.domain.nutrition.Sex
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
    /** For calorie targets (M7). */
    val sex: Sex? = null,
    val birthYear: Int? = null,
    val activityLevel: ActivityLevel = ActivityLevel.MODERATE,
    val nutritionGoal: NutritionGoal = NutritionGoal.MAINTAIN,
    /** Your own targets, replacing the calculated ones when set. */
    val customTargets: CustomTargets? = null,
    /** The Google Drive file nightly backups are written to (M10). */
    val driveBackupUri: String? = null,
    val lastDriveBackupAt: Long? = null,
    val lastDriveBackupError: String? = null,
    /** Your heart-rate strap (Bluetooth address and name) for live heart rate (M10). */
    val hrDeviceAddress: String? = null,
    val hrDeviceName: String? = null,
    /** First-run setup finished or skipped (M10). */
    val setupDone: Boolean = false,
) {
    fun weightsFor(equipment: Equipment?): List<Double> =
        equipment?.let { ownedWeights[it] }.orEmpty()

    companion object {
        val DEFAULT_EQUIPMENT = setOf(Equipment.BODY_ONLY, Equipment.BANDS, Equipment.DUMBBELL)
        val REST_OPTIONS = listOf(30, 60, 90, 120, 150, 180, 240, 300)
    }
}

data class CustomTargets(val kcal: Int, val proteinG: Int, val carbsG: Int, val fatG: Int)

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
        val SEX = stringPreferencesKey("sex")
        val BIRTH_YEAR = intPreferencesKey("birth_year")
        val ACTIVITY = stringPreferencesKey("activity_level")
        val GOAL = stringPreferencesKey("nutrition_goal")
        val CUSTOM_KCAL = intPreferencesKey("custom_kcal")
        val CUSTOM_PROTEIN = intPreferencesKey("custom_protein")
        val CUSTOM_CARBS = intPreferencesKey("custom_carbs")
        val CUSTOM_FAT = intPreferencesKey("custom_fat")
        val DRIVE_URI = stringPreferencesKey("drive_backup_uri")
        val DRIVE_LAST = longPreferencesKey("drive_backup_last")
        val DRIVE_ERROR = stringPreferencesKey("drive_backup_error")
        val HR_ADDRESS = stringPreferencesKey("hr_device_address")
        val HR_NAME = stringPreferencesKey("hr_device_name")
        val SETUP_DONE = booleanPreferencesKey("setup_done")
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
            sex = p[Keys.SEX]?.let { name -> Sex.entries.firstOrNull { it.name == name } },
            birthYear = p[Keys.BIRTH_YEAR],
            activityLevel = p[Keys.ACTIVITY].toEnumOr(defaults.activityLevel),
            nutritionGoal = p[Keys.GOAL].toEnumOr(defaults.nutritionGoal),
            driveBackupUri = p[Keys.DRIVE_URI],
            hrDeviceAddress = p[Keys.HR_ADDRESS],
            hrDeviceName = p[Keys.HR_NAME],
            setupDone = p[Keys.SETUP_DONE] ?: false,
            lastDriveBackupAt = p[Keys.DRIVE_LAST],
            lastDriveBackupError = p[Keys.DRIVE_ERROR],
            customTargets = p[Keys.CUSTOM_KCAL]?.let { kcal ->
                CustomTargets(kcal, p[Keys.CUSTOM_PROTEIN] ?: 0, p[Keys.CUSTOM_CARBS] ?: 0, p[Keys.CUSTOM_FAT] ?: 0)
            },
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) = dataStore.edit { it[Keys.THEME] = mode.name }

    suspend fun setWeightUnit(unit: WeightUnit) = dataStore.edit { it[Keys.UNIT] = unit.name }

    suspend fun setDefaultRestSeconds(seconds: Int) = dataStore.edit { it[Keys.REST] = seconds }

    suspend fun setEquipment(equipment: Set<Equipment>) =
        dataStore.edit { it[Keys.EQUIPMENT] = equipment.map(Equipment::name).toSet() }

    /** Read and write in one step, so quick taps can't overwrite each other. */
    suspend fun toggleEquipment(item: Equipment) = dataStore.edit { prefs ->
        val current = prefs[Keys.EQUIPMENT]?.toSet() ?: UserPreferences.DEFAULT_EQUIPMENT.map(Equipment::name).toSet()
        prefs[Keys.EQUIPMENT] = if (item.name in current) current - item.name else current + item.name
    }

    suspend fun dismissDeloadUntil(epochDay: Long) = dataStore.edit { it[Keys.DELOAD_DISMISSED] = epochDay }

    suspend fun setHealthConnectEnabled(enabled: Boolean) = dataStore.edit { it[Keys.HEALTH_ENABLED] = enabled }

    suspend fun setLastHealthSync(millis: Long?) = dataStore.edit {
        if (millis == null) it.remove(Keys.HEALTH_LAST_SYNC) else it[Keys.HEALTH_LAST_SYNC] = millis
    }

    suspend fun setSex(sex: Sex) = dataStore.edit { it[Keys.SEX] = sex.name }

    suspend fun setBirthYear(year: Int?) = dataStore.edit {
        if (year == null) it.remove(Keys.BIRTH_YEAR) else it[Keys.BIRTH_YEAR] = year
    }

    suspend fun setActivityLevel(level: ActivityLevel) = dataStore.edit { it[Keys.ACTIVITY] = level.name }

    suspend fun setNutritionGoal(goal: NutritionGoal) = dataStore.edit { it[Keys.GOAL] = goal.name }

    /** Null goes back to the calculated targets. */
    suspend fun setCustomTargets(targets: CustomTargets?) = dataStore.edit {
        if (targets == null) {
            it.remove(Keys.CUSTOM_KCAL); it.remove(Keys.CUSTOM_PROTEIN); it.remove(Keys.CUSTOM_CARBS); it.remove(Keys.CUSTOM_FAT)
        } else {
            it[Keys.CUSTOM_KCAL] = targets.kcal
            it[Keys.CUSTOM_PROTEIN] = targets.proteinG
            it[Keys.CUSTOM_CARBS] = targets.carbsG
            it[Keys.CUSTOM_FAT] = targets.fatG
        }
    }

    suspend fun setSetupDone() = dataStore.edit { it[Keys.SETUP_DONE] = true }

    suspend fun setHrDevice(address: String?, name: String?) = dataStore.edit {
        if (address == null) { it.remove(Keys.HR_ADDRESS); it.remove(Keys.HR_NAME) } else {
            it[Keys.HR_ADDRESS] = address
            if (name == null) it.remove(Keys.HR_NAME) else it[Keys.HR_NAME] = name
        }
    }

    suspend fun setDriveBackup(uri: String?, lastAt: Long?, error: String?) = dataStore.edit {
        if (uri == null) it.remove(Keys.DRIVE_URI) else it[Keys.DRIVE_URI] = uri
        if (lastAt == null) it.remove(Keys.DRIVE_LAST) else it[Keys.DRIVE_LAST] = lastAt
        if (error == null) it.remove(Keys.DRIVE_ERROR) else it[Keys.DRIVE_ERROR] = error
    }

    /** A backup run's result, only if [forUri] is still the chosen file (it may have been changed meanwhile). */
    suspend fun recordDriveResult(forUri: String, lastAt: Long?, error: String?) = dataStore.edit {
        if (it[Keys.DRIVE_URI] != forUri) return@edit
        if (lastAt == null) it.remove(Keys.DRIVE_LAST) else it[Keys.DRIVE_LAST] = lastAt
        if (error == null) it.remove(Keys.DRIVE_ERROR) else it[Keys.DRIVE_ERROR] = error
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

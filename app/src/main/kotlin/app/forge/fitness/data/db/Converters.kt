package app.forge.fitness.data.db

import androidx.room.TypeConverter
import app.forge.domain.model.Muscle
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/** Stores lists as JSON text. Enums are stored by name so reordering them is safe. */
class Converters {
    private val stringList = ListSerializer(String.serializer())

    @TypeConverter
    fun stringListToJson(value: List<String>): String = Json.encodeToString(stringList, value)

    @TypeConverter
    fun jsonToStringList(value: String): List<String> = Json.decodeFromString(stringList, value)

    @TypeConverter
    fun muscleListToJson(value: List<Muscle>): String =
        Json.encodeToString(stringList, value.map { it.name })

    /** Unknown names (e.g. from a newer backup) are skipped instead of crashing. */
    @TypeConverter
    fun jsonToMuscleList(value: String): List<Muscle> =
        Json.decodeFromString(stringList, value).mapNotNull { name ->
            Muscle.entries.firstOrNull { it.name == name }
        }
}

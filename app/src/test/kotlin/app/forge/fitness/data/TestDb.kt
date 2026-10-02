package app.forge.fitness.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.forge.domain.model.Equipment
import app.forge.domain.model.ExerciseCategory
import app.forge.domain.model.LogType
import app.forge.domain.model.Muscle
import app.forge.fitness.data.db.ExerciseEntity
import app.forge.fitness.data.db.ForgeDatabase
import app.forge.fitness.di.TimeSource

/** A controllable clock: each call to [now] returns the current value; [advance] moves it. */
class FakeTime(var millis: Long = 1_700_000_000_000L) : TimeSource {
    override fun now() = millis
    fun advance(ms: Long) { millis += ms }
}

object TestDb {
    val context: Context get() = ApplicationProvider.getApplicationContext()

    fun inMemory(): ForgeDatabase =
        Room.inMemoryDatabaseBuilder(context, ForgeDatabase::class.java)
            .allowMainThreadQueries()
            .build()

    /** A real file on disk, so a test can close and reopen it like an app restart. */
    fun onDisk(name: String): ForgeDatabase =
        Room.databaseBuilder(context, ForgeDatabase::class.java, name)
            .allowMainThreadQueries()
            .build()

    fun exercise(id: String, name: String = id, logType: LogType = LogType.WEIGHT_REPS) = ExerciseEntity(
        id = id,
        name = name,
        primaryMuscles = listOf(Muscle.CHEST),
        secondaryMuscles = emptyList(),
        equipment = Equipment.DUMBBELL,
        category = ExerciseCategory.STRENGTH,
        logType = logType,
        mechanic = null,
        force = null,
        level = null,
        instructions = emptyList(),
        images = emptyList(),
        isCustom = true,
        sourceId = null,
        createdAt = 0,
        updatedAt = 0,
    )
}

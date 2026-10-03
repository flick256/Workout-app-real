package app.forge.fitness

import android.app.Application
import app.forge.fitness.data.exercise.ExerciseSeeder
import app.forge.fitness.di.ApplicationScope
import app.forge.fitness.reminders.ReminderScheduler
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@HiltAndroidApp
class ForgeApplication : Application() {

    @Inject lateinit var exerciseSeeder: ExerciseSeeder

    @Inject lateinit var reminders: ReminderScheduler

    @Inject @field:ApplicationScope
    lateinit var appScope: CoroutineScope

    override fun onCreate() {
        super.onCreate()
        // Off the main thread, so it never slows down the app's start.
        appScope.launch { exerciseSeeder.seedIfNeeded() }
        // Cheap, and makes sure habit reminders are set even if an alarm was lost.
        appScope.launch { reminders.rescheduleAll() }
    }
}

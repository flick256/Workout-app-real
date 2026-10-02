package app.forge.fitness

import android.app.Application
import app.forge.fitness.data.exercise.ExerciseSeeder
import app.forge.fitness.di.ApplicationScope
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@HiltAndroidApp
class ForgeApplication : Application() {

    @Inject lateinit var exerciseSeeder: ExerciseSeeder

    @Inject @ApplicationScope
    lateinit var appScope: CoroutineScope

    override fun onCreate() {
        super.onCreate()
        // Off the main thread, so it never slows down the app's start.
        appScope.launch { exerciseSeeder.seedIfNeeded() }
    }
}

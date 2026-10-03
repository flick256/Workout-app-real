package app.forge.fitness

import android.app.Application
import app.forge.fitness.data.exercise.ExerciseSeeder
import app.forge.fitness.data.backup.BackupScheduler
import app.forge.fitness.di.ApplicationScope
import app.forge.fitness.heart.HeartRateSession
import app.forge.fitness.reminders.ReminderScheduler
import app.forge.fitness.widget.Shortcuts
import app.forge.fitness.widget.WidgetData
import app.forge.fitness.widget.updateAllWidgets
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@OptIn(FlowPreview::class)
@HiltAndroidApp
class ForgeApplication : Application() {

    @Inject lateinit var exerciseSeeder: ExerciseSeeder

    @Inject lateinit var reminders: ReminderScheduler

    @Inject lateinit var widgetData: WidgetData

    @Inject lateinit var backups: BackupScheduler

    @Inject lateinit var heartRate: HeartRateSession

    @Inject @field:ApplicationScope
    lateinit var appScope: CoroutineScope

    override fun onCreate() {
        super.onCreate()
        // Off the main thread, so it never slows down the app's start.
        appScope.launch { exerciseSeeder.seedIfNeeded() }
        // Cheap, and makes sure habit reminders are set even if an alarm was lost.
        appScope.launch { reminders.rescheduleAll() }
        appScope.launch { Shortcuts.publish(this@ForgeApplication) }
        // Nightly snapshot on the phone + copy to your Drive file (if set). KEEP: no-op if already scheduled.
        appScope.launch { backups.scheduleDaily() }
        // Live heart rate: connects to your strap whenever a workout is running.
        heartRate.start()
        // Keep home-screen widgets in step with what you log (debounced, so a burst of
        // ticked sets is one refresh). Only runs while Forge itself is running.
        appScope.launch {
            widgetData.observeToday().drop(1).debounce(WIDGET_DEBOUNCE_MS).collect { updateAllWidgets(this@ForgeApplication) }
        }
    }
}

private const val WIDGET_DEBOUNCE_MS = 1_500L

package app.forge.fitness.reminders

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.forge.domain.goals.DayMask
import app.forge.domain.goals.HabitKind
import app.forge.fitness.MainActivity
import app.forge.fitness.R
import app.forge.fitness.data.db.HabitEntity
import app.forge.fitness.data.goals.GoalsRepository
import app.forge.fitness.di.ApplicationScope
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Habit reminders, using the alarm clock rather than a background job, so they arrive on
 * time and nothing runs in between. Alarms are set one at a time (the next due day) and
 * re-set after each one fires, after a reboot, and when habits change.
 */
@Singleton
class ReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val goals: GoalsRepository,
) {
    private val alarms = context.getSystemService(AlarmManager::class.java)

    init {
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "Habit reminders", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Reminders for the habits you set a time on"
            },
        )
    }

    /** Re-sets every reminder from scratch (cheap: one alarm per habit). */
    suspend fun rescheduleAll(knownIds: Collection<String> = emptyList()) {
        val habits = goals.habitsWithReminders()
        (knownIds - habits.map { it.id }.toSet()).forEach(::cancel)
        habits.forEach(::schedule)
    }

    fun schedule(habit: HabitEntity) {
        val minutes = habit.reminderMinutes ?: return cancel(habit.id)
        val next = nextTime(DayMask(habit.dayMask), minutes, LocalDateTime.now()) ?: return cancel(habit.id)
        val at = next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        // A few minutes' leeway lets Android batch it with other alarms to save battery.
        alarms.setWindow(AlarmManager.RTC_WAKEUP, at, WINDOW_MS, pending(habit.id))
    }

    fun cancel(habitId: String) = alarms.cancel(pending(habitId))

    private fun pending(habitId: String): PendingIntent = PendingIntent.getBroadcast(
        context,
        habitId.hashCode(),
        Intent(context, ReminderReceiver::class.java).setAction(ACTION_REMIND).putExtra(EXTRA_HABIT, habitId),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    fun notify(habit: HabitEntity) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val builder = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_forge)
            .setContentTitle(habit.name)
            .setContentText("Keep your streak going")
            .setContentIntent(open)
            .setAutoCancel(true)
        if (HabitKind.entries.firstOrNull { it.name == habit.kind } == HabitKind.CUSTOM) {
            val done = PendingIntent.getBroadcast(
                context,
                habit.id.hashCode() + 1,
                Intent(context, ReminderReceiver::class.java).setAction(ACTION_DONE).putExtra(EXTRA_HABIT, habit.id),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            builder.addAction(0, "Done", done)
        }
        NotificationManagerCompat.from(context).notify(habit.id.hashCode(), builder.build())
    }

    fun dismiss(habitId: String) = NotificationManagerCompat.from(context).cancel(habitId.hashCode())

    companion object {
        const val CHANNEL = "habit_reminders"
        const val ACTION_REMIND = "app.forge.fitness.HABIT_REMIND"
        const val ACTION_DONE = "app.forge.fitness.HABIT_DONE"
        const val EXTRA_HABIT = "habit"
        private const val WINDOW_MS = 5 * 60_000L

        /** The next due day at [minutes] after midnight, strictly after [now]. */
        fun nextTime(due: DayMask, minutes: Int, now: LocalDateTime): LocalDateTime? {
            if (due.count == 0) return null
            val time = LocalTime.of((minutes / 60).coerceIn(0, 23), (minutes % 60).coerceIn(0, 59))
            var day: LocalDate = now.toLocalDate()
            repeat(8) {
                val candidate = LocalDateTime.of(day, time)
                if (due.isDue(day) && candidate.isAfter(now)) return candidate
                day = day.plusDays(1)
            }
            return null
        }
    }
}

/** Fires a reminder (if the habit isn't done yet), or ticks it from the "Done" button. */
class ReminderReceiver : BroadcastReceiver() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Deps {
        fun scheduler(): ReminderScheduler
        fun goals(): GoalsRepository

        @ApplicationScope
        fun appScope(): CoroutineScope
    }

    override fun onReceive(context: Context, intent: Intent) {
        val deps = EntryPointAccessors.fromApplication(context.applicationContext, Deps::class.java)
        val habitId = intent.getStringExtra(ReminderScheduler.EXTRA_HABIT) ?: return
        val pending = goAsync()
        deps.appScope().launch {
            try {
                val habit = deps.goals().getHabit(habitId)
                when (intent.action) {
                    ReminderScheduler.ACTION_REMIND -> if (habit != null && habit.deletedAt == null) {
                        val custom = habit.kind == HabitKind.CUSTOM.name
                        val alreadyDone = custom && deps.goals().isCheckedToday(habit.id)
                        if (!alreadyDone) deps.scheduler().notify(habit)
                        deps.scheduler().schedule(habit)
                    }
                    ReminderScheduler.ACTION_DONE -> {
                        deps.goals().setChecked(habitId, LocalDate.now(), true)
                        deps.scheduler().dismiss(habitId)
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }
}

/** Alarms are cleared by a reboot, an app update or a time-zone change: set them again. */
class RescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val deps = EntryPointAccessors.fromApplication(context.applicationContext, ReminderReceiver.Deps::class.java)
        val pending = goAsync()
        deps.appScope().launch {
            try {
                deps.scheduler().rescheduleAll()
            } finally {
                pending.finish()
            }
        }
    }
}

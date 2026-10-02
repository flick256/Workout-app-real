package app.forge.fitness.timer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/** Handles the rest alarm and the "+30s" / "Skip" buttons on the notification. */
class RestTimerReceiver : BroadcastReceiver() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Deps {
        fun restTimer(): RestTimer
    }

    override fun onReceive(context: Context, intent: Intent) {
        val restTimer = EntryPointAccessors.fromApplication(context.applicationContext, Deps::class.java).restTimer()
        when (intent.action) {
            ACTION_FINISHED -> restTimer.onFinished()
            ACTION_ADD_30 -> restTimer.adjust(30)
            ACTION_SKIP -> restTimer.stop()
        }
    }

    companion object {
        const val ACTION_FINISHED = "app.forge.fitness.REST_FINISHED"
        const val ACTION_ADD_30 = "app.forge.fitness.REST_ADD_30"
        const val ACTION_SKIP = "app.forge.fitness.REST_SKIP"
    }
}

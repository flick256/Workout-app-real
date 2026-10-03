package app.forge.fitness

import android.content.Context
import android.content.Intent

/**
 * What widgets, launcher shortcuts and notifications can ask the app to do. They all
 * open [MainActivity] with one of these actions.
 */
object AppActions {
    const val OPEN_WORKOUT = "app.forge.fitness.OPEN_WORKOUT"
    const val START_WORKOUT = "app.forge.fitness.START_WORKOUT"
    const val START_ROUTINE = "app.forge.fitness.START_ROUTINE"
    const val OPEN_FOOD = "app.forge.fitness.OPEN_FOOD"
    const val SCAN_FOOD = "app.forge.fitness.SCAN_FOOD"
    const val LOG_ACTIVITY = "app.forge.fitness.LOG_ACTIVITY"
    const val OPEN_HABITS = "app.forge.fitness.OPEN_HABITS"
    const val EXTRA_ROUTINE = "routine"

    fun intent(context: Context, action: String, routineId: String? = null): Intent =
        Intent(context, MainActivity::class.java)
            .setAction(action)
            .apply { routineId?.let { putExtra(EXTRA_ROUTINE, it) } }
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
}

/** A request from outside the app, handled once the UI is ready. */
sealed interface PendingAction {
    data object OpenWorkout : PendingAction
    data object OpenFood : PendingAction
    data object ScanFood : PendingAction
    data object LogActivity : PendingAction
    data object OpenHabits : PendingAction
}

package app.forge.fitness.widget

import android.content.Context
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import app.forge.fitness.AppActions
import app.forge.fitness.R

/**
 * Long-press Forge's icon for these. Set from code (not XML) so they point at the right
 * package for both the debug and release builds.
 */
object Shortcuts {
    fun publish(context: Context) {
        val shortcuts = listOf(
            shortcut(context, "start", "Start workout", AppActions.START_WORKOUT, R.drawable.ic_shortcut_workout),
            shortcut(context, "scan", "Scan food", AppActions.SCAN_FOOD, R.drawable.ic_shortcut_food),
            shortcut(context, "activity", "Log activity", AppActions.LOG_ACTIVITY, R.drawable.ic_shortcut_activity),
            shortcut(context, "habits", "Habits", AppActions.OPEN_HABITS, R.drawable.ic_shortcut_habits),
        )
        runCatching { ShortcutManagerCompat.setDynamicShortcuts(context, shortcuts) }
    }

    private fun shortcut(context: Context, id: String, label: String, action: String, icon: Int) =
        ShortcutInfoCompat.Builder(context, id)
            .setShortLabel(label)
            .setIcon(IconCompat.createWithResource(context, icon))
            .setIntent(AppActions.intent(context, action))
            .build()
}

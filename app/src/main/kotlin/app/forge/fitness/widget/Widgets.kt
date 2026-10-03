package app.forge.fitness.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import app.forge.fitness.AppActions
import app.forge.fitness.data.goals.GoalsRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun widgetData(): WidgetData
    fun goals(): GoalsRepository
}

private fun deps(context: Context) = EntryPointAccessors.fromApplication(context.applicationContext, WidgetEntryPoint::class.java)

/** Refreshes every Forge widget on the home screen. Cheap when none are placed. */
suspend fun updateAllWidgets(context: Context) {
    TodayWidget().updateAll(context)
    HabitsWidget().updateAll(context)
}

// ---- Today widget -------------------------------------------------------------------

/** Today at a glance: what's planned, a start button, streak, food and habits. */
class TodayWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(SMALL, WIDE, LARGE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = deps(context).widgetData()
        val initial = data.observeToday().first()
        provideContent {
            val flow = remember { data.observeToday() }
            val snapshot by flow.collectAsState(initial)
            GlanceTheme { TodayContent(context, snapshot) }
        }
    }

    companion object {
        val SMALL = DpSize(120.dp, 100.dp)
        val WIDE = DpSize(250.dp, 100.dp)
        val LARGE = DpSize(250.dp, 200.dp)
    }
}

class TodayWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodayWidget()
}

@Composable
private fun TodayContent(context: Context, s: TodaySnapshot) {
    val size = LocalSize.current
    val wide = size.width >= TodayWidget.WIDE.width
    val tall = size.height >= TodayWidget.LARGE.height
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .background(GlanceTheme.colors.widgetBackground)
            .cornerRadius(20.dp)
            .padding(12.dp),
    ) {
        // Header: what's next.
        val headline = when {
            s.activeWorkout != null -> "In progress: ${s.activeWorkout}"
            s.planLine != null -> s.planLine
            else -> "Ready when you are"
        }
        Text(
            "FORGE",
            style = TextStyle(color = GlanceTheme.colors.primary, fontSize = 11.sp, fontWeight = FontWeight.Bold),
        )
        Text(
            headline,
            maxLines = 2,
            style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 16.sp, fontWeight = FontWeight.Bold),
        )
        if (wide) {
            Text(
                "${s.workoutsThisWeek} this week · ${s.weekStreak} wk streak",
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp),
            )
        }
        Spacer(GlanceModifier.height(8.dp))
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            val (label, intent) = when {
                s.activeWorkout != null -> "Resume" to AppActions.intent(context, AppActions.OPEN_WORKOUT)
                s.plannedRoutineId != null -> "Start" to AppActions.intent(context, AppActions.START_ROUTINE, s.plannedRoutineId)
                else -> "Start workout" to AppActions.intent(context, AppActions.START_WORKOUT)
            }
            Pill(label, primary = true, modifier = GlanceModifier.clickable(actionStartActivity(intent)))
            if (wide) {
                Spacer(GlanceModifier.width(8.dp))
                Pill("Scan food", primary = false, modifier = GlanceModifier.clickable(actionStartActivity(AppActions.intent(context, AppActions.SCAN_FOOD))))
            }
        }
        if (tall) {
            Spacer(GlanceModifier.height(10.dp))
            val food = s.kcalTarget?.let { "${s.kcal} / $it kcal · protein ${s.proteinG}/${s.proteinTarget ?: "–"} g" }
                ?: "${s.kcal} kcal · protein ${s.proteinG} g"
            Text(
                food,
                style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 13.sp),
                modifier = GlanceModifier.clickable(actionStartActivity(AppActions.intent(context, AppActions.OPEN_FOOD))),
            )
            if (s.habits.isNotEmpty()) {
                Text(
                    "Habits ${s.habits.count { it.done }}/${s.habits.size} done",
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 13.sp),
                    modifier = GlanceModifier.clickable(actionStartActivity(AppActions.intent(context, AppActions.OPEN_HABITS))),
                )
            }
        }
    }
}

@Composable
private fun Pill(text: String, primary: Boolean, modifier: GlanceModifier) {
    Box(
        modifier = modifier
            .background(if (primary) GlanceTheme.colors.primary else GlanceTheme.colors.secondaryContainer)
            .cornerRadius(18.dp)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = TextStyle(
                color = if (primary) GlanceTheme.colors.onPrimary else GlanceTheme.colors.onSecondaryContainer,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            ),
        )
    }
}

// ---- Habits widget ------------------------------------------------------------------

/** Today's habits; tap one you tick by hand to mark it done. */
class HabitsWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = deps(context).widgetData()
        val initial = data.observeToday().first()
        provideContent {
            val flow = remember { data.observeToday() }
            val snapshot by flow.collectAsState(initial)
            GlanceTheme { HabitsContent(context, snapshot) }
        }
    }
}

class HabitsWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = HabitsWidget()
}

private val HABIT_ID = ActionParameters.Key<String>("habitId")

/** Ticks or unticks a custom habit for today, right from the home screen. */
class ToggleHabitAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val id = parameters[HABIT_ID] ?: return
        deps(context).goals().toggleToday(id)
        updateAllWidgets(context)
    }
}

@Composable
private fun HabitsContent(context: Context, s: TodaySnapshot) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .background(GlanceTheme.colors.widgetBackground)
            .cornerRadius(20.dp)
            .padding(12.dp),
    ) {
        Text(
            "HABITS · ${s.habits.count { it.done }}/${s.habits.size}",
            style = TextStyle(color = GlanceTheme.colors.primary, fontSize = 11.sp, fontWeight = FontWeight.Bold),
            modifier = GlanceModifier.clickable(actionStartActivity(AppActions.intent(context, AppActions.OPEN_HABITS))),
        )
        if (s.habits.isEmpty()) {
            Text(
                "No habits due today. Tap to add some.",
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 13.sp),
                modifier = GlanceModifier.clickable(actionStartActivity(AppActions.intent(context, AppActions.OPEN_HABITS))),
            )
        }
        s.habits.take(MAX_WIDGET_HABITS).forEach { habit ->
            val tick = if (habit.done) "✓" else if (habit.auto) "↻" else "○"
            val action = if (habit.auto) {
                actionStartActivity(AppActions.intent(context, AppActions.OPEN_HABITS))
            } else {
                actionRunCallback<ToggleHabitAction>(actionParametersOf(HABIT_ID to habit.id))
            }
            Row(
                modifier = GlanceModifier.fillMaxWidth().padding(vertical = 6.dp).clickable(action),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    tick,
                    style = TextStyle(
                        color = if (habit.done) GlanceTheme.colors.primary else GlanceTheme.colors.onSurfaceVariant,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                )
                Spacer(GlanceModifier.width(10.dp))
                Text(
                    habit.name + if (habit.streak > 1) "  🔥${habit.streak}" else "",
                    maxLines = 1,
                    style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 14.sp),
                )
            }
        }
    }
}

private const val MAX_WIDGET_HABITS = 6

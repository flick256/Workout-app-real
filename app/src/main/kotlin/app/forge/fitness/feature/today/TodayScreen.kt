package app.forge.fitness.feature.today

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.forge.fitness.feature.history.Stat
import app.forge.fitness.feature.routines.ProgramPlanCard
import app.forge.fitness.feature.routines.RoutineCard
import app.forge.fitness.feature.routines.rememberRoutineStarter
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.TextButton
import app.forge.fitness.ui.components.BigButton
import app.forge.fitness.ui.components.ForgeCard
import app.forge.domain.suggest.QuickPlan
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.forge.fitness.ui.components.ScreenScaffold
import app.forge.fitness.ui.format.Format
import app.forge.fitness.ui.theme.Spacing
import app.forge.fitness.ui.theme.tabular
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun TodayScreen(
    onOpenWorkout: () -> Unit,
    onOpenSession: (String) -> Unit,
    onOpenRoutines: () -> Unit,
    onOpenRoutine: (String) -> Unit,
    onLogActivity: () -> Unit,
    onOpenHealth: () -> Unit,
    onOpenFood: () -> Unit,
    onOpenGoals: () -> Unit,
    vm: TodayViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val goals by vm.goalsOverview.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val greeting = remember { greetingFor(LocalTime.now()) }
    val date = remember { LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE d MMMM")) }

    fun start() = scope.launch {
        vm.startWorkout()
        onOpenWorkout()
    }
    val startRoutine = rememberRoutineStarter(vm::startRoutine, onStarted = onOpenWorkout, onResume = onOpenWorkout)
    var quickToStart by remember { mutableStateOf<QuickPlan?>(null) }
    val startQuickById = rememberRoutineStarter(
        start = { _ -> quickToStart?.let { vm.startQuick(it, state.minutes) } },
        onStarted = onOpenWorkout,
        onResume = onOpenWorkout,
    )
    fun startQuick(plan: QuickPlan) {
        quickToStart = plan
        startQuickById("quick")
    }

    // The rest timer needs notification permission (Android 13+). Ask once, the first
    // time you start a workout; the workout starts either way.
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { start() }
    fun startWithPermission() {
        val needsAsk = Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(
            context, Manifest.permission.POST_NOTIFICATIONS,
        ) != PackageManager.PERMISSION_GRANTED
        if (needsAsk) permission.launch(Manifest.permission.POST_NOTIFICATIONS) else start()
    }

    ScreenScaffold(title = greeting) {
        item {
            Text(
                date,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        val active = state.active
        if (active != null) {
            item(key = "active") {
                Card(
                    onClick = onOpenWorkout,
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                ) {
                    Column(Modifier.fillMaxWidth().padding(Spacing.lg)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Timer, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                            Spacer(Modifier.width(Spacing.sm))
                            Text(
                                "Workout in progress",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                        Spacer(Modifier.height(Spacing.xs))
                        val elapsed by produceState(0L, active.startedAt) {
                            while (true) {
                                value = (System.currentTimeMillis() - active.startedAt) / 1000
                                delay(1_000)
                            }
                        }
                        Text(
                            "${active.name} · ${Format.duration(elapsed)}",
                            style = MaterialTheme.typography.bodyMedium.tabular(),
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Spacer(Modifier.height(Spacing.md))
                        BigButton(text = "Resume workout", icon = Icons.Rounded.PlayArrow, onClick = onOpenWorkout)
                    }
                }
            }
        } else {
            item(key = "start") {
                ForgeCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Bolt, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(Spacing.sm))
                        Text("Quick start", style = MaterialTheme.typography.titleLarge)
                    }
                    Spacer(Modifier.height(Spacing.sm))
                    Text(
                        "Start an empty workout and add exercises as you go. Every set saves the moment you tick it.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(Spacing.lg))
                    BigButton(text = "Start workout", icon = Icons.Rounded.PlayArrow, onClick = ::startWithPermission)
                    TextButton(onClick = onLogActivity, modifier = Modifier.fillMaxWidth()) {
                        Text("Log a sport, run or other activity")
                    }
                }
            }
        }

        item(key = "habits") { HabitsTodayCard(goals, onToggle = vm::toggleHabit, onOpen = onOpenGoals) }

        item(key = "food") { FoodTodayCard(state.food, onClick = onOpenFood) }

        if (state.health.enabled) {
            item(key = "health") { HealthTodayCard(state.health, onClick = onOpenHealth) }
        }

        if (active == null) {
            state.routines.active?.let { plan ->
                item(key = "plan") { ProgramPlanCard(plan = plan, onStart = startRoutine) }
            }
        }

        // Routines not already shown by the active program's card (all of them when there's no program).
        val activeProgramId = state.routines.active?.program?.id
        val unplanned = state.routines.routines.filter { activeProgramId == null || it.data.routine.programId != activeProgramId }
        item(key = "routines-header") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "MY ROUTINES",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onOpenRoutines) { Text("Routines & programs") }
            }
        }
        if (state.routines.routines.isEmpty()) {
            item(key = "routines-empty") {
                ForgeCard {
                    Text("Plan your training", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Pick a ready-made home program (full body, push/pull/legs, calisthenics or a " +
                            "15-minute express) or build your own routines.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = onOpenRoutines) { Text("Browse routines & programs") }
                }
            }
        }
        items(unplanned.take(MAX_ROUTINES_ON_TODAY), key = { "r-" + it.id }) { routine ->
            RoutineCard(routine = routine, onStart = { startRoutine(routine.id) }, onOpen = { onOpenRoutine(routine.id) })
        }

        state.lastWorkout?.let { last ->
            item(key = "last") {
                Card(
                    onClick = { onOpenSession(last.id) },
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                ) {
                    Column(Modifier.fillMaxWidth().padding(Spacing.lg)) {
                        Text("Last workout", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(last.name, style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(Spacing.sm))
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.lg)) {
                            Stat("Last 7 days", "${state.workoutsThisWeek}")
                            Stat("Sets", "${last.setCount}")
                            if (last.volumeKg > 0) Stat("Volume", Format.volume(last.volumeKg, state.unit))
                        }
                    }
                }
            }
        }

        state.deload?.let { hint ->
            item(key = "deload") { DeloadCard(hint, onDismiss = vm::dismissDeload) }
        }
        if (active == null) {
            item(key = "train-today") {
                TrainTodayCard(
                    minutes = state.minutes,
                    muscles = state.muscles,
                    bestRoutine = state.bestRoutine,
                    quickPlan = state.quickPlan,
                    readinessLow = state.health.enabled &&
                        state.health.readiness.level == app.forge.domain.activity.ReadinessLevel.LOW,
                    onMinutes = vm::setMinutes,
                    onStartRoutine = startRoutine,
                    onStartQuick = { plan -> startQuick(plan) },
                )
            }
        }
    }
}

private const val MAX_ROUTINES_ON_TODAY = 4

private fun greetingFor(time: LocalTime): String = when (time.hour) {
    in 5..11 -> "Good morning"
    in 12..17 -> "Good afternoon"
    else -> "Good evening"
}

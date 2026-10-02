package app.forge.fitness.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import androidx.compose.ui.unit.dp
import app.forge.fitness.data.db.WorkoutSessionEntity
import app.forge.fitness.feature.exercises.ExerciseDetailScreen
import app.forge.fitness.feature.exercises.ExerciseEditScreen
import app.forge.fitness.feature.exercises.ExercisePickerScreen
import app.forge.fitness.feature.exercises.ExercisesScreen
import app.forge.fitness.feature.history.HistoryScreen
import app.forge.fitness.feature.history.SessionDetailScreen
import app.forge.fitness.feature.progress.ProgressScreen
import app.forge.fitness.feature.settings.SettingsScreen
import app.forge.fitness.feature.today.TodayScreen
import app.forge.fitness.feature.workout.ActiveWorkoutScreen
import app.forge.fitness.ui.components.LocalSnackbarHostState
import app.forge.fitness.ui.components.rememberHaptics
import app.forge.fitness.ui.theme.Spacing

@Composable
fun ForgeApp(
    activeWorkout: WorkoutSessionEntity?,
    openWorkoutRequested: Boolean,
    onOpenWorkoutHandled: () -> Unit,
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val haptics = rememberHaptics()
    val snackbar = remember { SnackbarHostState() }
    val onTopLevel = currentDestination.isTopLevel()

    fun openWorkout() = navController.navigate(ActiveWorkoutRoute) { launchSingleTop = true }

    LaunchedEffect(openWorkoutRequested, activeWorkout != null) {
        if (openWorkoutRequested && activeWorkout != null) {
            openWorkout()
            onOpenWorkoutHandled()
        }
    }

    CompositionLocalProvider(LocalSnackbarHostState provides snackbar) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = {
                if (onTopLevel) {
                    Column {
                        AnimatedVisibility(
                            visible = activeWorkout != null,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut(),
                        ) {
                            ResumeBar(activeWorkout?.name.orEmpty(), onClick = ::openWorkout)
                        }
                        NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                            TopLevelDestination.entries.forEach { dest ->
                                val selected = currentDestination?.hierarchy?.any {
                                    it.hasRoute(dest.route::class)
                                } == true
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = {
                                        if (!selected) haptics.tick()
                                        navController.navigate(dest.route) {
                                            // Standard bottom-nav behaviour: one copy of each tab,
                                            // and each tab remembers its scroll position.
                                            popUpTo(navController.graph.findStartDestination().id) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                    icon = {
                                        Icon(
                                            if (selected) dest.selectedIcon else dest.icon,
                                            contentDescription = null,
                                        )
                                    },
                                    label = { Text(dest.label) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                                        indicatorColor = MaterialTheme.colorScheme.primary,
                                    ),
                                )
                            }
                        }
                    }
                }
            },
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = TodayRoute,
                modifier = Modifier.padding(bottom = if (onTopLevel) innerPadding.calculateBottomPadding() else 0.dp),
                enterTransition = { fadeIn(tween(180)) },
                exitTransition = { fadeOut(tween(120)) },
            ) {
                composable<TodayRoute> {
                    TodayScreen(
                        onOpenWorkout = ::openWorkout,
                        onOpenSession = { navController.navigate(SessionDetailRoute(it)) },
                    )
                }
                composable<HistoryRoute> {
                    HistoryScreen(onOpen = { navController.navigate(SessionDetailRoute(it)) })
                }
                composable<ExercisesRoute> {
                    ExercisesScreen(
                        onOpen = { navController.navigate(ExerciseDetailRoute(it)) },
                        onCreate = { navController.navigate(ExerciseEditRoute()) },
                    )
                }
                composable<ProgressRoute> { ProgressScreen() }
                composable<SettingsRoute> { SettingsScreen() }

                composable<ActiveWorkoutRoute>(
                    enterTransition = { slideInHorizontally(tween(220)) { it / 6 } + fadeIn(tween(220)) },
                    popExitTransition = { slideOutHorizontally(tween(180)) { it / 6 } + fadeOut(tween(180)) },
                ) {
                    ActiveWorkoutScreen(
                        onBack = { navController.popBackStack() },
                        onAddExercises = { navController.navigate(ExercisePickerRoute(it)) },
                        onOpenExercise = { navController.navigate(ExerciseDetailRoute(it)) },
                        onFinished = { id ->
                            navController.navigate(SessionDetailRoute(id, justFinished = true)) {
                                popUpTo(ActiveWorkoutRoute) { inclusive = true }
                            }
                        },
                    )
                }
                composable<ExercisePickerRoute> { entry ->
                    ExercisePickerScreen(
                        sessionId = entry.toRoute<ExercisePickerRoute>().sessionId,
                        onDone = { navController.popBackStack() },
                        onInfo = { navController.navigate(ExerciseDetailRoute(it)) },
                        onCreate = { navController.navigate(ExerciseEditRoute()) },
                    )
                }
                composable<SessionDetailRoute> {
                    SessionDetailScreen(onBack = { navController.popBackStack() })
                }
                composable<ExerciseDetailRoute> {
                    ExerciseDetailScreen(
                        onBack = { navController.popBackStack() },
                        // Moving along a progression replaces the screen instead of stacking up.
                        onOpenExercise = { id ->
                            navController.navigate(ExerciseDetailRoute(id)) {
                                popUpTo<ExerciseDetailRoute> { inclusive = true }
                            }
                        },
                        onEdit = { navController.navigate(ExerciseEditRoute(it)) },
                    )
                }
                composable<ExerciseEditRoute> {
                    ExerciseEditScreen(
                        onClose = { navController.popBackStack() },
                        onSaved = { navController.popBackStack() },
                    )
                }
            }
        }
    }
}

private fun NavDestination?.isTopLevel(): Boolean =
    this == null || TopLevelDestination.entries.any { dest -> hierarchy.any { it.hasRoute(dest.route::class) } }

/** Shown above the tabs while a workout is running, so it's always one tap away. */
@Composable
private fun ResumeBar(name: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Timer, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
            Spacer(Modifier.width(Spacing.sm))
            Text(
                "$name in progress",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.weight(1f),
            )
            Text("Resume", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}

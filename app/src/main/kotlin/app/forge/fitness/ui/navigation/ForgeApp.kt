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
import androidx.compose.runtime.rememberCoroutineScope
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
import app.forge.fitness.feature.activity.ActivityEditScreen
import app.forge.fitness.feature.exercises.ExerciseDetailScreen
import app.forge.fitness.feature.food.FoodAddScreen
import app.forge.fitness.feature.goals.GoalsScreen
import app.forge.fitness.feature.food.FoodDayScreen
import app.forge.fitness.feature.food.FoodEditScreen
import app.forge.fitness.feature.food.NutritionTargetsScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.forge.fitness.feature.health.HealthScreen
import app.forge.fitness.feature.exercises.ExerciseEditScreen
import app.forge.fitness.feature.exercises.ExercisePickerScreen
import app.forge.fitness.feature.exercises.ExercisesScreen
import app.forge.fitness.feature.history.HistoryScreen
import app.forge.fitness.feature.history.SessionDetailScreen
import app.forge.fitness.feature.progress.BodyScreen
import app.forge.fitness.feature.progress.ExerciseProgressScreen
import app.forge.fitness.feature.progress.PhotoViewerScreen
import app.forge.fitness.feature.progress.PhotosScreen
import app.forge.fitness.feature.progress.ProgressScreen
import app.forge.fitness.feature.routines.ProgramsScreen
import app.forge.fitness.feature.routines.RoutineEditorScreen
import app.forge.fitness.feature.routines.RoutinesScreen
import app.forge.fitness.feature.settings.SettingsScreen
import app.forge.fitness.feature.today.TodayScreen
import app.forge.fitness.feature.workout.ActiveWorkoutScreen
import app.forge.fitness.ui.components.LocalAppUiScope
import app.forge.fitness.ui.components.LocalSnackbarHostState
import app.forge.fitness.ui.components.rememberHaptics
import app.forge.fitness.ui.theme.Spacing

@Composable
fun ForgeApp(
    activeWorkout: WorkoutSessionEntity?,
    activeWorkoutLoaded: Boolean,
    openWorkoutRequested: Boolean,
    onOpenWorkoutHandled: () -> Unit,
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val haptics = rememberHaptics()
    val snackbar = remember { SnackbarHostState() }
    val appUiScope = rememberCoroutineScope()
    val onTopLevel = currentDestination.isTopLevel()

    fun openWorkout() = navController.navigate(ActiveWorkoutRoute) { launchSingleTop = true }

    // Opened from the rest-timer notification: go to the workout once we know whether one
    // is running. The request is always cleared, so it can't fire later by surprise.
    LaunchedEffect(openWorkoutRequested, activeWorkoutLoaded) {
        if (openWorkoutRequested && activeWorkoutLoaded) {
            if (activeWorkout != null) openWorkout()
            onOpenWorkoutHandled()
        }
    }

    CompositionLocalProvider(LocalSnackbarHostState provides snackbar, LocalAppUiScope provides appUiScope) {
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
                        onOpenRoutines = { navController.navigate(RoutinesRoute) },
                        onOpenRoutine = { navController.navigate(RoutineEditorRoute(it)) },
                        onLogActivity = { navController.navigate(ActivityEditRoute()) },
                        onOpenHealth = { navController.navigate(HealthRoute) },
                        onOpenFood = { navController.navigate(FoodRoute()) },
                        onOpenGoals = { navController.navigate(GoalsRoute) },
                    )
                }
                composable<HistoryRoute> {
                    HistoryScreen(
                        onOpen = { navController.navigate(SessionDetailRoute(it)) },
                        onOpenActivity = { navController.navigate(ActivityEditRoute(it)) },
                        onLogActivity = { navController.navigate(ActivityEditRoute()) },
                    )
                }
                composable<ExercisesRoute> {
                    ExercisesScreen(
                        onOpen = { navController.navigate(ExerciseDetailRoute(it)) },
                        onCreate = { navController.navigate(ExerciseEditRoute()) },
                    )
                }
                composable<ProgressRoute> {
                    ProgressScreen(
                        onOpenExercise = { navController.navigate(ExerciseProgressRoute(it)) },
                        onOpenBody = { navController.navigate(BodyRoute) },
                        onOpenPhotos = { navController.navigate(PhotosRoute) },
                    )
                }
                composable<SettingsRoute> {
                    SettingsScreen(
                        onOpenHealth = { navController.navigate(HealthRoute) },
                        onOpenNutrition = { navController.navigate(NutritionTargetsRoute) },
                    )
                }
                composable<FoodRoute> {
                    FoodDayScreen(
                        onBack = { navController.popBackStack() },
                        onAdd = { day, meal -> navController.navigate(FoodAddRoute(day, meal.name)) },
                        onTargets = { navController.navigate(NutritionTargetsRoute) },
                    )
                }
                composable<FoodAddRoute> { entry ->
                    // A food created from here comes back as "newFoodId" so it opens ready to log.
                    val newFoodId by entry.savedStateHandle.getStateFlow<String?>(NEW_FOOD_ID, null).collectAsStateWithLifecycle()
                    FoodAddScreen(
                        onBack = { navController.popBackStack() },
                        onCreateFood = { barcode -> navController.navigate(FoodEditRoute(barcode = barcode)) },
                        onEditFood = { id -> navController.navigate(FoodEditRoute(foodId = id)) },
                        newFoodId = newFoodId,
                        onNewFoodHandled = { entry.savedStateHandle[NEW_FOOD_ID] = null },
                    )
                }
                composable<FoodEditRoute> {
                    FoodEditScreen(
                        onClose = { navController.popBackStack() },
                        onSaved = { id ->
                            navController.previousBackStackEntry?.savedStateHandle?.set(NEW_FOOD_ID, id)
                            navController.popBackStack()
                        },
                    )
                }
                composable<GoalsRoute> { GoalsScreen(onBack = { navController.popBackStack() }) }
                composable<NutritionTargetsRoute> {
                    NutritionTargetsScreen(
                        onBack = { navController.popBackStack() },
                        onOpenBody = { navController.navigate(BodyRoute) },
                    )
                }
                composable<ActivityEditRoute> { ActivityEditScreen(onClose = { navController.popBackStack() }) }
                composable<HealthRoute> { HealthScreen(onBack = { navController.popBackStack() }) }

                composable<ActiveWorkoutRoute>(
                    enterTransition = { slideInHorizontally(tween(220)) { it / 6 } + fadeIn(tween(220)) },
                    popExitTransition = { slideOutHorizontally(tween(180)) { it / 6 } + fadeOut(tween(180)) },
                ) {
                    ActiveWorkoutScreen(
                        onBack = { navController.popBackStack() },
                        onAddExercises = { navController.navigate(ExercisePickerRoute(sessionId = it)) },
                        onOpenExercise = { navController.navigate(ExerciseDetailRoute(it)) },
                        onFinished = { id ->
                            navController.navigate(SessionDetailRoute(id, justFinished = true)) {
                                popUpTo(ActiveWorkoutRoute) { inclusive = true }
                            }
                        },
                    )
                }
                composable<ExercisePickerRoute> { entry ->
                    val picker = entry.toRoute<ExercisePickerRoute>()
                    ExercisePickerScreen(
                        sessionId = picker.sessionId,
                        routineId = picker.routineId,
                        onDone = { navController.popBackStack() },
                        onInfo = { navController.navigate(ExerciseDetailRoute(it)) },
                        onCreate = { navController.navigate(ExerciseEditRoute()) },
                    )
                }
                composable<SessionDetailRoute> {
                    SessionDetailScreen(
                        onBack = { navController.popBackStack() },
                        onOpenRoutine = { navController.navigate(RoutineEditorRoute(it)) },
                    )
                }
                composable<RoutinesRoute> {
                    RoutinesScreen(
                        onBack = { navController.popBackStack() },
                        onEdit = { navController.navigate(RoutineEditorRoute(it)) },
                        onBrowsePrograms = { navController.navigate(ProgramsRoute) },
                        onWorkoutStarted = ::openWorkout,
                    )
                }
                composable<RoutineEditorRoute> {
                    RoutineEditorScreen(
                        onBack = { navController.popBackStack() },
                        onAddExercises = { navController.navigate(ExercisePickerRoute(routineId = it)) },
                        onOpenExercise = { navController.navigate(ExerciseDetailRoute(it)) },
                        onWorkoutStarted = ::openWorkout,
                    )
                }
                composable<ProgramsRoute> {
                    ProgramsScreen(
                        onBack = { navController.popBackStack() },
                        onInstalled = { navController.popBackStack() },
                    )
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
                        onProgress = { navController.navigate(ExerciseProgressRoute(it)) },
                    )
                }
                composable<ExerciseProgressRoute> {
                    ExerciseProgressScreen(
                        onBack = { navController.popBackStack() },
                        onOpenDetails = { navController.navigate(ExerciseDetailRoute(it)) },
                    )
                }
                composable<BodyRoute> { BodyScreen(onBack = { navController.popBackStack() }) }
                composable<PhotosRoute> {
                    PhotosScreen(
                        onBack = { navController.popBackStack() },
                        onOpen = { id, other -> navController.navigate(PhotoViewerRoute(id, other)) },
                    )
                }
                composable<PhotoViewerRoute> { PhotoViewerScreen(onBack = { navController.popBackStack() }) }
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

private const val NEW_FOOD_ID = "newFoodId"

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

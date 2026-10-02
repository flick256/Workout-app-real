package app.forge.fitness.ui.navigation

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import app.forge.fitness.feature.exercises.ExercisesScreen
import app.forge.fitness.feature.history.HistoryScreen
import app.forge.fitness.feature.progress.ProgressScreen
import app.forge.fitness.feature.settings.SettingsScreen
import app.forge.fitness.feature.today.TodayScreen
import app.forge.fitness.ui.components.rememberHaptics

@Composable
fun ForgeApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val haptics = rememberHaptics()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
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
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = TodayRoute,
            modifier = Modifier.padding(innerPadding),
            enterTransition = { fadeIn(tween(180)) },
            exitTransition = { fadeOut(tween(120)) },
        ) {
            composable<TodayRoute> { TodayScreen() }
            composable<HistoryRoute> { HistoryScreen() }
            composable<ExercisesRoute> { ExercisesScreen() }
            composable<ProgressRoute> { ProgressScreen() }
            composable<SettingsRoute> { SettingsScreen() }
        }
    }
}

package app.forge.fitness.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.serialization.Serializable

// Type-safe navigation routes. Screens with arguments become data classes later.
@Serializable data object TodayRoute
@Serializable data object HistoryRoute
@Serializable data object ExercisesRoute
@Serializable data object ProgressRoute
@Serializable data object SettingsRoute

// Full-screen routes (no bottom bar).
@Serializable data object ActiveWorkoutRoute
/** Pick exercises for a workout ([sessionId]) or a routine ([routineId]). */
@Serializable data class ExercisePickerRoute(val sessionId: String? = null, val routineId: String? = null)
@Serializable data object RoutinesRoute
@Serializable data class RoutineEditorRoute(val routineId: String)
@Serializable data object ProgramsRoute
@Serializable data class ExerciseProgressRoute(val exerciseId: String)
@Serializable data object BodyRoute
@Serializable data object PhotosRoute
@Serializable data class PhotoViewerRoute(val photoId: String, val compareWith: String? = null)
@Serializable data class SessionDetailRoute(val sessionId: String, val justFinished: Boolean = false)
@Serializable data class ExerciseDetailRoute(val exerciseId: String)
/** [exerciseId] null = create a new custom exercise. */
@Serializable data class ExerciseEditRoute(val exerciseId: String? = null)

enum class TopLevelDestination(
    val route: Any,
    val label: String,
    val selectedIcon: ImageVector,
    val icon: ImageVector,
) {
    TODAY(TodayRoute, "Today", Icons.Rounded.Home, Icons.Outlined.Home),
    HISTORY(HistoryRoute, "History", Icons.Rounded.History, Icons.Outlined.History),
    EXERCISES(ExercisesRoute, "Exercises", Icons.Rounded.FitnessCenter, Icons.Outlined.FitnessCenter),
    PROGRESS(ProgressRoute, "Progress", Icons.Rounded.Insights, Icons.Outlined.Insights),
    SETTINGS(SettingsRoute, "Settings", Icons.Rounded.Settings, Icons.Outlined.Settings),
}

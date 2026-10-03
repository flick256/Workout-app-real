package app.forge.fitness.ui.components

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.CoroutineScope

/**
 * One snackbar host for the whole app, so an "Undo" survives navigating away
 * (e.g. delete a workout in its detail screen, get the undo on the History list).
 */
val LocalSnackbarHostState = staticCompositionLocalOf<SnackbarHostState> {
    error("No SnackbarHostState provided")
}

/**
 * A coroutine scope that lives as long as the app's UI. Use it for an undo snackbar
 * shown just before leaving a screen, which a screen's own scope would cancel.
 */
val LocalAppUiScope = staticCompositionLocalOf<CoroutineScope> {
    error("No app UI scope provided")
}

/** Shows "[message]  UNDO" and runs [onUndo] if it's tapped. */
suspend fun SnackbarHostState.showUndo(message: String, onUndo: () -> Unit) {
    currentSnackbarData?.dismiss()
    val result = showSnackbar(message, actionLabel = "Undo", withDismissAction = true, duration = SnackbarDuration.Short)
    if (result == SnackbarResult.ActionPerformed) onUndo()
}

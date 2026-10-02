package app.forge.fitness.ui.components

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * One snackbar host for the whole app, so an "Undo" survives navigating away
 * (e.g. delete a workout in its detail screen, get the undo on the History list).
 */
val LocalSnackbarHostState = staticCompositionLocalOf<SnackbarHostState> {
    error("No SnackbarHostState provided")
}

/** Shows "[message]  UNDO" and runs [onUndo] if it's tapped. */
suspend fun SnackbarHostState.showUndo(message: String, onUndo: () -> Unit) {
    currentSnackbarData?.dismiss()
    val result = showSnackbar(message, actionLabel = "Undo", withDismissAction = true, duration = SnackbarDuration.Short)
    if (result == SnackbarResult.ActionPerformed) onUndo()
}

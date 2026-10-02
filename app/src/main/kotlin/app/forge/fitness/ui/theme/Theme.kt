package app.forge.fitness.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.forge.domain.model.ThemeMode

private val DarkColors = darkColorScheme(
    primary = Volt,
    onPrimary = Ink,
    primaryContainer = Color(0xFF2A3500),
    onPrimaryContainer = Volt,
    secondary = Aqua,
    onSecondary = Ink,
    tertiary = Ember,
    onTertiary = Ink,
    background = DarkBackground,
    onBackground = DarkOnSurface,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceContainerHigh,
    onSurfaceVariant = DarkOnSurfaceVariant,
    surfaceContainerLowest = DarkBackground,
    surfaceContainerLow = DarkSurface,
    surfaceContainer = DarkSurfaceContainer,
    surfaceContainerHigh = DarkSurfaceContainerHigh,
    surfaceContainerHighest = DarkSurfaceContainerHighest,
    outline = DarkOutline,
    outlineVariant = DarkSurfaceContainerHighest,
    error = DarkError,
    onError = Ink,
)

private val LightColors = lightColorScheme(
    primary = LightPrimary,
    onPrimary = LightSurface,
    primaryContainer = Volt,
    onPrimaryContainer = Ink,
    secondary = Color(0xFF00695F),
    tertiary = Color(0xFFB4441A),
    background = LightBackground,
    onBackground = LightOnSurface,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceContainerHigh,
    onSurfaceVariant = LightOnSurfaceVariant,
    surfaceContainerLowest = LightSurface,
    surfaceContainerLow = LightBackground,
    surfaceContainer = LightSurfaceContainer,
    surfaceContainerHigh = LightSurfaceContainerHigh,
    surfaceContainerHighest = LightSurfaceContainerHighest,
    outline = LightOutline,
    error = LightError,
)

private val ForgeShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(Sizes.chipRadius),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(Sizes.cardRadius),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun ForgeTheme(
    themeMode: ThemeMode = ThemeMode.DARK,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        typography = ForgeTypography,
        shapes = ForgeShapes,
        content = content,
    )
}

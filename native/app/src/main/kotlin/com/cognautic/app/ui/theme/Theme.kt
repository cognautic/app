package com.cognautic.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val SquaryShapes = Shapes(
    extraSmall = RoundedCornerShape(0.dp),
    small = RoundedCornerShape(0.dp),
    medium = RoundedCornerShape(0.dp),
    large = RoundedCornerShape(0.dp),
    extraLarge = RoundedCornerShape(0.dp)
)

private val DarkColorScheme = darkColorScheme(
    primary = AccentPrimary,
    onPrimary = TextInverse,
    primaryContainer = SurfaceVariant,
    onPrimaryContainer = TextPrimary,
    secondary = AccentSecondary,
    onSecondary = TextInverse,
    secondaryContainer = SurfaceElevated,
    onSecondaryContainer = TextSecondary,
    tertiary = AccentAccent,
    onTertiary = TextInverse,
    tertiaryContainer = Surface,
    onTertiaryContainer = TextSecondary,
    error = AccentError,
    onError = TextInverse,
    errorContainer = AccentError.copy(alpha = 0.15f),
    onErrorContainer = AccentError,
    background = Background,
    onBackground = TextPrimary,
    surface = Surface,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceVariant,
    onSurfaceVariant = TextSecondary,
    outline = Border,
    outlineVariant = BorderLight,
    inverseSurface = SurfaceElevated,
    inverseOnSurface = TextPrimary,
    inversePrimary = AccentPrimary.copy(alpha = 0.8f),
    scrim = Color.Black,
    surfaceTint = AccentPrimary,
)

// We make LightColorScheme also a terminal/gray theme, just slightly different tones or same to stay fully dark-mode minimal terminal.
// Let's make it a clean medium-gray terminal design.
private val LightColorScheme = lightColorScheme(
    primary = AccentPrimary,
    onPrimary = TextInverse,
    primaryContainer = SurfaceVariant,
    onPrimaryContainer = TextPrimary,
    secondary = AccentSecondary,
    onSecondary = TextInverse,
    secondaryContainer = SurfaceElevated,
    onSecondaryContainer = TextSecondary,
    tertiary = AccentAccent,
    onTertiary = TextInverse,
    tertiaryContainer = Surface,
    onTertiaryContainer = TextSecondary,
    error = AccentError,
    onError = TextInverse,
    errorContainer = AccentError.copy(alpha = 0.15f),
    onErrorContainer = AccentError,
    background = BackgroundAlt,
    onBackground = TextPrimary,
    surface = Surface,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceVariant,
    onSurfaceVariant = TextSecondary,
    outline = Border,
    outlineVariant = BorderLight,
    inverseSurface = SurfaceElevated,
    inverseOnSurface = TextPrimary,
    inversePrimary = AccentPrimary.copy(alpha = 0.8f),
    scrim = Color.Black,
    surfaceTint = AccentPrimary,
)

@Composable
fun CognauticTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = SquaryShapes,
        content = content
    )
}
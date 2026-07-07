package com.cognautic.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = AccentPrimary,
    onPrimary = TextInverse,
    primaryContainer = AccentPrimary.copy(alpha = 0.12f),
    onPrimaryContainer = AccentPrimary,
    secondary = AccentSecondary,
    onSecondary = TextInverse,
    secondaryContainer = AccentSecondary.copy(alpha = 0.12f),
    onSecondaryContainer = AccentSecondary,
    tertiary = AccentAccent,
    onTertiary = TextInverse,
    tertiaryContainer = AccentAccent.copy(alpha = 0.12f),
    onTertiaryContainer = AccentAccent,
    error = AccentError,
    onError = TextInverse,
    errorContainer = AccentError.copy(alpha = 0.12f),
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

private val LightColorScheme = lightColorScheme(
    primary = AccentPrimary.copy(alpha = 0.8f),
    onPrimary = TextInverse,
    primaryContainer = AccentPrimary.copy(alpha = 0.12f),
    onPrimaryContainer = AccentPrimary.copy(alpha = 0.8f),
    secondary = AccentSecondary.copy(alpha = 0.8f),
    onSecondary = TextInverse,
    secondaryContainer = AccentSecondary.copy(alpha = 0.12f),
    onSecondaryContainer = AccentSecondary.copy(alpha = 0.8f),
    tertiary = AccentAccent.copy(alpha = 0.8f),
    onTertiary = TextInverse,
    tertiaryContainer = AccentAccent.copy(alpha = 0.12f),
    onTertiaryContainer = AccentAccent.copy(alpha = 0.8f),
    error = AccentError,
    onError = TextInverse,
    errorContainer = AccentError.copy(alpha = 0.12f),
    onErrorContainer = AccentError,
    background = Color(0xFFE0E0E0),
    onBackground = TextPrimaryLight,
    surface = Color(0xFFFFFFFF),
    onSurface = TextPrimaryLight,
    surfaceVariant = Color(0xFFE0E0E0),
    onSurfaceVariant = TextSecondaryLight,
    outline = BorderLight.copy(alpha = 0.6f),
    outlineVariant = BorderLight.copy(alpha = 0.4f),
    inverseSurface = Surface,
    inverseOnSurface = TextPrimary,
    inversePrimary = AccentPrimary,
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
        content = content
    )
}
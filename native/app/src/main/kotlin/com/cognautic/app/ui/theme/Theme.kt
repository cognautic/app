package com.cognautic.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFA8C8FF),
    onPrimary = Color(0xFF07305F),
    primaryContainer = Color(0xFF254777),
    onPrimaryContainer = Color(0xFFD6E3FF),
    secondary = Color(0xFFBEC6D4),
    onSecondary = Color(0xFF28313E),
    secondaryContainer = Color(0xFF3E4755),
    onSecondaryContainer = Color(0xFFDAE2F1),
    tertiary = Color(0xFFDDBCE0),
    onTertiary = Color(0xFF402843),
    error = Color(0xFFFFB4AB),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = BackgroundDark,
    onBackground = TextPrimary,
    surface = SurfaceDark,
    onSurface = TextPrimary,
    surfaceVariant = Color(0xFF43474E),
    onSurfaceVariant = TextSecondary,
    outline = Color(0xFF8D9199)
)

private val LightColorScheme = lightColorScheme(
    primary = PrimaryAccent,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6E3FF),
    onPrimaryContainer = Color(0xFF001B3E),
    secondary = SecondaryAccent,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDAE2F1),
    onSecondaryContainer = Color(0xFF131C28),
    tertiary = Color(0xFF705575),
    onTertiary = Color.White,
    error = Color(0xFFBA1A1A),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF8F9FF),
    onBackground = Color(0xFF191C20),
    surface = Color(0xFFF8F9FF),
    onSurface = Color(0xFF191C20),
    surfaceVariant = Color(0xFFE0E2EA),
    onSurfaceVariant = Color(0xFF43474E),
    outline = Color(0xFF74777F)
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

package com.ordia.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightOrdiaScheme = lightColorScheme(
    primary = LightOnBackground,
    onPrimary = LightBackground,
    secondary = LightOnBackground,
    onSecondary = LightBackground,
    background = LightBackground,
    onBackground = LightOnBackground,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurface,
    onSurfaceVariant = LightMuted,
    outline = LightOutline,
    error = OrdiaAlert,
)

private val DarkOrdiaScheme = darkColorScheme(
    primary = DarkOnBackground,
    onPrimary = DarkBackground,
    secondary = DarkOnBackground,
    onSecondary = DarkBackground,
    background = DarkBackground,
    onBackground = DarkOnBackground,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurface,
    onSurfaceVariant = DarkMuted,
    outline = DarkOutline,
    error = OrdiaAlert,
)

@Composable
fun NotepadTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkOrdiaScheme else LightOrdiaScheme,
        typography = OrdiaTypography,
        content = content,
    )
}

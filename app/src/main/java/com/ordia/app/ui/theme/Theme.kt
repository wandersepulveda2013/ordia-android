package com.ordia.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightOrdiaScheme = lightColorScheme(
    primary = TextPrimary,
    onPrimary = PageWhite,
    secondary = TextSecondary,
    onSecondary = PageWhite,
    background = PageWhite,
    onBackground = TextPrimary,
    surface = PageWhite,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceGray,
    onSurfaceVariant = TextSecondary,
    outline = OutlineGray,
    outlineVariant = OutlineGray,
    error = AccentAlert,
    onError = PageWhite
)

private val DarkOrdiaScheme = darkColorScheme(
    primary = DarkTextPrimary,
    onPrimary = DarkPage,
    secondary = DarkTextSecondary,
    onSecondary = DarkPage,
    background = DarkPage,
    onBackground = DarkTextPrimary,
    surface = DarkPage,
    onSurface = DarkTextPrimary,
    surfaceVariant = DarkSurfaceGray,
    onSurfaceVariant = DarkTextSecondary,
    outline = DarkOutlineGray,
    outlineVariant = DarkOutlineGray,
    error = DarkAccentAlert,
    onError = DarkPage
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

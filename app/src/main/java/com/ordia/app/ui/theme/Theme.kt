package com.ordia.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

/**
 * Ordía Theme
 * A restrained, minimalist black-and-white palette with semantic accents.
 */
private val LightColors = lightColorScheme(
    primary = OrdiaBlack,
    onPrimary = OrdiaWhite,
    secondary = OrdiaGray800,
    onSecondary = OrdiaWhite,
    background = OrdiaWhite,
    onBackground = OrdiaBlack,
    surface = OrdiaWhite,
    onSurface = OrdiaBlack,
    surfaceVariant = OrdiaGray100,
    onSurfaceVariant = OrdiaGray700,
    outline = OrdiaGray300,
    error = OrdiaPriority,
    onError = OrdiaWhite
)

private val DarkColors = darkColorScheme(
    primary = OrdiaWhite,
    onPrimary = OrdiaBlack,
    secondary = OrdiaGray300,
    onSecondary = OrdiaBlack,
    background = OrdiaBlack,
    onBackground = OrdiaWhite,
    surface = OrdiaBlack,
    onSurface = OrdiaWhite,
    surfaceVariant = OrdiaGray900,
    onSurfaceVariant = OrdiaGray400,
    outline = OrdiaGray800,
    error = OrdiaPriority,
    onError = OrdiaBlack
)

@Composable
fun NotepadTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = OrdiaTypography,
        content = content,
    )
}

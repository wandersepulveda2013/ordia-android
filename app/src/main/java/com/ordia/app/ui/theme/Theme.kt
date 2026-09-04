package com.ordia.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val OrdiaLightColorScheme = lightColorScheme(
    primary = OrdiaBlack,
    onPrimary = OrdiaWhite,
    secondary = OrdiaTextSecondary,
    onSecondary = OrdiaWhite,
    background = OrdiaBackground,
    onBackground = OrdiaTextPrimary,
    surface = OrdiaSurface,
    onSurface = OrdiaTextPrimary,
    surfaceVariant = OrdiaSurfaceVariant,
    onSurfaceVariant = OrdiaTextSecondary,
    outline = OrdiaBorder,
    outlineVariant = OrdiaBorderSubtle,
    error = OrdiaAccentPriority,
    onError = OrdiaWhite,
)

private val OrdiaDarkColorScheme = darkColorScheme(
    primary = OrdiaWhite,
    onPrimary = OrdiaBlack,
    secondary = OrdiaDarkTextSecondary,
    onSecondary = OrdiaBlack,
    background = OrdiaDarkBackground,
    onBackground = OrdiaDarkTextPrimary,
    surface = OrdiaDarkSurface,
    onSurface = OrdiaDarkTextPrimary,
    surfaceVariant = OrdiaDarkSurfaceVariant,
    onSurfaceVariant = OrdiaDarkTextSecondary,
    outline = OrdiaDarkBorder,
    outlineVariant = OrdiaDarkBorderSubtle,
    error = OrdiaAccentPriority,
    onError = OrdiaWhite,
)

@Composable
fun NotepadTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) OrdiaDarkColorScheme else OrdiaLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = OrdiaTypography,
        content = content,
    )
}

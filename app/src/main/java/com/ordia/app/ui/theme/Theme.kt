package com.ordia.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightPaper = lightColorScheme(
    primary = Ink,
    onPrimary = Page,
    secondary = Ink,
    onSecondary = Page,
    background = Page,
    onBackground = Ink,
    surface = Page,
    onSurface = Ink,
    surfaceVariant = SoftPaper,
    onSurfaceVariant = InkMuted,
    outline = Rule,
    error = SemanticAlert,
    onError = Page,
    tertiary = SemanticFocus,
    onTertiary = Page,
)

private val DarkPaper = darkColorScheme(
    primary = PageOnDark,
    onPrimary = DarkInk,
    secondary = PageOnDark,
    onSecondary = DarkInk,
    background = DarkInk,
    onBackground = PageOnDark,
    surface = DarkInk,
    onSurface = PageOnDark,
    surfaceVariant = DarkInkRaised,
    onSurfaceVariant = PageMuted,
    outline = DarkRule,
    error = SemanticAlertDark,
    onError = DarkInk,
    tertiary = SemanticFocusDark,
    onTertiary = DarkInk,
)

@Composable
fun NotepadTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkPaper else LightPaper,
        typography = OrdiaTypography,
        content = content,
    )
}

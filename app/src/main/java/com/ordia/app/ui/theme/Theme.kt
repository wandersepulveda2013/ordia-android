package com.ordia.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

/**
 * A restrained, paper-and-ink palette. No accent colors: the only chroma is the
 * page itself, so the writing stays the focus.
 */
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
)

@Composable
fun NotepadTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkPaper else LightPaper,
        typography = Typography,
        content = content,
    )
}

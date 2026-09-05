package com.ordia.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

/**
 * A restrained, paper-and-ink palette.
 * Base: white, black, grays.
 * Semantic colors exist but are contained.
 */
private val LightPaper = lightColorScheme(
    primary = Ink,
    onPrimary = Page,
    secondary = AccentSemantic,
    onSecondary = Page,
    tertiary = SuccessSemantic,
    onTertiary = Page,
    error = ErrorSemantic,
    onError = Page,
    background = Page,
    onBackground = Ink,
    surface = Page,
    onSurface = Ink,
    surfaceVariant = SoftPaper,
    onSurfaceVariant = InkMuted,
    outline = Rule,
    surfaceContainer = SoftPaper
)

private val DarkPaper = darkColorScheme(
    primary = PageOnDark,
    onPrimary = DarkInk,
    secondary = AccentSemanticDark,
    onSecondary = DarkInk,
    tertiary = SuccessSemanticDark,
    onTertiary = DarkInk,
    error = ErrorSemanticDark,
    onError = DarkInk,
    background = DarkInk,
    onBackground = PageOnDark,
    surface = DarkInk,
    onSurface = PageOnDark,
    surfaceVariant = DarkInkRaised,
    onSurfaceVariant = PageMuted,
    outline = DarkRule,
    surfaceContainer = DarkInkRaised
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

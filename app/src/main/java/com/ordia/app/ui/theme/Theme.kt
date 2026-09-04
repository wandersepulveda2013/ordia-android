package com.ordia.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp

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
    val base = Typography()
    val type = Typography(
        bodyLarge = base.bodyLarge.copy(fontSize = 17.sp, lineHeight = 28.sp),
        bodyMedium = base.bodyMedium.copy(fontSize = 16.sp, lineHeight = 26.sp),
        titleLarge = base.titleLarge.copy(fontFamily = FontFamily.Serif, fontSize = 28.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
        titleMedium = base.titleMedium.copy(fontFamily = FontFamily.Serif, fontSize = 22.sp),
        titleSmall = base.titleSmall.copy(fontFamily = FontFamily.Serif),
        labelMedium = base.labelMedium.copy(fontSize = 12.sp),
        labelSmall = base.labelSmall.copy(fontSize = 11.sp),
    )
    MaterialTheme(
        colorScheme = if (darkTheme) DarkPaper else LightPaper,
        typography = type,
        content = content,
    )
}

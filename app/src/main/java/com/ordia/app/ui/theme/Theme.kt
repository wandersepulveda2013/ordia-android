package com.ordia.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp

val LocalSpacing = staticCompositionLocalOf { Spacing() }

// Semantic colors composition local
data class OrdiaSemanticColors(
    val priority: Color = SemanticPriority,
    val alert: Color = SemanticAlert,
    val success: Color = SemanticSuccess,
    val focus: Color = SemanticFocus,
    val calendar: Color = SemanticCalendar,
    val automation: Color = SemanticAutomation,
    val guardian: Color = SemanticGuardian,
)

val LocalSemanticColors = staticCompositionLocalOf { OrdiaSemanticColors() }

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
        bodySmall = base.bodySmall.copy(fontSize = 14.sp, lineHeight = 20.sp),

        // Serifs solely for titles as requested for personality and clear hierarchy
        headlineLarge = base.headlineLarge.copy(fontFamily = FontFamily.Serif, fontSize = 32.sp, lineHeight = 40.sp),
        headlineMedium = base.headlineMedium.copy(fontFamily = FontFamily.Serif, fontSize = 28.sp, lineHeight = 36.sp),
        headlineSmall = base.headlineSmall.copy(fontFamily = FontFamily.Serif, fontSize = 24.sp, lineHeight = 32.sp),

        titleLarge = base.titleLarge.copy(fontFamily = FontFamily.Serif, fontSize = 22.sp, lineHeight = 28.sp),
        titleMedium = base.titleMedium.copy(fontFamily = FontFamily.Serif, fontSize = 18.sp, lineHeight = 24.sp),
        titleSmall = base.titleSmall.copy(fontFamily = FontFamily.Serif, fontSize = 16.sp, lineHeight = 24.sp),

        labelLarge = base.labelLarge.copy(fontSize = 14.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium),
        labelMedium = base.labelMedium.copy(fontSize = 12.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium),
        labelSmall = base.labelSmall.copy(fontSize = 11.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium),
    )

    val semanticColors = OrdiaSemanticColors()
    val spacing = Spacing()

    CompositionLocalProvider(
        LocalSemanticColors provides semanticColors,
        LocalSpacing provides spacing
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkPaper else LightPaper,
            typography = type,
            content = content,
        )
    }
}

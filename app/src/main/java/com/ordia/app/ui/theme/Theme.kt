package com.ordia.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * A restrained, monochrome palette. The design avoids excessive colors,
 * relying on careful grays and high contrast for focus.
 */
private val LightPaper = lightColorScheme(
    primary = Ink,
    onPrimary = Page,
    secondary = SemanticFocus,
    onSecondary = Page,
    tertiary = SemanticGuardian,
    onTertiary = Page,
    background = Page,
    onBackground = Ink,
    surface = Page,
    onSurface = Ink,
    surfaceVariant = SoftPaper,
    onSurfaceVariant = InkMuted,
    outline = Rule,
    error = SemanticAlert,
    onError = Page,
)

private val DarkPaper = darkColorScheme(
    primary = PageOnDark,
    onPrimary = DarkInk,
    secondary = FocusDark,
    onSecondary = DarkInk,
    tertiary = SemanticGuardian,
    onTertiary = DarkInk,
    background = DarkInk,
    onBackground = PageOnDark,
    surface = DarkInk,
    onSurface = PageOnDark,
    surfaceVariant = DarkInkRaised,
    onSurfaceVariant = PageMuted,
    outline = DarkRule,
    error = AlertDark,
    onError = DarkInk,
)

@Composable
fun NotepadTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    // Generous line heights, letter spacing, and lighter font weights
    // Avoid heavy SemiBold/Bold usage.
    val base = Typography()
    val type = Typography(
        displayLarge = base.displayLarge.copy(fontWeight = FontWeight.Light, letterSpacing = (-0.25).sp),
        displayMedium = base.displayMedium.copy(fontWeight = FontWeight.Light),
        displaySmall = base.displaySmall.copy(fontWeight = FontWeight.Normal),
        headlineLarge = base.headlineLarge.copy(fontWeight = FontWeight.Normal, lineHeight = 40.sp),
        headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.Normal, lineHeight = 36.sp),
        headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.Medium, lineHeight = 32.sp),
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.Medium, fontSize = 22.sp, lineHeight = 28.sp),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.Medium, fontSize = 18.sp, lineHeight = 24.sp, letterSpacing = 0.1.sp),
        titleSmall = base.titleSmall.copy(fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
        bodyLarge = base.bodyLarge.copy(fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.5.sp),
        bodyMedium = base.bodyMedium.copy(fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.25.sp),
        bodySmall = base.bodySmall.copy(fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.4.sp),
        labelLarge = base.labelLarge.copy(fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
        labelMedium = base.labelMedium.copy(fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp),
        labelSmall = base.labelSmall.copy(fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp),
    )
    MaterialTheme(
        colorScheme = if (darkTheme) DarkPaper else LightPaper,
        typography = type,
        content = content,
    )
}

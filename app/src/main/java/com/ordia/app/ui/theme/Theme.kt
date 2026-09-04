package com.ordia.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
    error = SemanticPriorityHigh,
    primaryContainer = SoftPaper,
    onPrimaryContainer = Ink,
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
    error = SemanticPriorityHigh,
    primaryContainer = DarkInkRaised,
    onPrimaryContainer = PageOnDark,
)

val OrdiaShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp)
)

@Composable
fun NotepadTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val base = Typography()

    // Updated typography for better legibility and hierarchy
    val type = Typography(
        displayLarge = base.displayLarge.copy(fontSize = 32.sp, fontWeight = FontWeight.Bold, lineHeight = 40.sp),
        displayMedium = base.displayMedium.copy(fontSize = 28.sp, fontWeight = FontWeight.Bold, lineHeight = 36.sp),
        displaySmall = base.displaySmall.copy(fontSize = 24.sp, fontWeight = FontWeight.SemiBold, lineHeight = 32.sp),

        titleLarge = base.titleLarge.copy(fontSize = 22.sp, fontWeight = FontWeight.SemiBold, lineHeight = 28.sp),
        titleMedium = base.titleMedium.copy(fontSize = 18.sp, fontWeight = FontWeight.Medium, lineHeight = 24.sp),
        titleSmall = base.titleSmall.copy(fontSize = 16.sp, fontWeight = FontWeight.Medium, lineHeight = 24.sp),

        bodyLarge = base.bodyLarge.copy(fontSize = 16.sp, fontWeight = FontWeight.Normal, lineHeight = 24.sp),
        bodyMedium = base.bodyMedium.copy(fontSize = 14.sp, fontWeight = FontWeight.Normal, lineHeight = 20.sp),
        bodySmall = base.bodySmall.copy(fontSize = 12.sp, fontWeight = FontWeight.Normal, lineHeight = 16.sp),

        labelLarge = base.labelLarge.copy(fontSize = 14.sp, fontWeight = FontWeight.Medium, lineHeight = 20.sp),
        labelMedium = base.labelMedium.copy(fontSize = 12.sp, fontWeight = FontWeight.Medium, lineHeight = 16.sp),
        labelSmall = base.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Medium, lineHeight = 16.sp),
    )

    MaterialTheme(
        colorScheme = if (darkTheme) DarkPaper else LightPaper,
        typography = type,
        shapes = OrdiaShapes,
        content = content,
    )
}

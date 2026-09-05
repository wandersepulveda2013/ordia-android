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
 * Ordía Design System Theme
 * Minimalist monochromatic base with dense hierarchy.
 */
private val LightColors = lightColorScheme(
    primary = OnBackgroundLight,
    onPrimary = BackgroundLight,
    secondary = OnBackgroundLight,
    onSecondary = BackgroundLight,
    background = BackgroundLight,
    onBackground = OnBackgroundLight,
    surface = SurfaceLight,
    onSurface = OnBackgroundLight,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = OnBackgroundMutedLight,
    outline = OutlineLight,
    error = AccentPriority,
    primaryContainer = AccentFocus,
)

private val DarkColors = darkColorScheme(
    primary = OnBackgroundDark,
    onPrimary = BackgroundDark,
    secondary = OnBackgroundDark,
    onSecondary = BackgroundDark,
    background = BackgroundDark,
    onBackground = OnBackgroundDark,
    surface = SurfaceDark,
    onSurface = OnBackgroundDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = OnBackgroundMutedDark,
    outline = OutlineDark,
    error = AccentPriority,
    primaryContainer = AccentFocus,
)

@Composable
fun NotepadTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val base = Typography()
    val type = Typography(
        bodyLarge = base.bodyLarge.copy(fontSize = 17.sp, lineHeight = 28.sp),
        bodyMedium = base.bodyMedium.copy(fontSize = 15.sp, lineHeight = 24.sp),
        titleLarge = base.titleLarge.copy(fontFamily = FontFamily.Serif, fontSize = 28.sp, lineHeight = 36.sp),
        titleMedium = base.titleMedium.copy(fontFamily = FontFamily.Serif, fontSize = 22.sp, lineHeight = 28.sp),
        titleSmall = base.titleSmall.copy(fontFamily = FontFamily.Serif, fontSize = 18.sp, lineHeight = 24.sp),
        labelLarge = base.labelLarge.copy(fontSize = 14.sp, letterSpacing = 0.sp),
        labelMedium = base.labelMedium.copy(fontSize = 12.sp, letterSpacing = 0.sp),
        labelSmall = base.labelSmall.copy(fontSize = 11.sp, letterSpacing = 0.sp),
    )
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = type,
        content = content,
    )
}

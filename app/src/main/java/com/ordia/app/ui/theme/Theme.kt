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
 * A minimalist, adaptive palette. High contrast base with semantic accents.
 */
private val LightPalette = lightColorScheme(
    primary = OrdiaBlack,
    onPrimary = OrdiaWhite,
    secondary = OrdiaFocus,
    onSecondary = OrdiaWhite,
    background = OrdiaWhite,
    onBackground = OrdiaBlack,
    surface = OrdiaSurfaceLight,
    onSurface = OrdiaBlack,
    surfaceVariant = OrdiaSurfaceVariantLight,
    onSurfaceVariant = OrdiaGray,
    outline = OrdiaBorderLight,
    error = OrdiaPriority,
    onError = OrdiaWhite,
)

private val DarkPalette = darkColorScheme(
    primary = OrdiaDarkWhite,
    onPrimary = OrdiaDarkBlack,
    secondary = OrdiaFocus,
    onSecondary = OrdiaDarkWhite,
    background = OrdiaDarkBlack,
    onBackground = OrdiaDarkWhite,
    surface = OrdiaSurfaceDark,
    onSurface = OrdiaDarkWhite,
    surfaceVariant = OrdiaSurfaceVariantDark,
    onSurfaceVariant = OrdiaDarkGray,
    outline = OrdiaBorderDark,
    error = OrdiaPriority,
    onError = OrdiaDarkBlack,
)

@Composable
fun NotepadTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val base = Typography()

    // Ordía Typography: Clean, highly legible, strong hierarchy
    val type = Typography(
        displayLarge = base.displayLarge.copy(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 42.sp),
        displayMedium = base.displayMedium.copy(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 36.sp),
        displaySmall = base.displaySmall.copy(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 32.sp),

        titleLarge = base.titleLarge.copy(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 28.sp),
        titleMedium = base.titleMedium.copy(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 18.sp, lineHeight = 26.sp),
        titleSmall = base.titleSmall.copy(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 24.sp),

        bodyLarge = base.bodyLarge.copy(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 26.sp),
        bodyMedium = base.bodyMedium.copy(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 24.sp),
        bodySmall = base.bodySmall.copy(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 20.sp),

        labelLarge = base.labelLarge.copy(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 14.sp),
        labelMedium = base.labelMedium.copy(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 12.sp),
        labelSmall = base.labelSmall.copy(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 11.sp),
    )

    MaterialTheme(
        colorScheme = if (darkTheme) DarkPalette else LightPalette,
        typography = type,
        content = content,
    )
}

package com.ordia.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.ordia.app.data.preferences.AccentPalette
import com.ordia.app.data.preferences.ThemeMode

// Minimalist foundation colors (Grayscale + Semantic)
val OrdiaBlack = Color(0xFF090909)
val OrdiaWhite = Color(0xFFFFFFFF)
val OrdiaSurfaceLight = Color(0xFFF9F9F9)
val OrdiaSurfaceDark = Color(0xFF141414)
val OrdiaGrayLight = Color(0xFFE5E5E5)
val OrdiaGrayDark = Color(0xFF262626)
val OrdiaTextPrimaryLight = Color(0xFF1A1A1A)
val OrdiaTextPrimaryDark = Color(0xFFF5F5F5)
val OrdiaTextSecondaryLight = Color(0xFF737373)
val OrdiaTextSecondaryDark = Color(0xFFA3A3A3)

// Semantic colors
val OrdiaSemanticAlert = Color(0xFFE5484D)
val OrdiaSemanticSuccess = Color(0xFF30A46C)
val OrdiaSemanticFocus = Color(0xFF0090FF)
val OrdiaSemanticWarning = Color(0xFFF7CE00)

private val LightColors = lightColorScheme(
    primary = OrdiaBlack,
    onPrimary = OrdiaWhite,
    primaryContainer = OrdiaGrayLight,
    onPrimaryContainer = OrdiaBlack,
    secondary = OrdiaGrayLight,
    onSecondary = OrdiaBlack,
    secondaryContainer = OrdiaSurfaceLight,
    onSecondaryContainer = OrdiaTextPrimaryLight,
    tertiary = OrdiaSemanticFocus,
    onTertiary = OrdiaWhite,
    background = OrdiaWhite,
    onBackground = OrdiaTextPrimaryLight,
    surface = OrdiaSurfaceLight,
    onSurface = OrdiaTextPrimaryLight,
    surfaceVariant = OrdiaGrayLight,
    onSurfaceVariant = OrdiaTextSecondaryLight,
    outline = OrdiaGrayLight,
    outlineVariant = Color(0xFFF0F0F0),
    error = OrdiaSemanticAlert,
    onError = OrdiaWhite
)

private val DarkColors = darkColorScheme(
    primary = OrdiaWhite,
    onPrimary = OrdiaBlack,
    primaryContainer = OrdiaGrayDark,
    onPrimaryContainer = OrdiaWhite,
    secondary = OrdiaGrayDark,
    onSecondary = OrdiaWhite,
    secondaryContainer = OrdiaSurfaceDark,
    onSecondaryContainer = OrdiaTextPrimaryDark,
    tertiary = OrdiaSemanticFocus,
    onTertiary = OrdiaWhite,
    background = OrdiaBlack,
    onBackground = OrdiaTextPrimaryDark,
    surface = OrdiaSurfaceDark,
    onSurface = OrdiaTextPrimaryDark,
    surfaceVariant = OrdiaGrayDark,
    onSurfaceVariant = OrdiaTextSecondaryDark,
    outline = OrdiaGrayDark,
    outlineVariant = Color(0xFF1A1A1A),
    error = OrdiaSemanticAlert,
    onError = OrdiaWhite
)

@Composable
fun OrdiaTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    // AccentPalette is kept for compatibility with existing code calling this, but ignored in favor of minimalism
    accentPalette: AccentPalette = AccentPalette.GOLD,
    content: @Composable () -> Unit
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val colors = if (dark) DarkColors else LightColors

    MaterialTheme(
        colorScheme = colors,
        typography = OrdiaTypography,
        shapes = OrdiaShapes,
        content = content
    )
}

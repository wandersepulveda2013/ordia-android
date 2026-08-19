package com.ordia.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Strict monochrome base (white, black, carefully chosen grays)
val OrdiaWhite = Color(0xFFFFFFFF)
val OrdiaBlack = Color(0xFF000000)
val OrdiaGrayLight = Color(0xFFF5F5F5)
val OrdiaGrayMedium = Color(0xFF9E9E9E)
val OrdiaGrayDark = Color(0xFF212121)

// Semantic Alert
val SemanticAlert = Color(0xFFD32F2F)

private val LightColorScheme = lightColorScheme(
    primary = OrdiaBlack,
    onPrimary = OrdiaWhite,
    secondary = OrdiaGrayMedium,
    onSecondary = OrdiaWhite,
    error = SemanticAlert,
    onError = OrdiaWhite,
    background = OrdiaWhite,
    onBackground = OrdiaBlack,
    surface = OrdiaWhite,
    onSurface = OrdiaBlack,
    surfaceVariant = OrdiaGrayLight,
    onSurfaceVariant = OrdiaGrayDark,
    outline = OrdiaGrayMedium
)

private val DarkColorScheme = darkColorScheme(
    primary = OrdiaWhite,
    onPrimary = OrdiaBlack,
    secondary = OrdiaGrayMedium,
    onSecondary = OrdiaBlack,
    error = SemanticAlert,
    onError = OrdiaWhite,
    background = OrdiaBlack,
    onBackground = OrdiaWhite,
    surface = OrdiaBlack,
    onSurface = OrdiaWhite,
    surfaceVariant = OrdiaGrayDark,
    onSurfaceVariant = OrdiaGrayLight,
    outline = OrdiaGrayMedium
)

@Composable
fun OrdiaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) {
        DarkColorScheme
    } else {
        LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = OrdiaTypography,
        content = content
    )
}

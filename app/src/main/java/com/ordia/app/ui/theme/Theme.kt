package com.ordia.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val OrdiaWhite = Color(0xFFFFFFFF)
val OrdiaBlack = Color(0xFF000000)
val OrdiaGray50 = Color(0xFFFAFAFA)
val OrdiaGray100 = Color(0xFFF5F5F5)
val OrdiaGray200 = Color(0xFFEEEEEE)
val OrdiaGray300 = Color(0xFFE0E0E0)
val OrdiaGray400 = Color(0xFFBDBDBD)
val OrdiaGray500 = Color(0xFF9E9E9E)
val OrdiaGray600 = Color(0xFF757575)
val OrdiaGray700 = Color(0xFF616161)
val OrdiaGray800 = Color(0xFF424242)
val OrdiaGray900 = Color(0xFF212121)

// Secondary palette for priorities/accents
val OrdiaAccent = Color(0xFF1A73E8) // Subdued blue for default accents
val OrdiaPriority = Color(0xFFE53935) // Red for urgent
val OrdiaSuccess = Color(0xFF43A047) // Green for success
val OrdiaFocus = Color(0xFFFDD835) // Yellow for focus/highlight

private val LightColorScheme = lightColorScheme(
    primary = OrdiaBlack,
    onPrimary = OrdiaWhite,
    secondary = OrdiaGray700,
    onSecondary = OrdiaWhite,
    background = OrdiaGray50,
    onBackground = OrdiaBlack,
    surface = OrdiaWhite,
    onSurface = OrdiaBlack,
    surfaceVariant = OrdiaGray100,
    onSurfaceVariant = OrdiaGray800,
    outline = OrdiaGray300,
    outlineVariant = OrdiaGray200,
    error = OrdiaPriority,
    onError = OrdiaWhite,
)

private val DarkColorScheme = darkColorScheme(
    primary = OrdiaWhite,
    onPrimary = OrdiaBlack,
    secondary = OrdiaGray300,
    onSecondary = OrdiaBlack,
    background = OrdiaGray900,
    onBackground = OrdiaWhite,
    surface = OrdiaBlack,
    onSurface = OrdiaWhite,
    surfaceVariant = OrdiaGray800,
    onSurfaceVariant = OrdiaGray200,
    outline = OrdiaGray700,
    outlineVariant = OrdiaGray800,
    error = OrdiaPriority,
    onError = OrdiaBlack,
)

@Composable
fun OrdiaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = OrdiaTypography,
        content = content,
    )
}

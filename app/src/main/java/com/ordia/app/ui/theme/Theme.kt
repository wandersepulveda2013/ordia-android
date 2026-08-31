package com.ordia.app.ui.theme
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColorScheme = lightColorScheme(
    primary = OrdiaBlack,
    onPrimary = OrdiaWhite,
    secondary = OrdiaGray700,
    onSecondary = OrdiaWhite,
    background = OrdiaWhite,
    onBackground = OrdiaBlack,
    surface = OrdiaWhite,
    onSurface = OrdiaBlack,
    surfaceVariant = OrdiaGray100,
    onSurfaceVariant = OrdiaGray900,
    error = SemanticAlert,
    onError = OrdiaWhite
)
private val DarkColorScheme = darkColorScheme(
    primary = DarkOrdiaBlack,
    onPrimary = DarkOrdiaWhite,
    secondary = DarkOrdiaGray700,
    onSecondary = DarkOrdiaWhite,
    background = DarkOrdiaWhite,
    onBackground = DarkOrdiaBlack,
    surface = DarkOrdiaWhite,
    onSurface = DarkOrdiaBlack,
    surfaceVariant = DarkOrdiaGray100,
    onSurfaceVariant = DarkOrdiaGray900,
    error = SemanticAlert,
    onError = DarkOrdiaWhite
)
@Composable
fun OrdiaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = OrdiaTypography,
        content = content
    )
}

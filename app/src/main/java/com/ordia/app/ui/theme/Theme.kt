package com.ordia.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

data class Spacing(
    val extraSmall: Dp = 4.dp,
    val small: Dp = 8.dp,
    val medium: Dp = 16.dp,
    val large: Dp = 24.dp,
    val extraLarge: Dp = 32.dp,
    val huge: Dp = 48.dp
)

val LocalSpacing = staticCompositionLocalOf { Spacing() }

private val LightOrdiaScheme = lightColorScheme(
    primary = OrdiaBlack,
    onPrimary = OrdiaWhite,
    secondary = OrdiaBlack,
    onSecondary = OrdiaWhite,
    background = OrdiaWhite,
    onBackground = OrdiaBlack,
    surface = OrdiaWhite,
    onSurface = OrdiaBlack,
    surfaceVariant = OrdiaGray100,
    onSurfaceVariant = OrdiaGray700,
    outline = OrdiaGray300,
    error = OrdiaPriority,
    onError = OrdiaWhite
)

private val DarkOrdiaScheme = darkColorScheme(
    primary = OrdiaWhite,
    onPrimary = OrdiaBlack,
    secondary = OrdiaWhite,
    onSecondary = OrdiaBlack,
    background = OrdiaBlack,
    onBackground = OrdiaWhite,
    surface = OrdiaBlack,
    onSurface = OrdiaWhite,
    surfaceVariant = OrdiaGray900,
    onSurfaceVariant = OrdiaGray400,
    outline = OrdiaGray800,
    error = OrdiaPriority,
    onError = OrdiaWhite
)

@Composable
fun OrdiaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkOrdiaScheme else LightOrdiaScheme

    CompositionLocalProvider(
        LocalSpacing provides Spacing()
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = OrdiaTypography,
            content = content,
        )
    }
}

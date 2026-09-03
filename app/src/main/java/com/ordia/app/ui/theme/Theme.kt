package com.ordia.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = Black,
    onPrimary = White,
    secondary = Gray800,
    onSecondary = White,
    background = White,
    onBackground = Black,
    surface = Gray50,
    onSurface = Black,
    surfaceVariant = Gray100,
    onSurfaceVariant = Gray800,
    outline = Gray300,
    error = AccentPriority,
    onError = White
)

private val DarkColors = darkColorScheme(
    primary = White,
    onPrimary = Black,
    secondary = Gray200,
    onSecondary = Black,
    background = Black,
    onBackground = White,
    surface = Gray900,
    onSurface = White,
    surfaceVariant = Gray800,
    onSurfaceVariant = Gray200,
    outline = Gray700,
    error = AccentPriority,
    onError = Black
)

@Composable
fun OrdiaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = OrdiaTypography,
        content = content,
    )
}

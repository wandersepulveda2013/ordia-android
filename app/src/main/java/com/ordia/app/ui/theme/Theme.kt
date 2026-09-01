package com.ordia.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColorScheme = lightColorScheme(
    primary = OrdiaPrimaryLight,
    onPrimary = OrdiaOnPrimaryLight,
    background = OrdiaBackgroundLight,
    onBackground = OrdiaOnBackgroundLight,
    surface = OrdiaSurfaceLight,
    onSurface = OrdiaOnSurfaceLight,
    error = SemanticAlert,
)

private val DarkColorScheme = darkColorScheme(
    primary = OrdiaPrimaryDark,
    onPrimary = OrdiaOnPrimaryDark,
    background = OrdiaBackgroundDark,
    onBackground = OrdiaOnBackgroundDark,
    surface = OrdiaSurfaceDark,
    onSurface = OrdiaOnSurfaceDark,
    error = SemanticAlert,
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

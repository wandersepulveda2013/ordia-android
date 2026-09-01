package com.ordia.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val White = Color(0xFFFFFFFF)
val Black = Color(0xFF000000)

val LightGray1 = Color(0xFFF7F7F7)
val LightGray2 = Color(0xFFEFEFEF)
val LightGray3 = Color(0xFFDFDFDF)
val DarkGray1 = Color(0xFF1E1E1E)
val DarkGray2 = Color(0xFF2A2A2A)
val DarkGray3 = Color(0xFF3B3B3B)

val SemanticAlert = Color(0xFFD32F2F)
val SemanticSuccess = Color(0xFF388E3C)
val SemanticFocus = Color(0xFF0288D1)

private val LightColors = lightColorScheme(
    primary = Black,
    onPrimary = White,
    secondary = DarkGray2,
    onSecondary = White,
    background = White,
    onBackground = Black,
    surface = White,
    onSurface = Black,
    surfaceVariant = LightGray1,
    onSurfaceVariant = DarkGray1,
    outline = LightGray3,
    error = SemanticAlert,
    onError = White
)

private val DarkColors = darkColorScheme(
    primary = White,
    onPrimary = Black,
    secondary = LightGray2,
    onSecondary = Black,
    background = Black,
    onBackground = White,
    surface = Black,
    onSurface = White,
    surfaceVariant = DarkGray1,
    onSurfaceVariant = LightGray1,
    outline = DarkGray3,
    error = SemanticAlert,
    onError = Black
)

@Composable
fun NotepadTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = OrdiaTypography,
        content = content,
    )
}

package com.ordia.app.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// Minimalist Monochrome Palette
private val Neutral0 = Color(0xFF000000)
private val Neutral10 = Color(0xFF1C1B1F)
private val Neutral20 = Color(0xFF313033)
private val Neutral30 = Color(0xFF484649)
private val Neutral40 = Color(0xFF605D62)
private val Neutral50 = Color(0xFF787579)
private val Neutral60 = Color(0xFF939094)
private val Neutral70 = Color(0xFFAEA9AD)
private val Neutral80 = Color(0xFFC9C5CA)
private val Neutral90 = Color(0xFFE6E1E5)
private val Neutral95 = Color(0xFFF4EFF4)
private val Neutral99 = Color(0xFFFFFBFE)
private val Neutral100 = Color(0xFFFFFFFF)

// Subtle Semantic Accents
private val AccentPrimary = Color(0xFF6750A4)
private val AccentSecondary = Color(0xFF625B71)
private val AccentTertiary = Color(0xFF7D5260)

private val LightColorScheme = lightColorScheme(
    primary = Neutral10,
    onPrimary = Neutral100,
    primaryContainer = Neutral90,
    onPrimaryContainer = Neutral10,
    secondary = Neutral40,
    onSecondary = Neutral100,
    secondaryContainer = Neutral95,
    onSecondaryContainer = Neutral10,
    tertiary = Neutral30,
    onTertiary = Neutral100,
    tertiaryContainer = Neutral90,
    onTertiaryContainer = Neutral10,
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
    background = Neutral99,
    onBackground = Neutral10,
    surface = Neutral99,
    onSurface = Neutral10,
    surfaceVariant = Neutral90,
    onSurfaceVariant = Neutral30,
    outline = Neutral50,
    outlineVariant = Neutral80,
    scrim = Neutral0,
)

private val DarkColorScheme = darkColorScheme(
    primary = Neutral90,
    onPrimary = Neutral10,
    primaryContainer = Neutral30,
    onPrimaryContainer = Neutral90,
    secondary = Neutral80,
    onSecondary = Neutral20,
    secondaryContainer = Neutral30,
    onSecondaryContainer = Neutral90,
    tertiary = Neutral80,
    onTertiary = Neutral20,
    tertiaryContainer = Neutral30,
    onTertiaryContainer = Neutral90,
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC),
    background = Neutral10,
    onBackground = Neutral90,
    surface = Neutral10,
    onSurface = Neutral90,
    surfaceVariant = Neutral30,
    onSurfaceVariant = Neutral80,
    outline = Neutral60,
    outlineVariant = Neutral30,
    scrim = Neutral0,
)

@Composable
fun NotepadTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

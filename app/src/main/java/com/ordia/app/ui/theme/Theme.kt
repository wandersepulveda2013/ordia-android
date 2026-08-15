package com.ordia.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.ordia.app.data.preferences.AccentPalette
import com.ordia.app.data.preferences.ThemeMode

// Base Monochrome Core
val OrdiaWhite = Color(0xFFFFFFFF)
val OrdiaBlack = Color(0xFF0F0F0F)
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

// Semantic Palette (Contained, meaningful colors)
val SemanticAlert = Color(0xFFD32F2F) // Errors, Overdue, Urgent
val SemanticSuccess = Color(0xFF388E3C) // Done, Streaks, Complete
val SemanticFocus = Color(0xFF1976D2) // Active timers, Focus mode
val SemanticAutomation = Color(0xFF7B1FA2) // Rules, Auto-actions, Insights

/** Palette of accent colors chosen by the user. */
data class AccentSwatch(
    val lightSecondary: Color,
    val lightSecondaryContainer: Color,
    val lightOnSecondaryContainer: Color,
    val darkSecondary: Color,
    val darkSecondaryContainer: Color,
    val darkOnSecondaryContainer: Color
)

val accentSwatches: Map<AccentPalette, AccentSwatch> = mapOf(
    AccentPalette.GOLD to AccentSwatch(
        Color(0xFF8A682D), Color(0xFFF2E5C9), Color(0xFF34270F),
        Color(0xFFD9BC7A), Color(0xFF4C3C1C), Color(0xFFFFE8AF)
    ),
    AccentPalette.SAGE to AccentSwatch(
        Color(0xFF76845F), Color(0xFFE0E8D6), Color(0xFF1F2A14),
        Color(0xFFB9C99F), Color(0xFF37431F), Color(0xFFDDEBC8)
    ),
    AccentPalette.ROSE to AccentSwatch(
        Color(0xFFA87373), Color(0xFFF2DADA), Color(0xFF3A1717),
        Color(0xFFD9A3A3), Color(0xFF4A1F1F), Color(0xFFFFDADA)
    ),
    AccentPalette.LAVENDER to AccentSwatch(
        Color(0xFF88759C), Color(0xFFE4DCEE), Color(0xFF241B33),
        Color(0xFFB5A5CC), Color(0xFF332745), Color(0xFFE6DCF6)
    ),
    AccentPalette.OCEAN to AccentSwatch(
        Color(0xFF3E6680), Color(0xFFD2E0EA), Color(0xFF0F2533),
        Color(0xFF8FB4CC), Color(0xFF1F3A4C), Color(0xFFCFE2F0)
    ),
    AccentPalette.TERRACOTTA to AccentSwatch(
        Color(0xFFB5603E), Color(0xFFF2DDD0), Color(0xFF33180C),
        Color(0xFFD89A78), Color(0xFF4A2417), Color(0xFFF6D9C8)
    ),
    AccentPalette.SYSTEM to AccentSwatch(
        Color(0xFF8A682D), Color(0xFFF2E5C9), Color(0xFF34270F),
        Color(0xFFD9BC7A), Color(0xFF4C3C1C), Color(0xFFFFE8AF)
    )
)

private val LightColors = lightColorScheme(
    primary = OrdiaBlack,
    onPrimary = OrdiaWhite,
    primaryContainer = OrdiaGray100,
    onPrimaryContainer = OrdiaBlack,
    secondary = Color(0xFF8A682D),
    onSecondary = OrdiaWhite,
    secondaryContainer = Color(0xFFF2E5C9),
    onSecondaryContainer = Color(0xFF34270F),
    tertiary = SemanticFocus,
    onTertiary = OrdiaWhite,
    background = OrdiaWhite,
    onBackground = OrdiaBlack,
    surface = OrdiaWhite,
    onSurface = OrdiaBlack,
    surfaceVariant = OrdiaGray50,
    onSurfaceVariant = OrdiaGray700,
    outline = OrdiaGray300,
    outlineVariant = OrdiaGray200,
    error = SemanticAlert,
    onError = OrdiaWhite
)

private val DarkColors = darkColorScheme(
    primary = OrdiaWhite,
    onPrimary = OrdiaBlack,
    primaryContainer = OrdiaGray800,
    onPrimaryContainer = OrdiaWhite,
    secondary = Color(0xFFD9BC7A),
    onSecondary = Color(0xFF33270E),
    secondaryContainer = Color(0xFF4C3C1C),
    onSecondaryContainer = Color(0xFFFFE8AF),
    tertiary = Color(0xFF64B5F6),
    onTertiary = OrdiaBlack,
    background = OrdiaBlack,
    onBackground = OrdiaGray50,
    surface = OrdiaBlack,
    onSurface = OrdiaGray50,
    surfaceVariant = OrdiaGray900,
    onSurfaceVariant = OrdiaGray400,
    outline = OrdiaGray700,
    outlineVariant = OrdiaGray800,
    error = Color(0xFFEF5350),
    onError = OrdiaBlack
)

@Composable
fun OrdiaTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    accentPalette: AccentPalette = AccentPalette.GOLD,
    content: @Composable () -> Unit
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val colors = if (accentPalette == AccentPalette.SYSTEM && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
        val context = androidx.compose.ui.platform.LocalContext.current
        if (dark) androidx.compose.material3.dynamicDarkColorScheme(context)
        else androidx.compose.material3.dynamicLightColorScheme(context)
    } else {
        val swatch = accentSwatches[accentPalette] ?: accentSwatches.getValue(AccentPalette.GOLD)
        val base = if (dark) DarkColors else LightColors
        if (dark) {
            base.copy(
                secondary = swatch.darkSecondary,
                onSecondary = OrdiaBlack,
                secondaryContainer = swatch.darkSecondaryContainer,
                onSecondaryContainer = swatch.darkOnSecondaryContainer
            )
        } else {
            base.copy(
                secondary = swatch.lightSecondary,
                onSecondary = OrdiaWhite,
                secondaryContainer = swatch.lightSecondaryContainer,
                onSecondaryContainer = swatch.lightOnSecondaryContainer
            )
        }
    }
    MaterialTheme(
        colorScheme = colors,
        typography = OrdiaTypography,
        shapes = OrdiaShapes,
        content = content
    )
}

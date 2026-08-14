package com.ordia.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.ordia.app.data.preferences.AccentPalette
import com.ordia.app.data.preferences.ThemeMode

// Base Monochrome Palette (Zero UI focus)
val OrdiaBlack = Color(0xFF121212)
val OrdiaWhite = Color(0xFFFFFFFF)

// Grayscales for subtle structure
val OrdiaGray50 = Color(0xFFFAFAFA)
val OrdiaGray100 = Color(0xFFF5F5F5)
val OrdiaGray200 = Color(0xFFEEEEEE)
val OrdiaGray300 = Color(0xFFE0E0E0)
val OrdiaGray800 = Color(0xFF212121)
val OrdiaGray900 = Color(0xFF171717)

// Minimal Action Palette
val OrdiaActionBlue = Color(0xFF2962FF)
val OrdiaActionGreen = Color(0xFF00C853)
val OrdiaActionRed = Color(0xFFD50000)

/** Palette of accent colors chosen by the user. Each entry holds (secondary, secondaryContainer, onSecondaryContainer) for light and dark. */
data class AccentSwatch(
    val lightSecondary: Color,
    val lightSecondaryContainer: Color,
    val lightOnSecondaryContainer: Color,
    val darkSecondary: Color,
    val darkSecondaryContainer: Color,
    val darkOnSecondaryContainer: Color
)

// Simplified Accents for functional meaning
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
        Color(0xFF121212), Color(0xFFEEEEEE), Color(0xFF121212),
        Color(0xFFFAFAFA), Color(0xFF212121), Color(0xFFFAFAFA)
    )
)

private val LightColors = lightColorScheme(
    primary = OrdiaBlack,
    onPrimary = OrdiaWhite,
    primaryContainer = OrdiaGray200,
    onPrimaryContainer = OrdiaBlack,
    secondary = OrdiaActionBlue,
    onSecondary = OrdiaWhite,
    secondaryContainer = OrdiaGray100,
    onSecondaryContainer = OrdiaBlack,
    tertiary = OrdiaGray300,
    onTertiary = OrdiaBlack,
    background = OrdiaWhite,
    onBackground = OrdiaBlack,
    surface = OrdiaWhite,
    onSurface = OrdiaBlack,
    surfaceVariant = OrdiaGray50,
    onSurfaceVariant = Color(0xFF616161),
    outline = OrdiaGray300,
    outlineVariant = OrdiaGray200,
    error = OrdiaActionRed,
    onError = OrdiaWhite
)

private val DarkColors = darkColorScheme(
    primary = OrdiaWhite,
    onPrimary = OrdiaBlack,
    primaryContainer = OrdiaGray800,
    onPrimaryContainer = OrdiaWhite,
    secondary = OrdiaActionBlue,
    onSecondary = OrdiaBlack,
    secondaryContainer = OrdiaGray900,
    onSecondaryContainer = OrdiaWhite,
    tertiary = OrdiaGray800,
    onTertiary = OrdiaWhite,
    background = OrdiaBlack,
    onBackground = OrdiaWhite,
    surface = OrdiaBlack,
    onSurface = OrdiaWhite,
    surfaceVariant = OrdiaGray900,
    onSurfaceVariant = Color(0xFFAAAAAA),
    outline = OrdiaGray800,
    outlineVariant = OrdiaGray900,
    error = Color(0xFFFF5252),
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

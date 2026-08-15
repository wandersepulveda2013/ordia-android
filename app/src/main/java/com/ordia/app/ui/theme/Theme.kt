package com.ordia.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.ordia.app.data.preferences.AccentPalette
import com.ordia.app.data.preferences.ThemeMode

val OrdiaInk = Color(0xFF1D1B17)
val OrdiaCream = Color(0xFFF7F3EB)
val OrdiaPaper = Color(0xFFFFFCF7)
val OrdiaGold = Color(0xFF8A682D)
val OrdiaGoldSoft = Color(0xFFD9BC7A)
val OrdiaSage = Color(0xFF76845F)
val OrdiaRose = Color(0xFFA87373)
val OrdiaLavender = Color(0xFF88759C)

// Semantic colors
val SemanticAlert = Color(0xFFD32F2F)
val SemanticAlertDark = Color(0xFFE57373)
val SemanticSuccess = Color(0xFF388E3C)
val SemanticSuccessDark = Color(0xFF81C784)
val SemanticFocus = Color(0xFF1976D2)
val SemanticFocusDark = Color(0xFF64B5F6)
val SemanticAutomation = Color(0xFF7B1FA2)
val SemanticAutomationDark = Color(0xFFBA68C8)

/** Palette of accent colors chosen by the user. Each entry holds (secondary, secondaryContainer, onSecondaryContainer) for light and dark. */
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
        OrdiaGold, Color(0xFFF2E5C9), Color(0xFF34270F),
        OrdiaGoldSoft, Color(0xFF4C3C1C), Color(0xFFFFE8AF)
    ),
    AccentPalette.SAGE to AccentSwatch(
        OrdiaSage, Color(0xFFE0E8D6), Color(0xFF1F2A14),
        Color(0xFFB9C99F), Color(0xFF37431F), Color(0xFFDDEBC8)
    ),
    AccentPalette.ROSE to AccentSwatch(
        OrdiaRose, Color(0xFFF2DADA), Color(0xFF3A1717),
        Color(0xFFD9A3A3), Color(0xFF4A1F1F), Color(0xFFFFDADA)
    ),
    AccentPalette.LAVENDER to AccentSwatch(
        OrdiaLavender, Color(0xFFE4DCEE), Color(0xFF241B33),
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
        OrdiaGold, Color(0xFFF2E5C9), Color(0xFF34270F),
        OrdiaGoldSoft, Color(0xFF4C3C1C), Color(0xFFFFE8AF)
    )
)

private val LightColors = lightColorScheme(
    primary = OrdiaInk,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFF2EFE8),
    onPrimaryContainer = OrdiaInk,
    secondary = OrdiaGold,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF2E5C9),
    onSecondaryContainer = Color(0xFF34270F),
    tertiary = OrdiaSage,
    onTertiary = Color.White,
    background = Color(0xFFFFFFFF),
    onBackground = OrdiaInk,
    surface = Color(0xFFFFFFFF),
    onSurface = OrdiaInk,
    surfaceVariant = Color(0xFFF5F3ED),
    onSurfaceVariant = Color(0xFF625D55),
    outline = Color(0xFFE0DCD3),
    outlineVariant = Color(0xFFF0EBE0),
    error = SemanticAlert,
    onError = Color.White
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFF3EDE2),
    onPrimary = Color(0xFF1A1916),
    primaryContainer = Color(0xFF2B2924),
    onPrimaryContainer = Color(0xFFF5EFE4),
    secondary = OrdiaGoldSoft,
    onSecondary = Color(0xFF33270E),
    secondaryContainer = Color(0xFF4C3C1C),
    onSecondaryContainer = Color(0xFFFFE8AF),
    tertiary = Color(0xFFB9C99F),
    onTertiary = Color(0xFF263017),
    background = Color(0xFF0F0E0C),
    onBackground = Color(0xFFEAE5DC),
    surface = Color(0xFF141310),
    onSurface = Color(0xFFEAE5DC),
    surfaceVariant = Color(0xFF201E1A),
    onSurfaceVariant = Color(0xFFB3ADA4),
    outline = Color(0xFF38352F),
    outlineVariant = Color(0xFF26241F),
    error = SemanticAlertDark,
    onError = Color(0xFF690005)
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
                onSecondary = Color(0xFF1A1916),
                secondaryContainer = swatch.darkSecondaryContainer,
                onSecondaryContainer = swatch.darkOnSecondaryContainer
            )
        } else {
            base.copy(
                secondary = swatch.lightSecondary,
                onSecondary = Color.White,
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

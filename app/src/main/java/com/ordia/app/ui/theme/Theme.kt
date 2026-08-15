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

// Semantic Colors
val SemanticAlertLight = Color(0xFFC62828)
val SemanticAlertDark = Color(0xFFEF5350)
val SemanticSuccessLight = Color(0xFF2E7D32)
val SemanticSuccessDark = Color(0xFF81C784)
val SemanticFocusLight = Color(0xFF0277BD)
val SemanticFocusDark = Color(0xFF4FC3F7)
val SemanticAutomationLight = Color(0xFF6A1B9A)
val SemanticAutomationDark = Color(0xFFCE93D8)

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
    primaryContainer = Color(0xFFEAE2D4),
    onPrimaryContainer = OrdiaInk,
    secondary = OrdiaGold,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF2E5C9),
    onSecondaryContainer = Color(0xFF34270F),
    tertiary = OrdiaSage,
    onTertiary = Color.White,
    background = OrdiaCream,
    onBackground = OrdiaInk,
    surface = OrdiaPaper,
    onSurface = OrdiaInk,
    surfaceVariant = Color(0xFFEDE7DC),
    onSurfaceVariant = Color(0xFF625D55),
    outline = Color(0xFF8C8579),
    outlineVariant = Color(0xFFDAD1C2),
    error = Color(0xFF9A3E3E),
    onError = Color.White
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFF3EDE2),
    onPrimary = Color(0xFF1A1916),
    primaryContainer = Color(0xFF34312B),
    onPrimaryContainer = Color(0xFFF5EFE4),
    secondary = OrdiaGoldSoft,
    onSecondary = Color(0xFF33270E),
    secondaryContainer = Color(0xFF4C3C1C),
    onSecondaryContainer = Color(0xFFFFE8AF),
    tertiary = Color(0xFFB9C99F),
    onTertiary = Color(0xFF263017),
    background = Color(0xFF141310),
    onBackground = Color(0xFFEDE8DF),
    surface = Color(0xFF1C1B18),
    onSurface = Color(0xFFEDE8DF),
    surfaceVariant = Color(0xFF2B2924),
    onSurfaceVariant = Color(0xFFC9C2B8),
    outline = Color(0xFF938C81),
    outlineVariant = Color(0xFF47433D),
    error = Color(0xFFFFB4AB),
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

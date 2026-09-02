package com.ordia.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

private val LightPaper = lightColorScheme(
    primary = Ink,
    onPrimary = Page,
    secondary = Ink,
    onSecondary = Page,
    background = Page,
    onBackground = Ink,
    surface = Page,
    onSurface = Ink,
    surfaceVariant = SoftPaper,
    onSurfaceVariant = InkMuted,
    outline = Rule,
)

private val DarkPaper = darkColorScheme(
    primary = PageOnDark,
    onPrimary = DarkInk,
    secondary = PageOnDark,
    onSecondary = DarkInk,
    background = DarkInk,
    onBackground = PageOnDark,
    surface = DarkInk,
    onSurface = PageOnDark,
    surfaceVariant = DarkInkRaised,
    onSurfaceVariant = PageMuted,
    outline = DarkRule,
)

@Immutable
data class OrdiaSemanticColors(
    val priority: Color,
    val alert: Color,
    val success: Color,
    val focus: Color,
    val calendar: Color,
    val automations: Color,
    val guardians: Color
)

val LightSemanticColors = OrdiaSemanticColors(
    priority = ColorPriority,
    alert = ColorAlert,
    success = ColorSuccess,
    focus = ColorFocus,
    calendar = ColorCalendar,
    automations = ColorAutomations,
    guardians = ColorGuardians
)

val DarkSemanticColors = OrdiaSemanticColors(
    priority = ColorPriorityDark,
    alert = ColorAlertDark,
    success = ColorSuccessDark,
    focus = ColorFocusDark,
    calendar = ColorCalendarDark,
    automations = ColorAutomationsDark,
    guardians = ColorGuardiansDark
)

val LocalOrdiaSemanticColors = staticCompositionLocalOf { LightSemanticColors }

object OrdiaTheme {
    val semanticColors: OrdiaSemanticColors
        @Composable
        get() = LocalOrdiaSemanticColors.current
}

@Composable
fun NotepadTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val semanticColors = if (darkTheme) DarkSemanticColors else LightSemanticColors

    CompositionLocalProvider(LocalOrdiaSemanticColors provides semanticColors) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkPaper else LightPaper,
            typography = OrdiaTypography,
            content = content,
        )
    }
}

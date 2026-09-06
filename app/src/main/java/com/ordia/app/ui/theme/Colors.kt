package com.ordia.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.darkColorScheme

val Ink = Color(0xFF1A1A1A)
val InkMuted = Color(0xFF666666)
val SoftPaper = Color(0xFFF0F0F0)
val Rule = Color(0xFFE5E5E5)
val Page = Color(0xFFFAFAFA)

val PageOnDark = Color(0xFFEAEAEA)
val PageMuted = Color(0xFF999999)
val DarkInk = Color(0xFF121212)
val DarkInkRaised = Color(0xFF1E1E1E)
val DarkRule = Color(0xFF333333)

val PalettePriority = Color(0xFFE53935)
val PaletteSuccess = Color(0xFF43A047)
val PaletteFocus = Color(0xFF1E88E5)

val LightColors = lightColorScheme(
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

val DarkColors = darkColorScheme(
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

package com.ordia.app.ui.theme

import androidx.compose.ui.graphics.Color

// Base Grayscale (Light)
val White = Color(0xFFFFFFFF)
val Black = Color(0xFF000000)
val Gray50 = Color(0xFFFAFAFA)
val Gray100 = Color(0xFFF5F5F5)
val Gray200 = Color(0xFFEEEEEE)
val Gray300 = Color(0xFFE0E0E0)
val Gray400 = Color(0xFFBDBDBD)
val Gray500 = Color(0xFF9E9E9E)
val Gray600 = Color(0xFF757575)
val Gray700 = Color(0xFF616161)
val Gray800 = Color(0xFF424242)
val Gray900 = Color(0xFF212121)

// Semantic Palette (Restrained)
val OrdiaAlert = Color(0xFFD32F2F)      // Red for destructive/urgent
val OrdiaSuccess = Color(0xFF388E3C)    // Green for completion/success
val OrdiaPriority = Color(0xFFF57C00)   // Orange for high priority
val OrdiaFocus = Color(0xFF1976D2)      // Blue for focus/active state
val OrdiaAutomation = Color(0xFF7B1FA2) // Purple for automated actions/intelligence

// Core Theme mappings for Light
val LightBackground = White
val LightSurface = White
val LightSurfaceRaised = Gray50
val LightOnBackground = Gray900
val LightOnSurfaceMuted = Gray600
val LightDivider = Gray200

// Core Theme mappings for Dark
val DarkBackground = Black
val DarkSurface = Color(0xFF121212)
val DarkSurfaceRaised = Color(0xFF1E1E1E)
val DarkOnBackground = Gray100
val DarkOnSurfaceMuted = Gray400
val DarkDivider = Gray800

package com.ordia.app.ui.theme

import androidx.compose.ui.graphics.Color

// Minimalist foundation (Blanco, Negro, Grises)
val PureWhite = Color(0xFFFFFFFF)
val PureBlack = Color(0xFF000000)

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

// Semantic colors (contained, for specific meaning)
val SemanticPriorityHigh = Color(0xFFD32F2F) // Alerta / Urgente
val SemanticSuccess = Color(0xFF388E3C)      // Éxito / Hecho
val SemanticFocus = Color(0xFF1976D2)        // Foco / Activo
val SemanticAutomation = Color(0xFF7B1FA2)   // Automatizaciones
val SemanticWarning = Color(0xFFF57C00)      // Advertencia

// Light Theme base
val Page = PureWhite
val SoftPaper = Gray50
val Ink = Gray900
val InkMuted = Gray600
val Rule = Gray200

// Dark Theme base
val DarkInk = Color(0xFF121212)
val DarkInkRaised = Color(0xFF1E1E1E)
val PageOnDark = Gray100
val PageMuted = Gray400
val DarkRule = Gray800

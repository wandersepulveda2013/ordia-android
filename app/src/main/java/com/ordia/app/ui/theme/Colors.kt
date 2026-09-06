package com.ordia.app.ui.theme

import androidx.compose.ui.graphics.Color

/** Core Minimalist Palette */
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

/** Semantic Secondary Palette */
val SemanticPriority = Color(0xFFD32F2F) // Priority / High Alert
val SemanticSuccess = Color(0xFF388E3C)  // Success / Completed
val SemanticFocus = Color(0xFF1976D2)    // Focus / Active state
val SemanticWarning = Color(0xFFF57C00)  // Warning / Attention
val SemanticCalendar = Color(0xFF7B1FA2) // Events / Agenda
val SemanticAutomation = Color(0xFF00796B) // Rules / Automations
val SemanticGuardian = Color(0xFF455A64) // Guardians

// Aliases for themes to maintain similar naming convention but updated hexes
val LightBackground = White
val LightSurface = White
val LightSurfaceVariant = Gray50
val LightOnBackground = Gray900
val LightOnSurfaceVariant = Gray600
val LightOutline = Gray300

val DarkBackground = Color(0xFF121212)
val DarkSurface = Color(0xFF121212)
val DarkSurfaceVariant = Color(0xFF1E1E1E)
val DarkOnBackground = Gray200
val DarkOnSurfaceVariant = Gray500
val DarkOutline = Gray800

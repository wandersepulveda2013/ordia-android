package com.ordia.app.ui.theme

import androidx.compose.ui.graphics.Color

// Base minimalist monochromatic colors
val BaseWhite = Color(0xFFFFFFFF)
val BaseBlack = Color(0xFF121212)
val BaseGrayLight = Color(0xFFF0F0F0)
val BaseGrayMedium = Color(0xFF888888)
val BaseGrayDark = Color(0xFF333333)

// Secondary Semantic Palette (Contained)
val SemanticPriority = Color(0xFFE57373)   // Soft Red for priority/urgent
val SemanticSuccess = Color(0xFF81C784)    // Soft Green for success/done
val SemanticFocus = Color(0xFF64B5F6)      // Soft Blue for focus
val SemanticAutomation = Color(0xFFFFD54F) // Soft Yellow for automations/magic
val SemanticGuardian = Color(0xFFBA68C8)   // Soft Purple for guardians

// Light Theme Mapping
val LightBackground = BaseWhite
val LightSurface = BaseWhite
val LightSurfaceVariant = BaseGrayLight
val LightOnBackground = BaseBlack
val LightOnSurface = BaseBlack
val LightOnSurfaceVariant = BaseGrayMedium
val LightOutline = BaseGrayLight

// Dark Theme Mapping
val DarkBackground = BaseBlack
val DarkSurface = BaseBlack
val DarkSurfaceVariant = BaseGrayDark
val DarkOnBackground = BaseWhite
val DarkOnSurface = BaseWhite
val DarkOnSurfaceVariant = BaseGrayMedium
val DarkOutline = BaseGrayDark

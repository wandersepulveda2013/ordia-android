package com.ordia.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * A restrained, monochrome-first palette. The design avoids excessive colors,
 * using careful grays and high contrast for focus.
 * Semantic colors are added for specific meanings (alert, success, focus, etc.)
 * but are used sparingly.
 */

// Base Light
val Page = Color(0xFFF9F9F9)        // Main background
val SoftPaper = Color(0xFFF1F1F1)   // Elevated or muted surfaces
val Ink = Color(0xFF121212)         // Primary text
val InkMuted = Color(0xFF6B6B6B)    // Secondary text
val Rule = Color(0xFFE0E0E0)        // Borders, dividers

// Base Dark
val DarkInk = Color(0xFF101010)       // Main background
val DarkInkRaised = Color(0xFF1A1A1A) // Elevated surfaces
val PageOnDark = Color(0xFFEBEBEB)    // Primary text
val PageMuted = Color(0xFF8A8A8A)     // Secondary text
val DarkRule = Color(0xFF2E2E2E)      // Borders, dividers

// Semantic Palette (Used sparingly for meaning)
val SemanticAlert = Color(0xFFD32F2F)
val SemanticSuccess = Color(0xFF388E3C)
val SemanticFocus = Color(0xFF1976D2)
val SemanticWarning = Color(0xFFF57C00)
val SemanticCalendar = Color(0xFF8E24AA) // Events, dates
val SemanticGuardian = Color(0xFF00897B) // Guardian actions/state
val SemanticAutomation = Color(0xFF5E35B1) // Automation/rules

// Colors for semantic roles in light theme
val AlertLight = SemanticAlert
val SuccessLight = SemanticSuccess
val FocusLight = SemanticFocus
val WarningLight = SemanticWarning

// Colors for semantic roles in dark theme (slightly adjusted for contrast)
val AlertDark = Color(0xFFEF5350)
val SuccessDark = Color(0xFF66BB6A)
val FocusDark = Color(0xFF42A5F5)
val WarningDark = Color(0xFFFFA726)

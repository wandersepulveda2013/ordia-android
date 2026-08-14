package com.ordia.app.ui.components.core

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.unit.dp

/**
 * Core spacing and dimensions for the Ordia Design System.
 * Adheres to the "Less is More" philosophy.
 */
object OrdiaSpacing {
    val XXS = 2.dp
    val XS = 4.dp
    val S = 8.dp
    val M = 16.dp
    val L = 24.dp
    val XL = 32.dp
    val XXL = 48.dp
    val XXXL = 64.dp

    val ScreenPadding = PaddingValues(horizontal = L, vertical = M)
    val CardPadding = PaddingValues(all = M)
    val ButtonPadding = PaddingValues(horizontal = L, vertical = 12.dp)
}

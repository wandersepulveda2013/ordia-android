package com.ordia.app.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Sistema de espaciado centralizado para Ordía.
 * Provee más aire, márgenes consistentes y mejor ritmo vertical
 * evitando la sensación de "apretar" información.
 */
data class Spacing(
    val extraSmall: Dp = 4.dp,
    val small: Dp = 8.dp,
    val medium: Dp = 16.dp,
    val large: Dp = 24.dp,
    val extraLarge: Dp = 32.dp,
    val doubleLarge: Dp = 48.dp,
    val screenHorizontal: Dp = 20.dp,
    val screenVertical: Dp = 24.dp,
    val itemSpacing: Dp = 12.dp
)

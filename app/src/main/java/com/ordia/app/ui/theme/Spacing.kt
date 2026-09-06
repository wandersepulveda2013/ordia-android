package com.ordia.app.ui.theme

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

data class Spacing(
    val extraSmall: Dp = 4.dp,
    val small: Dp = 8.dp,
    val medium: Dp = 16.dp,
    val large: Dp = 24.dp,
    val extraLarge: Dp = 32.dp,
    val defaultPadding: Dp = 16.dp,
    val contentPadding: Dp = 20.dp
)

val LocalSpacing = compositionLocalOf { Spacing() }

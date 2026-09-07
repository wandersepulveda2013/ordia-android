package com.ordia.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp

val baseTypography = Typography()

val OrdiaTypography = Typography(
    bodyLarge = baseTypography.bodyLarge.copy(fontSize = 17.sp, lineHeight = 28.sp),
    bodyMedium = baseTypography.bodyMedium.copy(fontSize = 16.sp, lineHeight = 26.sp),
    titleLarge = baseTypography.titleLarge.copy(fontFamily = FontFamily.Serif, fontSize = 24.sp),
    titleMedium = baseTypography.titleMedium.copy(fontFamily = FontFamily.Serif, fontSize = 20.sp),
    titleSmall = baseTypography.titleSmall.copy(fontFamily = FontFamily.Serif),
    labelSmall = baseTypography.labelSmall.copy(fontSize = 11.sp),
)

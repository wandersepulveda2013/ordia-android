package com.ordia.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp

val BaseTypography = Typography()

val OrdiaTypography = Typography(
    bodyLarge = BaseTypography.bodyLarge.copy(fontSize = 17.sp, lineHeight = 28.sp),
    bodyMedium = BaseTypography.bodyMedium.copy(fontSize = 16.sp, lineHeight = 26.sp),
    titleLarge = BaseTypography.titleLarge.copy(fontFamily = FontFamily.Serif, fontSize = 24.sp),
    titleMedium = BaseTypography.titleMedium.copy(fontFamily = FontFamily.Serif, fontSize = 20.sp),
    titleSmall = BaseTypography.titleSmall.copy(fontFamily = FontFamily.Serif),
    labelSmall = BaseTypography.labelSmall.copy(fontSize = 11.sp),
)

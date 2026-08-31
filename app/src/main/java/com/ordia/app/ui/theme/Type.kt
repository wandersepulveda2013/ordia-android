package com.ordia.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Typography = Typography(
    bodyLarge = Typography().bodyLarge.copy(
        fontSize = 17.sp,
        lineHeight = 28.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.15.sp
    ),
    bodyMedium = Typography().bodyMedium.copy(
        fontSize = 16.sp,
        lineHeight = 26.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.1.sp
    ),
    titleLarge = Typography().titleLarge.copy(
        fontFamily = FontFamily.Serif,
        fontSize = 24.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.sp
    ),
    titleMedium = Typography().titleMedium.copy(
        fontFamily = FontFamily.Serif,
        fontSize = 20.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.1.sp
    ),
    titleSmall = Typography().titleSmall.copy(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.1.sp
    ),
    labelSmall = Typography().labelSmall.copy(
        fontSize = 11.sp,
        fontWeight = FontWeight.Light,
        letterSpacing = 0.3.sp
    )
)

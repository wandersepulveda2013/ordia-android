package com.ordia.app.ui.theme

import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Sans = FontFamily.SansSerif

val OrdiaTypography = Typography(
    displayLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 54.sp, lineHeight = 64.sp, letterSpacing = (-1.5).sp),
    displayMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 42.sp, lineHeight = 52.sp, letterSpacing = (-1.0).sp),
    headlineLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 32.sp, lineHeight = 40.sp, letterSpacing = (-0.5).sp),
    headlineMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 26.sp, lineHeight = 34.sp, letterSpacing = 0.sp),
    headlineSmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 22.sp, lineHeight = 30.sp, letterSpacing = 0.sp),
    titleLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 20.sp, lineHeight = 28.sp, letterSpacing = 0.sp),
    titleMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.1.sp),
    titleSmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
    bodyLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 26.sp, letterSpacing = 0.15.sp),
    bodyMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 22.sp, letterSpacing = 0.25.sp),
    bodySmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 18.sp, letterSpacing = 0.4.sp),
    labelLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
    labelMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp),
    labelSmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp)
)

val OrdiaShapes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(26.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(34.dp)
)

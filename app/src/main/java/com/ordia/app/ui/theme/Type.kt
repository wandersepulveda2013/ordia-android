package com.ordia.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp

val Typography = Typography().let { base ->
    Typography(
        bodyLarge = base.bodyLarge.copy(fontSize = 17.sp, lineHeight = 28.sp),
        bodyMedium = base.bodyMedium.copy(fontSize = 16.sp, lineHeight = 26.sp),
        titleLarge = base.titleLarge.copy(fontFamily = FontFamily.Serif, fontSize = 24.sp),
        titleMedium = base.titleMedium.copy(fontFamily = FontFamily.Serif, fontSize = 20.sp),
        titleSmall = base.titleSmall.copy(fontFamily = FontFamily.Serif),
        labelSmall = base.labelSmall.copy(fontSize = 11.sp),
    )
}

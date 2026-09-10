package com.radustavila.nasaexplorer.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

internal val NasaTypography = Typography().let { defaults ->
    defaults.copy(
        headlineSmall = defaults.headlineSmall.copy(
            fontWeight = FontWeight.SemiBold,
            letterSpacing = (-0.2).sp,
        ),
        titleMedium = defaults.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelSmall = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Medium,
            fontSize = 11.sp,
            letterSpacing = 0.6.sp,
        ),
    )
}

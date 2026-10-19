package com.radustavila.nasaexplorer.feature.spaceweather.components

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import com.radustavila.nasaexplorer.core.model.Kp
import com.radustavila.nasaexplorer.feature.spaceweather.R
import java.util.Locale

/** NOAA-style colours by whole Kp: greens while quiet, yellow to deep red through G1–G5. */
internal fun kpColor(level: Int): Color = when (level.coerceIn(0, Kp.MAX)) {
    0, 1, 2 -> Color(0xFF4CAF50)
    3 -> Color(0xFF8BC34A)
    4 -> Color(0xFFCDDC39)
    5 -> Color(0xFFFFEB3B)
    6 -> Color(0xFFFFC107)
    7 -> Color(0xFFFF9800)
    8 -> Color(0xFFFF5722)
    else -> Color(0xFFD32F2F)
}

/** What a whole Kp means, in words. */
@StringRes
internal fun kpDescription(level: Int): Int = when (level.coerceIn(0, Kp.MAX)) {
    0, 1 -> R.string.space_weather_kp_quiet
    2, 3 -> R.string.space_weather_kp_unsettled
    4 -> R.string.space_weather_kp_active
    5 -> R.string.space_weather_kp_g1
    6 -> R.string.space_weather_kp_g2
    7 -> R.string.space_weather_kp_g3
    8 -> R.string.space_weather_kp_g4
    else -> R.string.space_weather_kp_g5
}

/** The locale numbers and day names are formatted in; recomposes when the user changes it. */
@Composable
@ReadOnlyComposable
internal fun currentLocale(): Locale = LocalConfiguration.current.locales[0]

package com.radustavila.nasaexplorer.feature.spaceweather.neighbourhood

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.radustavila.nasaexplorer.core.designsystem.units.UnitFormatter
import com.radustavila.nasaexplorer.core.model.SizeEstimate
import com.radustavila.nasaexplorer.feature.spaceweather.R
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.roundToLong

/** "7.6" lunar distances: one decimal below 10, whole numbers above. LD is the same in every unit system. */
internal fun formatLunarDistance(lunarDistances: Double, locale: Locale): String =
    NumberFormat.getNumberInstance(locale).apply {
        val decimals = if (lunarDistances < 10) 1 else 0
        minimumFractionDigits = decimals
        maximumFractionDigits = decimals
    }.format(lunarDistances)

/** "34–75 m", "340 m" or "1.2 km" (feet and miles in imperial): the estimate is rough anyway. */
internal fun SizeEstimate.format(units: UnitFormatter): String =
    if (isRange) units.length(minMeters, maxMeters) else units.length(minMeters)

/** An impact probability as odds against: 1.59e-4 → 6,300 (for "1 in 6,300"); null for no chance. */
internal fun oddsAgainst(probability: Double): Long? =
    if (probability <= 0) null else roundToTwoSignificant(1 / probability)

/** "1 in 6,300", or "none" when the probability is zero. */
@Composable
internal fun formatOdds(probability: Double, locale: Locale): String {
    val odds = oddsAgainst(probability) ?: return stringResource(R.string.small_body_odds_none)
    return stringResource(R.string.small_body_odds, NumberFormat.getIntegerInstance(locale).format(odds))
}

private fun roundToTwoSignificant(value: Double): Long {
    if (value < 100) return value.roundToLong()
    var scale = 1L
    var scaled = value
    while (scaled >= 100) {
        scaled /= 10
        scale *= 10
    }
    return scaled.roundToLong() * scale
}

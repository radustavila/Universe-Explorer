package com.radustavila.nasaexplorer.feature.spaceweather.neighbourhood

import com.radustavila.nasaexplorer.core.model.SizeEstimate
import java.util.Locale
import kotlin.math.roundToLong

/** "7.6" lunar distances: one decimal below 10, whole numbers above. */
internal fun formatLunarDistance(lunarDistances: Double, locale: Locale): String =
    if (lunarDistances < 10) String.format(locale, "%.1f", lunarDistances) else String.format(locale, "%.0f", lunarDistances)

/** "2.9 million" km, or "748,000" below a million. */
internal fun formatKilometres(km: Double, locale: Locale): String =
    if (km >= MILLION) {
        String.format(locale, "%.1f million", km / MILLION)
    } else {
        String.format(locale, "%,d", (km / THOUSAND).roundToLong() * THOUSAND.toLong())
    }

/** "34–75 m", "340 m" or "1.2 km": two significant figures, as the estimate is rough anyway. */
internal fun formatSize(size: SizeEstimate, locale: Locale): String {
    val unitIsKm = size.maxMeters >= THOUSAND
    fun value(meters: Double): String = if (unitIsKm) {
        String.format(locale, "%.1f", meters / THOUSAND)
    } else {
        roundToTwoSignificant(meters).toString()
    }
    val unit = if (unitIsKm) "km" else "m"
    return if (size.isRange) "${value(size.minMeters)}–${value(size.maxMeters)} $unit" else "${value(size.minMeters)} $unit"
}

/** An impact probability as odds: 1.59e-4 → "1 in 6,300". */
internal fun formatOdds(probability: Double, locale: Locale): String {
    if (probability <= 0) return "0"
    val odds = roundToTwoSignificant(1 / probability)
    return String.format(locale, "1 in %,d", odds)
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

private const val MILLION = 1_000_000.0
private const val THOUSAND = 1_000.0

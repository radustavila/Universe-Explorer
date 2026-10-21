package com.radustavila.nasaexplorer.feature.widget.aurora

import android.content.res.Resources
import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import com.radustavila.nasaexplorer.core.model.AuroraForecast
import com.radustavila.nasaexplorer.core.model.AuroraVisibility
import com.radustavila.nasaexplorer.core.model.CurrentKp
import com.radustavila.nasaexplorer.core.model.GeoCoordinate
import com.radustavila.nasaexplorer.core.model.Kp
import com.radustavila.nasaexplorer.core.model.KpReading
import com.radustavila.nasaexplorer.feature.widget.R
import com.radustavila.nasaexplorer.feature.widget.WidgetFormat
import java.time.Duration
import java.time.Instant
import kotlin.math.roundToInt

/** Everything the aurora widget shows, already in words. */
internal data class AuroraWidgetState(
    /** Current Kp with NOAA's thirds, e.g. "4.3". */
    val kp: String,
    /** Whole Kp 0..9, for the colour scale. */
    val level: Int,
    /** "Quiet" … "Extreme storm". */
    val activity: String,
    /** The forecast's highest Kp in the coming hours and when; null without a forecast. */
    val peak: String?,
    /** The aurora chance, or the Kp needed, at the saved place; null without a place. */
    val local: String?,
    /** When the Kp reading is from, and how old it is once it is no longer current. */
    val updated: String,
    /** The reading is older than [AURORA_STALE_AFTER]: the widget dims the number. */
    val isStale: Boolean,
    val contentDescription: String,
)

/** The widget's own refresh comes from the space weather sync, which may not run for hours. */
internal val AURORA_STALE_AFTER: Duration = Duration.ofHours(3)

/** How far ahead the peak is looked for; the same horizon aurora alerts use. */
internal val AURORA_PEAK_HORIZON: Duration = Duration.ofHours(24)

/**
 * OVATION is a nowcast for the next half hour or so; a grid whose target time is longer ago than
 * this says nothing about tonight.
 */
internal val AURORA_MAP_MAX_AGE: Duration = Duration.ofHours(1)

/**
 * Maps the cached space weather to the widget's text, or null when nothing is cached yet.
 *
 * "Now" is the 1-minute Kp estimate, falling back to the latest 3-hour slot that has begun.
 */
internal fun auroraWidgetState(
    current: CurrentKp?,
    forecast: List<KpReading>,
    auroraMap: AuroraForecast?,
    place: GeoCoordinate?,
    now: Instant,
    format: WidgetFormat,
    resources: Resources,
): AuroraWidgetState? {
    val latestSlot = forecast.filter { !it.start.isAfter(now) }.maxByOrNull { it.start }
    val kp = current?.kp ?: latestSlot?.kp ?: return null
    val readingTime = current?.time ?: checkNotNull(latestSlot).start
    val level = Kp.level(kp)

    val peakSlot = forecast
        .filter { it.end.isAfter(now) && it.start.isBefore(now.plus(AURORA_PEAK_HORIZON)) }
        .maxByOrNull { it.kp }
    val peak = peakSlot?.let { slot ->
        val time = if (slot.start.isAfter(now)) {
            format.dayAndTime(slot.start, now)
        } else {
            resources.getString(R.string.aurora_widget_now)
        }
        resources.getString(R.string.aurora_widget_peak, slot.level, time)
    }

    val isStale = Duration.between(readingTime, now) > AURORA_STALE_AFTER
    val updated = if (isStale) {
        resources.getString(R.string.aurora_widget_updated_old, format.time(readingTime), format.age(readingTime, now))
    } else {
        resources.getString(R.string.aurora_widget_updated, format.time(readingTime))
    }

    val local = place?.let { localOutlook(it, auroraMap, peakSlot?.level ?: level, now, resources) }
    val activity = resources.getString(activityLabel(level))
    val kpText = format.decimal(kp, fractionDigits = 1)

    return AuroraWidgetState(
        kp = kpText,
        level = level,
        activity = activity,
        peak = peak,
        local = local,
        updated = updated,
        isStale = isStale,
        contentDescription = listOfNotNull(
            resources.getString(R.string.aurora_widget_kp_description, kpText, activity),
            peak,
            local,
            updated,
        ).joinToString(separator = ". "),
    )
}

/**
 * The OVATION chance at the place while the grid is current, otherwise what the Kp table says:
 * whether the coming peak reaches the place, or the Kp it would take.
 */
private fun localOutlook(
    place: GeoCoordinate,
    auroraMap: AuroraForecast?,
    peakLevel: Int,
    now: Instant,
    resources: Resources,
): String {
    if (auroraMap != null && !auroraMap.forecastFor.plus(AURORA_MAP_MAX_AGE).isBefore(now)) {
        val chance = auroraMap.probabilityAt(
            longitude = Math.floorMod(place.longitude.roundToInt(), AuroraForecast.LONGITUDES),
            latitude = place.latitude.roundToInt(),
        )
        return resources.getString(R.string.aurora_widget_chance_here, chance)
    }
    val minimumKp = AuroraVisibility.minimumKp(place.latitude, place.longitude)
        ?: return resources.getString(R.string.aurora_widget_out_of_reach)
    return if (peakLevel >= minimumKp) {
        resources.getString(R.string.aurora_widget_reaches_you, minimumKp)
    } else {
        resources.getString(R.string.aurora_widget_needs_kp, minimumKp)
    }
}

/** What a whole Kp means, in the space weather screen's words. */
@StringRes
private fun activityLabel(level: Int): Int = when (level.coerceIn(0, Kp.MAX)) {
    0, 1 -> R.string.aurora_widget_kp_quiet
    2, 3 -> R.string.aurora_widget_kp_unsettled
    4 -> R.string.aurora_widget_kp_active
    5 -> R.string.aurora_widget_kp_g1
    6 -> R.string.aurora_widget_kp_g2
    7 -> R.string.aurora_widget_kp_g3
    8 -> R.string.aurora_widget_kp_g4
    else -> R.string.aurora_widget_kp_g5
}

/**
 * NOAA-style colours by whole Kp: greens while quiet, yellow to deep red through G1–G5. The same
 * scale as the space weather screen's gauge (`KpStyle.kt` in `:feature:spaceweather`, which feature
 * modules cannot share).
 */
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

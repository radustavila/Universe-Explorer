package com.radustavila.nasaexplorer.feature.widget.sky

import android.content.res.Resources
import androidx.annotation.StringRes
import com.radustavila.nasaexplorer.core.domain.sky.IssPassForecast
import com.radustavila.nasaexplorer.core.model.MoonPhase
import com.radustavila.nasaexplorer.core.model.MoonTonight
import com.radustavila.nasaexplorer.core.model.ObserverLocation
import com.radustavila.nasaexplorer.feature.widget.R
import com.radustavila.nasaexplorer.feature.widget.WidgetFormat
import java.time.Instant
import kotlin.math.roundToInt

/** Everything the sky tonight widget shows, already in words. */
internal data class SkyTonightWidgetState(
    val phaseAngleDegrees: Double,
    /** Draw the Moon as seen from the southern hemisphere. */
    val southernHemisphere: Boolean,
    /** "Waxing gibbous". */
    val phase: String,
    /** "78% lit". */
    val illumination: String,
    /** "Full moon Fri 24 Oct". */
    val nextFullMoon: String,
    /** Where the pass is for: the place's name, or null for coordinates or no place. */
    val placeName: String?,
    /** The next visible ISS pass, or why there is none to show. */
    val iss: String,
    /** Whether [iss] is an actual pass rather than an explanation. */
    val hasIssPass: Boolean,
    val contentDescription: String,
)

/**
 * Maps tonight's Moon and the ISS forecast to the widget's text.
 *
 * The Moon's phase does not depend on the place, so it shows even before the user has saved one;
 * the ISS pass needs the place, and its [issForecast] is null without one.
 */
internal fun skyTonightWidgetState(
    moon: MoonTonight,
    location: ObserverLocation?,
    issForecast: IssPassForecast?,
    now: Instant,
    format: WidgetFormat,
    resources: Resources,
): SkyTonightWidgetState {
    val phase = resources.getString(phaseName(moon.phase))
    val litPercent = (moon.illuminatedFraction * PERCENT).roundToInt()
    val illumination = resources.getString(R.string.sky_widget_illuminated, litPercent)
    val nextFullMoon = resources.getString(R.string.sky_widget_next_full_moon, format.day(moon.nextFullMoon))

    val nextPass = issForecast?.passes?.firstOrNull { it.isVisible && it.end.isAfter(now) }
    val iss = when {
        location == null || issForecast == null -> resources.getString(R.string.sky_widget_iss_no_place)
        issForecast.elements == null -> resources.getString(R.string.sky_widget_iss_no_elements)
        issForecast.isStale -> resources.getString(R.string.sky_widget_iss_stale)
        nextPass == null -> resources.getString(R.string.sky_widget_iss_no_visible)
        else -> resources.getString(
            R.string.sky_widget_iss_pass,
            format.weekdayAndTime(nextPass.start, now),
            nextPass.maxElevationDegrees.roundToInt(),
        )
    }

    return SkyTonightWidgetState(
        phaseAngleDegrees = moon.phaseAngleDegrees,
        southernHemisphere = (location?.coordinate?.latitude ?: 0.0) < 0.0,
        phase = phase,
        illumination = illumination,
        nextFullMoon = nextFullMoon,
        placeName = location?.name,
        iss = iss,
        hasIssPass = nextPass != null,
        contentDescription = listOf(phase, illumination, nextFullMoon, iss).joinToString(separator = ". "),
    )
}

@StringRes
private fun phaseName(phase: MoonPhase): Int = when (phase) {
    MoonPhase.NEW_MOON -> R.string.sky_widget_phase_new
    MoonPhase.WAXING_CRESCENT -> R.string.sky_widget_phase_waxing_crescent
    MoonPhase.FIRST_QUARTER -> R.string.sky_widget_phase_first_quarter
    MoonPhase.WAXING_GIBBOUS -> R.string.sky_widget_phase_waxing_gibbous
    MoonPhase.FULL_MOON -> R.string.sky_widget_phase_full
    MoonPhase.WANING_GIBBOUS -> R.string.sky_widget_phase_waning_gibbous
    MoonPhase.LAST_QUARTER -> R.string.sky_widget_phase_last_quarter
    MoonPhase.WANING_CRESCENT -> R.string.sky_widget_phase_waning_crescent
}

private const val PERCENT = 100

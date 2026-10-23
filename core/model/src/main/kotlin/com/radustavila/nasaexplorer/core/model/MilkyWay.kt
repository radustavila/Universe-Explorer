package com.radustavila.nasaexplorer.core.model

import java.time.Duration
import java.time.Instant
import java.time.Month
import kotlin.math.abs

/** The centre of our galaxy, the bright core photographers point their cameras at. */
object GalacticCentre {

    /** Sgr A*, J2000: RA 17h 45m 40.04s, Dec −29° 00′ 28.2″ (SIMBAD, checked 2026-10-23). */
    val POSITION = EquatorialCoordinate(rightAscensionDegrees = 266.41683, declinationDegrees = -29.00782)

    /** How high the core must be to count: lower, haze and the ground swallow it. */
    const val MIN_ALTITUDE_DEGREES = 10.0

    /** The Moon still counts as dark below this lit fraction, even when it is up. */
    const val MAX_MOON_FRACTION = 0.10

    private const val RIGHT_ANGLE = 90.0

    /** The highest the core ever gets seen from [latitude], degrees (negative: it never rises). */
    fun highestAltitude(latitude: Double): Double = RIGHT_ANGLE - abs(latitude - POSITION.declinationDegrees)
}

/** From [start] to [end]. */
data class TimeInterval(val start: Instant, val end: Instant) {
    val duration: Duration get() = Duration.between(start, end)
}

/**
 * The soft light photographers plan around, for one evening or morning: the golden hour (Sun from
 * 6° above to 4° below the horizon) and the blue hour (4° to 6° below). Null where the Sun does
 * not pass through that band that day.
 */
data class PhotoLight(
    val goldenHour: TimeInterval?,
    val blueHour: TimeInterval?,
)

/** The best time to photograph the core one night: dark, with the core high and no bright Moon. */
data class MilkyWayWindow(
    val start: Instant,
    val end: Instant,
    /** When the core is highest within the window. */
    val best: Instant,
    /** Where the core is at [best], with refraction. */
    val coreAtBest: HorizontalCoordinate,
) {
    val duration: Duration get() = Duration.between(start, end)
}

/**
 * One night for Milky Way photography, from sunset to sunrise (or a day where the Sun does not
 * set or rise). Every interval lies within [start] to [end].
 */
data class MilkyWayNight(
    val start: Instant,
    val end: Instant,
    /** Sunset, sunrise and the civil, nautical and astronomical twilights. */
    val twilight: NightWindow,
    val evening: PhotoLight,
    val morning: PhotoLight,
    val darkness: NightDarkness,
    /** Lit fraction of the Moon in the middle of the night, 0–1. */
    val moonIlluminatedFraction: Double,
    /** When the Moon is above the horizon. */
    val moonUp: List<TimeInterval>,
    /** When the core is at least [GalacticCentre.MIN_ALTITUDE_DEGREES] high. */
    val coreUp: List<TimeInterval>,
    /** When the core clears the horizon, culminates and sets during the night; null outside it. */
    val coreRise: Instant?,
    val coreTransit: Instant?,
    val coreSet: Instant?,
    /** Null when there is no dark, moonless stretch with the core up. */
    val window: MilkyWayWindow?,
)

/** Why there is no Milky Way window in the coming nights. */
enum class MilkyWayUnavailable {
    /** The core never gets [GalacticCentre.MIN_ALTITUDE_DEGREES] high at this latitude. */
    CORE_TOO_LOW,

    /** The Sun never gets 18° below the horizon (summer far from the equator). */
    NO_DARKNESS,

    /** It does get dark, but the core is up only by day this time of year. */
    CORE_UP_BY_DAY,

    /** The core is up in the dark, but a bright Moon is up with it every night. */
    MOONLIGHT,
}

/** The coming nights at one place, with when in the year the core is worth going out for. */
data class MilkyWayOutlook(
    val observer: GeoCoordinate,
    val nights: List<MilkyWayNight>,
    /** Months with dark hours while the core is up, ignoring the Moon. */
    val season: Set<Month>,
) {
    val coreHighestAltitudeDegrees: Double get() = GalacticCentre.highestAltitude(observer.latitude)

    /** The first night with a window, tonight included. */
    val nextWindowNight: MilkyWayNight? get() = nights.firstOrNull { it.window != null }

    /** Why no night has a window; null when one does. */
    val unavailable: MilkyWayUnavailable?
        get() = when {
            nextWindowNight != null -> null
            coreHighestAltitudeDegrees < GalacticCentre.MIN_ALTITUDE_DEGREES -> MilkyWayUnavailable.CORE_TOO_LOW
            nights.none { it.darkness == NightDarkness.ASTRONOMICAL } -> MilkyWayUnavailable.NO_DARKNESS
            nights.none { it.hasCoreInTheDark() } -> MilkyWayUnavailable.CORE_UP_BY_DAY
            else -> MilkyWayUnavailable.MOONLIGHT
        }
}

/** Whether the core is up while the Sun is 18° down, whatever the Moon does. */
private fun MilkyWayNight.hasCoreInTheDark(): Boolean {
    val darkStart = twilight.astronomicalDusk ?: start.takeIf { darkness == NightDarkness.ASTRONOMICAL }
    val darkEnd = twilight.astronomicalDawn ?: end.takeIf { darkness == NightDarkness.ASTRONOMICAL }
    if (darkness != NightDarkness.ASTRONOMICAL || darkStart == null || darkEnd == null) return false
    return coreUp.any { it.start < darkEnd && it.end > darkStart }
}

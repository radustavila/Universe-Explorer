package com.radustavila.nasaexplorer.core.testing.data

import com.radustavila.nasaexplorer.core.model.GeoCoordinate
import com.radustavila.nasaexplorer.core.model.HorizontalCoordinate
import com.radustavila.nasaexplorer.core.model.MilkyWayNight
import com.radustavila.nasaexplorer.core.model.MilkyWayNightEvent
import com.radustavila.nasaexplorer.core.model.MilkyWayWindow
import com.radustavila.nasaexplorer.core.model.NightDarkness
import com.radustavila.nasaexplorer.core.model.NightWindow
import com.radustavila.nasaexplorer.core.model.PhotoLight
import com.radustavila.nasaexplorer.core.model.TimeInterval
import java.time.Duration
import java.time.Instant
import java.time.Month
import kotlin.math.cos

/**
 * Milky Way planner nights shaped like Madrid's from 10 July 2026 (sunset 19:46 UTC, new Moon on
 * the fourth night), with fixed numbers so screens and tests do not depend on the ephemeris.
 */
object TestMilkyWay {

    val MADRID = GeoCoordinate(latitude = 40.42, longitude = -3.70)
    val FIRST_SUNSET: Instant = Instant.parse("2026-07-10T19:46:00Z")
    val SEASON: Set<Month> = (Month.MARCH.value..Month.OCTOBER.value).map(Month::of).toSet()

    private const val NEW_MOON_NIGHT = 4
    private const val LUNAR_MONTH_DAYS = 29.5
    private const val MOON_FRACTION_LIMIT = 0.10
    private const val CORE_TRANSIT_MINUTES = 240L
    private const val GOLDEN_HOUR_BEFORE_SUNSET = 42L
    private const val BLUE_HOUR_AFTER_SUNSET = 10L
    private const val MORNING_GOLDEN_HOUR_START = 537L
    private const val MORNING_GOLDEN_HOUR_END = 590L
    private const val CORE_SET_MINUTES = 420L
    private const val CORE_DRIFT_MINUTES = 4L
    private const val CORE_AZIMUTH = 184.0
    private val MIN_WINDOW: Duration = Duration.ofMinutes(15)

    fun nights(count: Int = 30, first: Instant = FIRST_SUNSET): List<MilkyWayNight> =
        (0 until count).map { night(it, first) }

    /** Night [index] after [first]: the core is up a little earlier and the Moon about 50 minutes later each night. */
    fun night(index: Int, first: Instant = FIRST_SUNSET): MilkyWayNight {
        val sunset = first.plus(Duration.ofDays(index.toLong())).minus(Duration.ofMinutes(index.toLong()))
        val twilight = NightWindow(
            sunset = sunset,
            civilDusk = sunset.plus(minutes(32)),
            nauticalDusk = sunset.plus(minutes(73)),
            astronomicalDusk = sunset.plus(minutes(121)),
            astronomicalDawn = sunset.plus(minutes(427)),
            nauticalDawn = sunset.plus(minutes(474)),
            civilDawn = sunset.plus(minutes(515)),
            sunrise = sunset.plus(minutes(548)),
        )
        val end = checkNotNull(twilight.sunrise)
        val moonFraction = (1 - cos(2 * Math.PI * (index - NEW_MOON_NIGHT) / LUNAR_MONTH_DAYS)) / 2
        // At new Moon the Moon crosses the meridian at noon, then about 50 minutes later a night.
        val moonTransit = sunset.minus(minutes(466)).plus(minutes(50L * (index - NEW_MOON_NIGHT)))
        val moonUp = clip(TimeInterval(moonTransit.minus(minutes(370)), moonTransit.plus(minutes(370))), sunset, end)
        val coreUp = clip(
            TimeInterval(sunset.plus(minutes(60L - 4 * index)), sunset.plus(minutes(330L - 4 * index))),
            sunset,
            end,
        )
        val eveningBlue = sunset.plus(minutes(BLUE_HOUR_AFTER_SUNSET))
        val morningGolden = sunset.plus(minutes(MORNING_GOLDEN_HOUR_START))
        return MilkyWayNight(
            start = sunset,
            end = end,
            twilight = twilight,
            evening = PhotoLight(
                goldenHour = TimeInterval(sunset.minus(minutes(GOLDEN_HOUR_BEFORE_SUNSET)), eveningBlue),
                blueHour = TimeInterval(eveningBlue, checkNotNull(twilight.civilDusk)),
            ),
            morning = PhotoLight(
                blueHour = TimeInterval(checkNotNull(twilight.civilDawn), morningGolden),
                goldenHour = TimeInterval(morningGolden, sunset.plus(minutes(MORNING_GOLDEN_HOUR_END))),
            ),
            darkness = NightDarkness.ASTRONOMICAL,
            moonIlluminatedFraction = moonFraction,
            moonUp = listOfNotNull(moonUp),
            coreUp = listOfNotNull(coreUp),
            coreRise = null,
            coreTransit = coreTransit(sunset, index),
            coreSet = sunset.plus(minutes(CORE_SET_MINUTES - CORE_DRIFT_MINUTES * index)),
            window = window(twilight, coreUp, moonUp.takeIf { moonFraction >= MOON_FRACTION_LIMIT }, sunset, index),
        )
    }

    /** The sky events calendar's best Milky Way night around August's new Moon (F12). */
    fun milkyWayNight() = MilkyWayNightEvent(
        time = Instant.parse("2026-08-12T19:05:00Z"),
        end = Instant.parse("2026-08-12T22:40:00Z"),
        best = Instant.parse("2026-08-12T19:05:00Z"),
        coreAltitudeDegrees = 16.4,
        coreAzimuthDegrees = 192.0,
        moonIlluminatedFraction = 0.01,
    )

    /** A night where the Sun stays up (Tromsø in June). */
    fun polarDay(start: Instant = FIRST_SUNSET): MilkyWayNight = MilkyWayNight(
        start = start,
        end = start.plus(Duration.ofDays(1)),
        twilight = NightWindow(null, null, null, null, null, null, null, null),
        evening = PhotoLight(null, null),
        morning = PhotoLight(null, null),
        darkness = NightDarkness.NONE,
        moonIlluminatedFraction = 0.5,
        moonUp = emptyList(),
        coreUp = emptyList(),
        coreRise = null,
        coreTransit = null,
        coreSet = null,
        window = null,
    )

    private fun window(
        twilight: NightWindow,
        coreUp: TimeInterval?,
        moonUp: TimeInterval?,
        sunset: Instant,
        index: Int,
    ): MilkyWayWindow? {
        if (coreUp == null) return null
        val dark = TimeInterval(checkNotNull(twilight.astronomicalDusk), checkNotNull(twilight.astronomicalDawn))
        var start = maxOf(dark.start, coreUp.start)
        var end = minOf(dark.end, coreUp.end)
        if (moonUp != null) {
            // The Moon either sets during the window or rises in it: keep the longer moonless side.
            val before = if (moonUp.start > start) minOf(end, moonUp.start) else start
            val after = if (moonUp.end < end) maxOf(start, moonUp.end) else end
            if (Duration.between(start, before) >= Duration.between(after, end)) end = before else start = after
        }
        val best = minOf(end, maxOf(start, coreTransit(sunset, index)))
        return MilkyWayWindow(
            start = start,
            end = end,
            best = best,
            coreAtBest = HorizontalCoordinate(altitudeDegrees = 20.4, azimuthDegrees = CORE_AZIMUTH + index),
        ).takeIf { it.duration >= MIN_WINDOW }
    }

    /** The core culminates about four minutes earlier each night. */
    private fun coreTransit(sunset: Instant, index: Int): Instant =
        sunset.plus(minutes(CORE_TRANSIT_MINUTES - CORE_DRIFT_MINUTES * index))

    private fun clip(interval: TimeInterval, from: Instant, until: Instant): TimeInterval? {
        val start = maxOf(interval.start, from)
        val end = minOf(interval.end, until)
        return if (end.isAfter(start)) TimeInterval(start, end) else null
    }

    private fun minutes(value: Long): Duration = Duration.ofMinutes(value)
}

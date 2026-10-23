package com.radustavila.nasaexplorer.core.astro

import com.radustavila.nasaexplorer.core.model.NightWindow
import com.radustavila.nasaexplorer.core.model.PhotoLight
import com.radustavila.nasaexplorer.core.model.TimeInterval
import io.github.cosinekitty.astronomy.Body
import io.github.cosinekitty.astronomy.Direction
import io.github.cosinekitty.astronomy.Observer
import io.github.cosinekitty.astronomy.Time
import io.github.cosinekitty.astronomy.searchAltitude
import java.time.Instant

/**
 * The Sun's twilight stages through one night, shared by Sky tonight (F5) and the Milky Way
 * planner (F20). Altitudes are of the Sun's centre without refraction, as twilight is defined.
 */
internal object Twilight {

    const val CIVIL = -6.0
    const val NAUTICAL = -12.0
    const val ASTRONOMICAL = -18.0

    /** Golden hour: the Sun from this high down to [BLUE_HOUR_TOP]. */
    private const val GOLDEN_HOUR_TOP = 6.0

    /** Blue hour: the Sun from this low down to [CIVIL]. */
    private const val BLUE_HOUR_TOP = -4.0

    /** The golden hour can start long before sunset far from the equator. */
    private const val GOLDEN_HOUR_SEARCH_DAYS = 0.5

    /**
     * The twilights of the night from [start] (the evening's sunset, or the start of a day without
     * one) to [end] (the next sunrise, or a day later). A stage the Sun does not reach is null.
     */
    fun nightWindow(
        site: Observer,
        sunset: Instant?,
        sunrise: Instant?,
        start: Time,
        end: Time,
        searchDays: Double,
    ): NightWindow = NightWindow(
        sunset = sunset,
        civilDusk = dusk(site, start, end, searchDays, CIVIL),
        nauticalDusk = dusk(site, start, end, searchDays, NAUTICAL),
        astronomicalDusk = dusk(site, start, end, searchDays, ASTRONOMICAL),
        astronomicalDawn = dawn(site, start, end, searchDays, ASTRONOMICAL),
        nauticalDawn = dawn(site, start, end, searchDays, NAUTICAL),
        civilDawn = dawn(site, start, end, searchDays, CIVIL),
        sunrise = sunrise,
    )

    /** The golden and blue hours around [night]'s sunset; none without one. */
    fun evening(site: Observer, night: NightWindow, end: Time, searchDays: Double): PhotoLight {
        val sunset = night.sunset ?: return PhotoLight(goldenHour = null, blueHour = null)
        val start = sunset.toAstronomyTime()
        val goldenStart = searchAltitude(
            Body.Sun,
            site,
            Direction.Set,
            start.addDays(-GOLDEN_HOUR_SEARCH_DAYS),
            GOLDEN_HOUR_SEARCH_DAYS,
            GOLDEN_HOUR_TOP,
        )?.toInstant()
        val blueStart = dusk(site, start, end, searchDays, BLUE_HOUR_TOP)
        return PhotoLight(
            goldenHour = interval(goldenStart, blueStart),
            blueHour = interval(blueStart, night.civilDusk),
        )
    }

    /** The blue and golden hours around [night]'s sunrise; none without one. */
    fun morning(site: Observer, night: NightWindow, start: Time, searchDays: Double): PhotoLight {
        val sunrise = night.sunrise ?: return PhotoLight(goldenHour = null, blueHour = null)
        val end = sunrise.toAstronomyTime()
        val blueEnd = dawn(site, start, end, searchDays, BLUE_HOUR_TOP)
        val goldenEnd = searchAltitude(Body.Sun, site, Direction.Rise, end, GOLDEN_HOUR_SEARCH_DAYS, GOLDEN_HOUR_TOP)
            ?.toInstant()
        return PhotoLight(
            goldenHour = interval(blueEnd, goldenEnd),
            blueHour = interval(night.civilDawn, blueEnd),
        )
    }

    private fun dusk(site: Observer, start: Time, end: Time, searchDays: Double, altitude: Double): Instant? =
        searchAltitude(Body.Sun, site, Direction.Set, start, searchDays, altitude)
            ?.takeIf { it.ut < end.ut }
            ?.toInstant()

    private fun dawn(site: Observer, start: Time, end: Time, searchDays: Double, altitude: Double): Instant? =
        searchAltitude(Body.Sun, site, Direction.Rise, start, searchDays, altitude)
            ?.takeIf { it.ut <= end.ut }
            ?.toInstant()

    private fun interval(start: Instant?, end: Instant?): TimeInterval? =
        if (start != null && end != null && end.isAfter(start)) TimeInterval(start, end) else null
}

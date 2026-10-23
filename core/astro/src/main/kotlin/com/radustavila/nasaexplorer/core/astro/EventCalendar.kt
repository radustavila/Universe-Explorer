package com.radustavila.nasaexplorer.core.astro

import com.radustavila.nasaexplorer.core.domain.sky.MilkyWayCalculator
import com.radustavila.nasaexplorer.core.domain.sky.SkyEventCalendar
import com.radustavila.nasaexplorer.core.model.GeoCoordinate
import com.radustavila.nasaexplorer.core.model.MilkyWayNightEvent
import com.radustavila.nasaexplorer.core.model.MoonPhaseEvent
import com.radustavila.nasaexplorer.core.model.MoonQuarter
import com.radustavila.nasaexplorer.core.model.MoonSize
import com.radustavila.nasaexplorer.core.model.SeasonEvent
import com.radustavila.nasaexplorer.core.model.SeasonMarker
import com.radustavila.nasaexplorer.core.model.SkyEvent
import com.radustavila.nasaexplorer.core.model.SkyEventCategory
import io.github.cosinekitty.astronomy.ApsisInfo
import io.github.cosinekitty.astronomy.ApsisKind
import io.github.cosinekitty.astronomy.Observer
import io.github.cosinekitty.astronomy.Time
import io.github.cosinekitty.astronomy.geoMoon
import io.github.cosinekitty.astronomy.nextLunarApsis
import io.github.cosinekitty.astronomy.nextMoonQuarter
import io.github.cosinekitty.astronomy.searchLunarApsis
import io.github.cosinekitty.astronomy.searchMoonPhase
import io.github.cosinekitty.astronomy.searchMoonQuarter
import io.github.cosinekitty.astronomy.seasons
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import kotlin.math.abs

/**
 * [SkyEventCalendar] on Astronomy Engine: every search runs on the device, for any year.
 * Eclipses, planet events and meteor showers are found by [EclipseSearch], [PlanetEventSearch]
 * and [MeteorShowerSearch]; Moon phases and seasons here, and the best Milky Way night around each
 * new Moon through [milkyWay] (F20).
 *
 * Accuracy is Astronomy Engine's (about a minute for phases, seasons and eclipse contacts). Meteor
 * shower peaks come from [MeteorShowerTable] and are good to a few hours, as are the showers
 * themselves.
 */
class EventCalendar @Inject constructor(
    private val milkyWay: MilkyWayCalculator,
) : SkyEventCalendar {

    override fun events(
        from: Instant,
        until: Instant,
        observer: GeoCoordinate?,
        categories: Set<SkyEventCategory>,
    ): List<SkyEvent> {
        if (!until.isAfter(from)) return emptyList()
        val site = observer?.let { Observer(it.latitude, it.longitude, 0.0) }
        val range = from..until
        val events = buildList {
            if (SkyEventCategory.ECLIPSES in categories) {
                addAll(EclipseSearch.lunarEclipses(range, site))
                addAll(EclipseSearch.solarEclipses(range, site))
            }
            if (SkyEventCategory.MOON in categories) addAll(moonPhases(range))
            if (SkyEventCategory.PLANETS in categories) {
                addAll(PlanetEventSearch.oppositions(range))
                addAll(PlanetEventSearch.elongations(range))
                addAll(PlanetEventSearch.transits(range, site))
            }
            if (SkyEventCategory.METEORS in categories) addAll(MeteorShowerSearch.peaks(range, site))
            if (SkyEventCategory.SEASONS in categories) addAll(seasonEvents(range))
            if (SkyEventCategory.MILKY_WAY in categories && observer != null) addAll(milkyWayNights(range, observer))
        }
        // The range's end is exclusive.
        return events.filter { it.time >= from && it.time < until }.sortedBy { it.time }
    }

    private fun moonPhases(range: ClosedRange<Instant>): List<MoonPhaseEvent> =
        generateSequence(searchMoonQuarter(range.start.toAstronomyTime())) { nextMoonQuarter(it) }
            .takeWhile { it.time.toInstant() < range.endInclusive }
            .map { info ->
                val quarter = MoonQuarter.entries[info.quarter]
                val distanceKm = geoMoon(info.time).length() * KM_PER_AU
                MoonPhaseEvent(
                    time = info.time.toInstant(),
                    quarter = quarter,
                    distanceKm = distanceKm,
                    size = if (quarter == MoonQuarter.FULL_MOON) moonSize(info.time, distanceKm) else null,
                )
            }.toList()

    /**
     * Nolle's rule: a supermoon is within 10 % of the perigee-to-apogee range from the perigee of
     * the orbit it happens in, a micromoon as close to the apogee. The nearest perigee and apogee
     * stand in for that orbit's.
     */
    private fun moonSize(fullMoon: Time, distanceKm: Double): MoonSize? {
        val firstApsis = searchLunarApsis(fullMoon.addDays(-HALF_LUNAR_MONTH_DAYS))
        val apsides = generateSequence(firstApsis) { nextLunarApsis(it) }
            .takeWhile { it.time.ut < fullMoon.ut + HALF_LUNAR_MONTH_DAYS }
            .toList()
        val perigee = apsides.nearest(ApsisKind.Pericenter, fullMoon)?.distanceKm
        val apogee = apsides.nearest(ApsisKind.Apocenter, fullMoon)?.distanceKm
        if (perigee == null || apogee == null) return null
        val margin = (apogee - perigee) * MOON_SIZE_FRACTION
        return when {
            distanceKm <= perigee + margin -> MoonSize.SUPERMOON
            distanceKm >= apogee - margin -> MoonSize.MICROMOON
            else -> null
        }
    }

    private fun List<ApsisInfo>.nearest(kind: ApsisKind, to: Time): ApsisInfo? =
        filter { it.kind == kind }.minByOrNull { abs(it.time.ut - to.ut) }

    private fun seasonEvents(range: ClosedRange<Instant>): List<SeasonEvent> =
        years(range).flatMap { year ->
            val info = seasons(year)
            listOf(
                SeasonEvent(info.marchEquinox.toInstant(), SeasonMarker.MARCH_EQUINOX),
                SeasonEvent(info.juneSolstice.toInstant(), SeasonMarker.JUNE_SOLSTICE),
                SeasonEvent(info.septemberEquinox.toInstant(), SeasonMarker.SEPTEMBER_EQUINOX),
                SeasonEvent(info.decemberSolstice.toInstant(), SeasonMarker.DECEMBER_SOLSTICE),
            )
        }

    /**
     * Around each new Moon, the night with the longest Milky Way window from the three nights
     * before it to the three after. Every new Moon near [range] is looked at, as its best night
     * can fall a few days either side; only nights that depend on the new Moon alone are picked,
     * so a narrower range (an event looked up by id) finds the same night.
     */
    private fun milkyWayNights(range: ClosedRange<Instant>, observer: GeoCoordinate): List<MilkyWayNightEvent> {
        val searchFrom = range.start.minus(NEW_MOON_MARGIN)
        val searchUntil = range.endInclusive.plus(NEW_MOON_MARGIN)
        return generateSequence(searchMoonPhase(NEW_MOON, searchFrom.toAstronomyTime(), LUNAR_MONTH_SEARCH_DAYS)) {
            searchMoonPhase(NEW_MOON, it.addDays(1.0), LUNAR_MONTH_SEARCH_DAYS)
        }
            .takeWhile { it.toInstant() < searchUntil }
            .mapNotNull { newMoon ->
                val nights = milkyWay.nights(observer, newMoon.toInstant().minus(NIGHTS_BEFORE_NEW_MOON), NIGHTS_AROUND)
                nights.mapNotNull { night -> night.window?.let { night to it } }
                    .maxByOrNull { (_, window) -> window.duration }
                    ?.let { (night, window) ->
                        MilkyWayNightEvent(
                            time = window.start,
                            end = window.end,
                            best = window.best,
                            coreAltitudeDegrees = window.coreAtBest.altitudeDegrees,
                            coreAzimuthDegrees = window.coreAtBest.azimuthDegrees,
                            moonIlluminatedFraction = night.moonIlluminatedFraction,
                        )
                    }
            }
            .toList()
    }

    private companion object {
        const val NEW_MOON = 0.0
        const val LUNAR_MONTH_SEARCH_DAYS = 32.0
        const val NIGHTS_AROUND = 7
        val NIGHTS_BEFORE_NEW_MOON: Duration = Duration.ofHours(84)
        val NEW_MOON_MARGIN: Duration = Duration.ofDays(5)
        const val KM_PER_AU = 1.4959787069098932e8
        const val HALF_LUNAR_MONTH_DAYS = 15.0
        const val MOON_SIZE_FRACTION = 0.1
    }
}

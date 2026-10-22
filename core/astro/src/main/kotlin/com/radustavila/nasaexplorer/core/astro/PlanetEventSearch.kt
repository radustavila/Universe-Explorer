package com.radustavila.nasaexplorer.core.astro

import com.radustavila.nasaexplorer.core.model.Apparition
import com.radustavila.nasaexplorer.core.model.GreatestElongation
import com.radustavila.nasaexplorer.core.model.Planet
import com.radustavila.nasaexplorer.core.model.PlanetOpposition
import com.radustavila.nasaexplorer.core.model.PlanetTransit
import io.github.cosinekitty.astronomy.Aberration
import io.github.cosinekitty.astronomy.Body
import io.github.cosinekitty.astronomy.EquatorEpoch
import io.github.cosinekitty.astronomy.Observer
import io.github.cosinekitty.astronomy.Time
import io.github.cosinekitty.astronomy.Visibility
import io.github.cosinekitty.astronomy.angleFromSun
import io.github.cosinekitty.astronomy.constellation
import io.github.cosinekitty.astronomy.equator
import io.github.cosinekitty.astronomy.illumination
import io.github.cosinekitty.astronomy.searchMaxElongation
import io.github.cosinekitty.astronomy.searchRelativeLongitude
import io.github.cosinekitty.astronomy.searchTransit
import java.time.Instant
import kotlin.math.abs

/** Oppositions of the outer planets, and the inner planets' greatest elongations and transits. */
internal object PlanetEventSearch {

    private val OUTER_PLANETS = listOf(Planet.MARS, Planet.JUPITER, Planet.SATURN)
    private val INNER_PLANETS = listOf(Planet.MERCURY, Planet.VENUS)

    /** Any point on the Earth: a planet's position changes by less than an arcsecond across it. */
    private val GEOCENTRE = Observer(0.0, 0.0, 0.0)

    /**
     * Relative longitude (planet minus Earth, seen from the Sun) of an outer planet's opposition,
     * and of an inner planet passing between the Earth and the Sun.
     */
    private const val OPPOSITION = 0.0
    private const val INFERIOR_CONJUNCTION = 0.0

    /** The Sun's apparent radius, about 0.27 degrees, with a margin. */
    private const val TRANSIT_MAX_SEPARATION_DEGREES = 0.3

    fun oppositions(range: ClosedRange<Instant>): List<PlanetOpposition> =
        OUTER_PLANETS.flatMap { planet ->
            val body = planet.body
            relativeLongitudes(body, OPPOSITION, range).map { time ->
                val position = equator(body, time, GEOCENTRE, EquatorEpoch.J2000, Aberration.Corrected)
                PlanetOpposition(
                    time = time.toInstant(),
                    planet = planet,
                    magnitude = illumination(body, time).mag,
                    distanceAu = position.dist,
                    constellation = constellation(position.ra, position.dec).name,
                )
            }
        }

    fun elongations(range: ClosedRange<Instant>): List<GreatestElongation> =
        INNER_PLANETS.flatMap { planet ->
            val body = planet.body
            generateSequence(searchMaxElongation(body, range.start.toAstronomyTime())) { previous ->
                searchMaxElongation(body, previous.time.addDays(SEARCH_STEP_DAYS))
            }.takeWhile { it.time.toInstant() < range.endInclusive }.map { info ->
                GreatestElongation(
                    time = info.time.toInstant(),
                    planet = planet,
                    apparition = if (info.visibility == Visibility.Morning) Apparition.MORNING else Apparition.EVENING,
                    elongationDegrees = info.elongation,
                )
            }.toList()
        }

    /**
     * Transits can only happen at an inferior conjunction, and only when the planet is then within
     * the Sun's radius of its centre. Checking the conjunctions in range first keeps Astronomy
     * Engine's transit search, which runs until it finds one (Venus: 2117), to the rare cases.
     */
    fun transits(range: ClosedRange<Instant>, site: Observer?): List<PlanetTransit> =
        INNER_PLANETS.flatMap { planet ->
            val body = planet.body
            relativeLongitudes(body, INFERIOR_CONJUNCTION, range)
                .filter { conjunction -> angleFromSun(body, conjunction) < TRANSIT_MAX_SEPARATION_DEGREES }
                .mapNotNull { conjunction ->
                    val info = searchTransit(body, conjunction.addDays(-1.0))
                    // A grazing miss would send the search on to the next transit, years away.
                    if (abs(info.peak.ut - conjunction.ut) > 1.0) return@mapNotNull null
                    val start = info.start.toInstant()
                    val end = info.finish.toInstant()
                    PlanetTransit(
                        time = info.peak.toInstant(),
                        planet = planet,
                        transitStart = start,
                        transitEnd = end,
                        isVisibleLocally = site?.let { isAboveHorizonDuring(Body.Sun, it, start, end) },
                    )
                }
        }

    /** Every time in [range] the body is [relativeLongitude] degrees from the Earth, seen from the Sun. */
    private fun relativeLongitudes(body: Body, relativeLongitude: Double, range: ClosedRange<Instant>): List<Time> =
        generateSequence(searchRelativeLongitude(body, relativeLongitude, range.start.toAstronomyTime())) { previous ->
            searchRelativeLongitude(body, relativeLongitude, previous.addDays(SEARCH_STEP_DAYS))
        }.takeWhile { it.toInstant() < range.endInclusive }.toList()
}

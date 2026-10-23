package com.radustavila.nasaexplorer.core.astro

import com.radustavila.nasaexplorer.core.model.Apparition
import com.radustavila.nasaexplorer.core.model.GeoCoordinate
import com.radustavila.nasaexplorer.core.model.GreatestElongation
import com.radustavila.nasaexplorer.core.model.LunarEclipse
import com.radustavila.nasaexplorer.core.model.LunarEclipseType
import com.radustavila.nasaexplorer.core.model.MeteorShower
import com.radustavila.nasaexplorer.core.model.MeteorShowerPeak
import com.radustavila.nasaexplorer.core.model.MilkyWayNightEvent
import com.radustavila.nasaexplorer.core.model.MoonPhaseEvent
import com.radustavila.nasaexplorer.core.model.MoonQuarter
import com.radustavila.nasaexplorer.core.model.MoonSize
import com.radustavila.nasaexplorer.core.model.Planet
import com.radustavila.nasaexplorer.core.model.PlanetOpposition
import com.radustavila.nasaexplorer.core.model.PlanetTransit
import com.radustavila.nasaexplorer.core.model.SeasonEvent
import com.radustavila.nasaexplorer.core.model.SeasonMarker
import com.radustavila.nasaexplorer.core.model.SkyEvent
import com.radustavila.nasaexplorer.core.model.SkyEventCategory
import com.radustavila.nasaexplorer.core.model.SkyEventId
import com.radustavila.nasaexplorer.core.model.SolarEclipse
import com.radustavila.nasaexplorer.core.model.SolarEclipseType
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The calendar against published dates: NASA's eclipse pages (Espenak; times converted from TD to
 * UT with ΔT = 69 s), the USNO's seasons and phases, and the IMO's 2026 meteor shower calendar.
 * Times agree to within two minutes, meteor peaks to within the night the IMO gives.
 */
class EventCalendarTest {

    private val calendar = EventCalendar(AstronomyEngineMilkyWayCalculator(AstronomyEngineDarkSkyCalculator()))

    @Test
    fun `total lunar eclipse of 2026-03-03, seen from Los Angeles but not from Spain`() {
        val eclipse = single<LunarEclipse>("2026-03-03", observer = LOS_ANGELES)

        assertEquals(LunarEclipseType.TOTAL, eclipse.type)
        assertClose("2026-03-03T11:33:40Z", eclipse.time)
        assertClose("2026-03-03T11:04:30Z", eclipse.totalStart)
        assertClose("2026-03-03T12:02:50Z", eclipse.totalEnd)
        assertClose("2026-03-03T09:50:00Z", eclipse.partialStart)
        assertEquals(1.0, eclipse.obscuration, 1e-9)
        assertTrue(checkNotNull(eclipse.local).isVisible)
        assertEquals(eclipse.partialStart, eclipse.start)
        assertEquals("lunar-eclipse:2026-03-03", eclipse.id)

        val fromSpain = single<LunarEclipse>("2026-03-03", observer = ZARAGOZA)
        // Mid-day in Spain: the Moon is far below the horizon, so the event is listed at its peak.
        assertFalse(checkNotNull(fromSpain.local).isVisible)
        assertTrue(fromSpain.local!!.moonAltitudeAtPeak < 0)
        assertEquals(fromSpain.time, fromSpain.start)
    }

    @Test
    fun `total solar eclipse of 2026-08-12 is total from Zaragoza, nearly total from Madrid, unseen from Bucharest`() {
        val eclipse = single<SolarEclipse>("2026-08-12", observer = ZARAGOZA)

        assertEquals(SolarEclipseType.TOTAL, eclipse.type)
        assertClose("2026-08-12T17:46:00Z", eclipse.time)
        // Greatest eclipse off the west coast of Iceland.
        val greatest = checkNotNull(eclipse.greatestEclipseAt)
        assertEquals(65.2, greatest.latitude, 0.5)
        assertEquals(-25.2, greatest.longitude, 0.5)

        val zaragoza = checkNotNull(eclipse.local)
        assertEquals(SolarEclipseType.TOTAL, zaragoza.type)
        assertNotNull(zaragoza.totalStart)
        // Low in the west-north-west, a few degrees above the horizon.
        assertTrue(zaragoza.sunAltitudeAtPeak in 3.0..10.0)
        assertEquals(zaragoza.partialStart, eclipse.start)

        val madrid = checkNotNull(single<SolarEclipse>("2026-08-12", observer = MADRID).local)
        assertEquals(SolarEclipseType.PARTIAL, madrid.type)
        assertTrue(madrid.obscuration > 0.99)
        assertNull(madrid.totalStart)

        // The Sun has set in Bucharest before the Moon's shadow arrives.
        val bucharest = single<SolarEclipse>("2026-08-12", observer = ReferenceElements.BUCHAREST)
        assertNull(bucharest.local)
        assertEquals(bucharest.time, bucharest.start)
    }

    @Test
    fun `Perseids peak on the night of 12 to 13 August 2026 under a new Moon`() {
        val perseids = events("2026-08-01", "2026-09-01", observer = ZARAGOZA)
            .filterIsInstance<MeteorShowerPeak>()
            .single { it.shower == MeteorShower.PERSEIDS }

        assertTrue(perseids.time >= Instant.parse("2026-08-12T12:00:00Z"))
        assertTrue(perseids.time < Instant.parse("2026-08-13T12:00:00Z"))
        assertEquals(100, perseids.zenithalHourlyRate)
        assertEquals(LocalDate.parse("2026-07-17"), perseids.activeFrom)
        assertEquals(LocalDate.parse("2026-08-24"), perseids.activeUntil)
        assertEquals("109P/Swift–Tuttle", perseids.parentBody)
        assertTrue(perseids.moonIlluminatedFraction < 0.05)

        val viewing = checkNotNull(perseids.viewing)
        // The night of the 12th in Spain, the radiant high in the north-east before dawn.
        assertEquals(LocalDate.parse("2026-08-12"), viewing.darkFrom.atOffset(java.time.ZoneOffset.UTC).toLocalDate())
        assertTrue(viewing.bestTime in viewing.darkFrom..viewing.darkUntil)
        assertTrue(viewing.radiantAltitudeDegrees > 50.0)
        assertTrue(viewing.radiantAzimuthDegrees in 0.0..90.0)
        assertEquals(viewing.darkFrom, perseids.start)
    }

    @Test
    fun `every major shower peaks once a year on its usual date`() {
        val peaks = events("2026-01-01", "2027-01-01", observer = null).filterIsInstance<MeteorShowerPeak>()
            .associate { it.shower to it.time.atOffset(java.time.ZoneOffset.UTC).toLocalDate() }

        assertEquals(MeteorShower.entries.toSet(), peaks.keys)
        val expected = mapOf(
            MeteorShower.QUADRANTIDS to "2026-01-03",
            MeteorShower.LYRIDS to "2026-04-22",
            MeteorShower.ETA_AQUARIIDS to "2026-05-06",
            MeteorShower.SOUTHERN_DELTA_AQUARIIDS to "2026-07-30",
            MeteorShower.DRACONIDS to "2026-10-08",
            MeteorShower.ORIONIDS to "2026-10-21",
            MeteorShower.LEONIDS to "2026-11-17",
            MeteorShower.GEMINIDS to "2026-12-14",
            MeteorShower.URSIDS to "2026-12-22",
        )
        expected.forEach { (shower, date) ->
            val actual = checkNotNull(peaks[shower])
            val days = Duration.between(LocalDate.parse(date).atStartOfDay(), actual.atStartOfDay()).toDays()
            assertTrue("$shower peaks on $actual, expected about $date", days in -1..1)
        }
    }

    @Test
    fun `the Quadrantids' activity starts in the previous December`() {
        val quadrantids = events("2026-01-01", "2026-01-10", observer = null)
            .filterIsInstance<MeteorShowerPeak>()
            .single()

        assertEquals(MeteorShower.QUADRANTIDS, quadrantids.shower)
        assertEquals(LocalDate.parse("2025-12-28"), quadrantids.activeFrom)
        assertEquals(LocalDate.parse("2026-01-12"), quadrantids.activeUntil)
    }

    @Test
    fun `equinoxes and solstices of 2026`() {
        val seasons = events("2026-01-01", "2027-01-01", observer = null).filterIsInstance<SeasonEvent>()

        assertEquals(SeasonMarker.entries, seasons.map { it.marker })
        assertClose("2026-03-20T14:46:00Z", seasons[0].time)
        assertClose("2026-06-21T08:24:00Z", seasons[1].time)
        assertClose("2026-09-23T00:05:00Z", seasons[2].time)
        assertClose("2026-12-21T20:50:00Z", seasons[3].time)
        assertEquals("season-december-solstice:2026-12-21", seasons[3].id)
    }

    @Test
    fun `moon phases with the year's last supermoon`() {
        val phases = events("2026-12-01", "2027-01-01", observer = null).filterIsInstance<MoonPhaseEvent>()

        assertEquals(
            listOf(
                MoonQuarter.LAST_QUARTER,
                MoonQuarter.NEW_MOON,
                MoonQuarter.FIRST_QUARTER,
                MoonQuarter.FULL_MOON,
                MoonQuarter.LAST_QUARTER,
            ),
            phases.map { it.quarter },
        )
        val fullMoon = phases.single { it.quarter == MoonQuarter.FULL_MOON }
        assertClose("2026-12-24T01:28:00Z", fullMoon.time)
        assertEquals(MoonSize.SUPERMOON, fullMoon.size)
        assertTrue(fullMoon.distanceKm < 357_000)
        assertTrue(phases.filter { it.quarter != MoonQuarter.FULL_MOON }.all { it.size == null })
    }

    @Test
    fun `2026 has three supermoons and a micromoon in May`() {
        val fullMoons = events("2026-01-01", "2027-01-01", observer = null)
            .filterIsInstance<MoonPhaseEvent>()
            .filter { it.quarter == MoonQuarter.FULL_MOON }

        assertEquals(
            listOf("2026-01-03", "2026-11-24", "2026-12-24"),
            fullMoons.filter { it.size == MoonSize.SUPERMOON }.map { it.time.toString().take(10) },
        )
        assertTrue(fullMoons.single { it.time.toString().startsWith("2026-05-31") }.size == MoonSize.MICROMOON)
    }

    @Test
    fun `planet oppositions and elongations`() {
        val planets = events("2026-01-01", "2027-03-01", observer = null)

        val oppositions = planets.filterIsInstance<PlanetOpposition>().map { it.planet to it.time.toString().take(10) }
        assertEquals(
            listOf(
                Planet.JUPITER to "2026-01-10",
                Planet.SATURN to "2026-10-04",
                Planet.JUPITER to "2027-02-11",
                Planet.MARS to "2027-02-19",
            ),
            oppositions,
        )
        val jupiter = planets.filterIsInstance<PlanetOpposition>().first { it.planet == Planet.JUPITER }
        assertEquals("Gemini", jupiter.constellation)
        assertTrue(jupiter.magnitude < -2.5)

        // Venus is at greatest eastern (evening) elongation on 15 August 2026, 45.9 degrees.
        val venus = planets.filterIsInstance<GreatestElongation>().first { it.planet == Planet.VENUS }
        assertEquals("2026-08-15", venus.time.toString().take(10))
        assertEquals(Apparition.EVENING, venus.apparition)
        assertEquals(45.9, venus.elongationDegrees, 0.1)
    }

    @Test
    fun `the next transit of Mercury, on 13 November 2032`() {
        val transit = events("2032-11-01", "2032-12-01", observer = ReferenceElements.BUCHAREST)
            .filterIsInstance<PlanetTransit>()
            .single()

        assertEquals(Planet.MERCURY, transit.planet)
        assertClose("2032-11-13T08:54:00Z", transit.time)
        assertClose("2032-11-13T06:41:00Z", transit.transitStart)
        assertClose("2032-11-13T11:07:00Z", transit.transitEnd)
        assertEquals(true, transit.isVisibleLocally)

        // No transits in an ordinary year, although Mercury passes the Sun three times.
        assertTrue(events("2026-01-01", "2027-01-01", observer = null).none { it is PlanetTransit })
    }

    @Test
    fun `the best Milky Way night around July's new Moon, only with a place and only in season`() {
        // New Moon on 14 July 2026 at 09:44 UTC.
        val july = events("2026-07-01", "2026-08-01", observer = MADRID).filterIsInstance<MilkyWayNightEvent>()

        val night = july.single()
        assertTrue(night.time in Instant.parse("2026-07-10T18:00:00Z")..Instant.parse("2026-07-18T06:00:00Z"))
        assertTrue(night.end.isAfter(night.time.plus(Duration.ofHours(1))))
        assertTrue(night.coreAltitudeDegrees in 10.0..21.5)
        assertTrue(night.moonIlluminatedFraction < 0.1)
        assertEquals(SkyEventCategory.MILKY_WAY, night.category)
        assertTrue(night.id.startsWith("milky-way:"))

        assertTrue(events("2026-07-01", "2026-08-01", observer = null).none { it is MilkyWayNightEvent })
        assertTrue(events("2026-12-01", "2027-01-01", observer = MADRID).none { it is MilkyWayNightEvent })
    }

    @Test
    fun `without an observer only the global facts are filled in`() {
        val events = events("2026-08-01", "2026-09-01", observer = null)

        assertNull(events.filterIsInstance<SolarEclipse>().single().local)
        assertNull(events.filterIsInstance<LunarEclipse>().single().local)
        assertTrue(events.filterIsInstance<MeteorShowerPeak>().all { it.viewing == null })
    }

    @Test
    fun `the range is half open, sorted, and filtered by category`() {
        val from = Instant.parse("2026-12-21T20:50:22Z")
        val all = calendar.events(from, Instant.parse("2027-01-01T00:00:00Z"), observer = null)

        assertEquals(all.sortedBy { it.time }, all)
        assertTrue(all.all { it.time >= from })
        val onlySeasons = calendar.events(
            Instant.parse("2026-01-01T00:00:00Z"),
            Instant.parse("2026-12-21T20:50:00Z"),
            observer = null,
            categories = setOf(SkyEventCategory.SEASONS),
        )
        // The December solstice, at 20:50:22, is past the end.
        assertEquals(3, onlySeasons.size)
        assertTrue(onlySeasons.all { it.category == SkyEventCategory.SEASONS })
    }

    @Test
    fun `ids carry the event's UTC date and find it again`() {
        val events = events("2026-01-01", "2027-01-01", observer = MADRID)

        assertEquals(events.size, events.map { it.id }.toSet().size)
        events.forEach { event ->
            val date = checkNotNull(SkyEventId.dateOf(event.id))
            val again = calendar.events(
                date.minusDays(1).atStartOfDay().toInstant(java.time.ZoneOffset.UTC),
                date.plusDays(2).atStartOfDay().toInstant(java.time.ZoneOffset.UTC),
                observer = MADRID,
            )
            val found = again.single { it.id == event.id }
            assertEquals(event::class, found::class)
            // Searches started elsewhere converge on the same instant, to floating-point noise.
            assertTrue(Duration.between(event.time, found.time).abs() < Duration.ofSeconds(1))
        }
    }

    @Test
    fun `a month computes quickly enough for the screen`() {
        // Warm-up: the first search loads Astronomy Engine's tables.
        events("2026-01-01", "2026-02-01", observer = ReferenceElements.BUCHAREST)
        val started = System.nanoTime()
        events("2026-08-01", "2026-09-01", observer = ReferenceElements.BUCHAREST)
        val took = Duration.ofNanos(System.nanoTime() - started)
        assertTrue("took $took", took < Duration.ofMillis(500))
    }

    private inline fun <reified T : SkyEvent> single(date: String, observer: GeoCoordinate?): T {
        val day = LocalDate.parse(date)
        return events(day.toString(), day.plusDays(1).toString(), observer).filterIsInstance<T>().single()
    }

    private fun events(from: String, until: String, observer: GeoCoordinate?): List<SkyEvent> =
        calendar.events(Instant.parse("${from}T00:00:00Z"), Instant.parse("${until}T00:00:00Z"), observer)

    private fun assertClose(expected: String, actual: Instant?) {
        assertNotNull("expected $expected", actual)
        val error = Duration.between(Instant.parse(expected), actual).abs()
        assertTrue("$actual is $error from $expected", error <= Duration.ofMinutes(2))
    }

    private companion object {
        val ZARAGOZA = GeoCoordinate(latitude = 41.65, longitude = -0.88)
        val MADRID = GeoCoordinate(latitude = 40.42, longitude = -3.70)
        val LOS_ANGELES = GeoCoordinate(latitude = 34.05, longitude = -118.24)
    }
}

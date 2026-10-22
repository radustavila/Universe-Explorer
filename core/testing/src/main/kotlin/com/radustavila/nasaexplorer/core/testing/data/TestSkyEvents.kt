package com.radustavila.nasaexplorer.core.testing.data

import com.radustavila.nasaexplorer.core.model.Apparition
import com.radustavila.nasaexplorer.core.model.GeoCoordinate
import com.radustavila.nasaexplorer.core.model.GreatestElongation
import com.radustavila.nasaexplorer.core.model.LocalLunarEclipse
import com.radustavila.nasaexplorer.core.model.LocalSolarEclipse
import com.radustavila.nasaexplorer.core.model.LunarEclipse
import com.radustavila.nasaexplorer.core.model.LunarEclipseType
import com.radustavila.nasaexplorer.core.model.MeteorShower
import com.radustavila.nasaexplorer.core.model.MeteorShowerPeak
import com.radustavila.nasaexplorer.core.model.MeteorViewing
import com.radustavila.nasaexplorer.core.model.MoonPhaseEvent
import com.radustavila.nasaexplorer.core.model.MoonQuarter
import com.radustavila.nasaexplorer.core.model.MoonSize
import com.radustavila.nasaexplorer.core.model.Planet
import com.radustavila.nasaexplorer.core.model.PlanetOpposition
import com.radustavila.nasaexplorer.core.model.SeasonEvent
import com.radustavila.nasaexplorer.core.model.SeasonMarker
import com.radustavila.nasaexplorer.core.model.SkyEvent
import com.radustavila.nasaexplorer.core.model.SolarEclipse
import com.radustavila.nasaexplorer.core.model.SolarEclipseType
import java.time.Instant
import java.time.LocalDate

/** Sky events of 2026 as the calendar computes them (from Zaragoza where local), for tests and screenshots. */
object TestSkyEvents {

    fun fullMoon(
        time: Instant = Instant.parse("2026-12-24T01:28:45Z"),
        size: MoonSize? = MoonSize.SUPERMOON,
        distanceKm: Double = 356_729.0,
    ) = MoonPhaseEvent(time = time, quarter = MoonQuarter.FULL_MOON, distanceKm = distanceKm, size = size)

    fun moonPhase(quarter: MoonQuarter, time: Instant) =
        MoonPhaseEvent(time = time, quarter = quarter, distanceKm = 384_400.0)

    fun totalSolarEclipse(local: Boolean = true) = SolarEclipse(
        time = Instant.parse("2026-08-12T17:45:47Z"),
        type = SolarEclipseType.TOTAL,
        greatestEclipseAt = GeoCoordinate(latitude = 65.2, longitude = -25.2),
        local = if (!local) {
            null
        } else {
            LocalSolarEclipse(
                type = SolarEclipseType.TOTAL,
                obscuration = 1.0,
                partialStart = Instant.parse("2026-08-12T17:34:34Z"),
                totalStart = Instant.parse("2026-08-12T18:28:53Z"),
                peak = Instant.parse("2026-08-12T18:29:36Z"),
                totalEnd = Instant.parse("2026-08-12T18:30:20Z"),
                partialEnd = Instant.parse("2026-08-12T19:21:19Z"),
                sunAltitudeAtPeak = 6.1,
            )
        },
    )

    fun partialLunarEclipse(visible: Boolean = true) = LunarEclipse(
        time = Instant.parse("2026-08-28T04:12:49Z"),
        type = LunarEclipseType.PARTIAL,
        obscuration = 0.966,
        penumbralStart = Instant.parse("2026-08-28T01:23:36Z"),
        penumbralEnd = Instant.parse("2026-08-28T07:02:02Z"),
        partialStart = Instant.parse("2026-08-28T02:33:24Z"),
        partialEnd = Instant.parse("2026-08-28T05:52:14Z"),
        totalStart = null,
        totalEnd = null,
        local = LocalLunarEclipse(moonAltitudeAtPeak = if (visible) 12.8 else -30.0, isVisible = visible),
    )

    fun perseids(withViewing: Boolean = true) = MeteorShowerPeak(
        time = Instant.parse("2026-08-13T01:52:25Z"),
        shower = MeteorShower.PERSEIDS,
        activeFrom = LocalDate.parse("2026-07-17"),
        activeUntil = LocalDate.parse("2026-08-24"),
        zenithalHourlyRate = 100,
        parentBody = "109P/Swift–Tuttle",
        radiantConstellation = "Perseus",
        moonIlluminatedFraction = 0.002,
        viewing = if (!withViewing) {
            null
        } else {
            MeteorViewing(
                darkFrom = Instant.parse("2026-08-12T20:14:26Z"),
                darkUntil = Instant.parse("2026-08-13T04:03:01Z"),
                bestTime = Instant.parse("2026-08-13T03:54:26Z"),
                radiantAltitudeDegrees = 65.6,
                radiantAzimuthDegrees = 38.1,
            )
        },
    )

    fun geminids() = MeteorShowerPeak(
        time = Instant.parse("2026-12-14T13:26:48Z"),
        shower = MeteorShower.GEMINIDS,
        activeFrom = LocalDate.parse("2026-12-04"),
        activeUntil = LocalDate.parse("2026-12-20"),
        zenithalHourlyRate = 150,
        parentBody = "3200 Phaethon",
        radiantConstellation = "Gemini",
        moonIlluminatedFraction = 0.25,
        viewing = null,
    )

    fun saturnOpposition() = PlanetOpposition(
        time = Instant.parse("2026-10-04T12:12:54Z"),
        planet = Planet.SATURN,
        magnitude = 0.2,
        distanceAu = 8.43,
        constellation = "Cetus",
    )

    fun venusElongation() = GreatestElongation(
        time = Instant.parse("2026-08-15T06:23:02Z"),
        planet = Planet.VENUS,
        apparition = Apparition.EVENING,
        elongationDegrees = 45.9,
    )

    fun decemberSolstice() = SeasonEvent(
        time = Instant.parse("2026-12-21T20:50:22Z"),
        marker = SeasonMarker.DECEMBER_SOLSTICE,
    )

    /** August 2026, one of every kind the month has. */
    fun august2026(): List<SkyEvent> = listOf(
        moonPhase(MoonQuarter.LAST_QUARTER, Instant.parse("2026-08-06T02:21:59Z")),
        moonPhase(MoonQuarter.NEW_MOON, Instant.parse("2026-08-12T17:37:11Z")),
        totalSolarEclipse(),
        perseids(),
        venusElongation(),
        moonPhase(MoonQuarter.FIRST_QUARTER, Instant.parse("2026-08-20T02:46:58Z")),
        partialLunarEclipse(),
        fullMoon(time = Instant.parse("2026-08-28T04:19:06Z"), size = null, distanceKm = 390_385.0),
    )
}

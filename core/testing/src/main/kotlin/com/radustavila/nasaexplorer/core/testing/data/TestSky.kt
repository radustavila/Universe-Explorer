package com.radustavila.nasaexplorer.core.testing.data

import com.radustavila.nasaexplorer.core.model.GeoCoordinate
import com.radustavila.nasaexplorer.core.model.LocationSource
import com.radustavila.nasaexplorer.core.model.MoonPhase
import com.radustavila.nasaexplorer.core.model.MoonTonight
import com.radustavila.nasaexplorer.core.model.NightWindow
import com.radustavila.nasaexplorer.core.model.ObserverLocation
import com.radustavila.nasaexplorer.core.model.OrbitalElements
import com.radustavila.nasaexplorer.core.model.PersonInSpace
import com.radustavila.nasaexplorer.core.model.Planet
import com.radustavila.nasaexplorer.core.model.PlanetTonight
import com.radustavila.nasaexplorer.core.model.SatellitePass
import com.radustavila.nasaexplorer.core.model.SatellitePosition
import com.radustavila.nasaexplorer.core.model.SkyTonight
import java.time.Duration
import java.time.Instant

/** Builders for Sky tonight's models, around the night of 2026-10-19 above Bucharest. */
object TestSky {

    val BUCHAREST = GeoCoordinate(latitude = 44.43, longitude = 26.10)
    val SUNSET: Instant = Instant.parse("2026-10-19T15:25:00Z")

    fun location(
        coordinate: GeoCoordinate = BUCHAREST,
        name: String? = "Bucharest",
        source: LocationSource = LocationSource.MANUAL,
    ) = ObserverLocation(coordinate, name, source)

    fun night(sunset: Instant = SUNSET) = NightWindow(
        sunset = sunset,
        civilDusk = sunset.plus(Duration.ofMinutes(30)),
        nauticalDusk = sunset.plus(Duration.ofMinutes(64)),
        astronomicalDusk = sunset.plus(Duration.ofMinutes(97)),
        astronomicalDawn = sunset.plus(Duration.ofMinutes(695)),
        nauticalDawn = sunset.plus(Duration.ofMinutes(729)),
        civilDawn = sunset.plus(Duration.ofMinutes(762)),
        sunrise = sunset.plus(Duration.ofMinutes(792)),
    )

    fun moon(
        phase: MoonPhase = MoonPhase.FIRST_QUARTER,
        illuminatedFraction: Double = 0.58,
    ) = MoonTonight(
        phase = phase,
        phaseAngleDegrees = 99.0,
        illuminatedFraction = illuminatedFraction,
        rise = Instant.parse("2026-10-20T12:49:00Z"),
        set = Instant.parse("2026-10-19T21:53:00Z"),
        isUpAtStart = true,
        nextFullMoon = Instant.parse("2026-10-26T04:12:00Z"),
        nextNewMoon = Instant.parse("2026-11-09T07:02:00Z"),
    )

    fun planet(
        planet: Planet = Planet.SATURN,
        bestTime: Instant? = Instant.parse("2026-10-19T19:30:00Z"),
        bestAltitude: Double? = 38.0,
    ) = PlanetTonight(
        planet = planet,
        rise = Instant.parse("2026-10-20T14:51:00Z"),
        set = Instant.parse("2026-10-20T03:11:00Z"),
        isUpAtStart = true,
        bestTime = bestTime,
        bestAltitudeDegrees = bestAltitude,
        bestAzimuthDegrees = bestTime?.let { 180.0 },
        magnitude = 0.9,
        constellation = "Pisces",
    )

    fun skyTonight(
        planets: List<PlanetTonight> = listOf(
            planet(Planet.SATURN),
            planet(Planet.JUPITER, bestTime = Instant.parse("2026-10-20T04:00:00Z"), bestAltitude = 52.0),
            planet(Planet.MERCURY, bestTime = null, bestAltitude = null),
        ),
    ) = SkyTonight(
        computedAt = Instant.parse("2026-10-19T12:00:00Z"),
        night = night(),
        moon = moon(),
        planets = planets,
    )

    fun issElements(epoch: Instant = Instant.parse("2026-10-19T04:14:45Z")) = OrbitalElements(
        noradId = OrbitalElements.ISS_NORAD_ID,
        name = "ISS (ZARYA)",
        epoch = epoch,
        meanMotion = 15.48934931,
        eccentricity = 0.0006763,
        inclinationDegrees = 51.6325,
        raanDegrees = 46.5189,
        argOfPericenterDegrees = 282.0427,
        meanAnomalyDegrees = 77.9804,
        bstar = 0.00013772,
    )

    fun pass(
        start: Instant = Instant.parse("2026-10-20T02:07:31Z"),
        minutes: Long = 7,
        maxElevation: Double = 61.6,
        isVisible: Boolean = true,
    ) = SatellitePass(
        start = start,
        peak = start.plus(Duration.ofMinutes(minutes).dividedBy(2)),
        end = start.plus(Duration.ofMinutes(minutes)),
        maxElevationDegrees = maxElevation,
        startAzimuthDegrees = 225.8,
        endAzimuthDegrees = 64.2,
        isVisible = isVisible,
    )

    fun position(
        latitude: Double = 35.72,
        longitude: Double = -155.14,
        at: Instant = Instant.parse("2026-10-19T13:44:32Z"),
        isLive: Boolean = true,
    ) = SatellitePosition(
        coordinate = GeoCoordinate(latitude, longitude),
        altitudeKm = 425.0,
        speedKmPerHour = 27_575.0,
        isSunlit = false,
        at = at,
        isLive = isLive,
    )

    fun person(
        id: Int = 582,
        name: String = "Jessica Watkins",
        agency: String? = "NASA",
        launchedAt: Instant? = Instant.parse("2026-10-01T15:10:06Z"),
    ) = PersonInSpace(
        id = id,
        name = name,
        agency = agency,
        nationality = "United States of America",
        imageUrl = null,
        launchedAt = launchedAt,
    )
}

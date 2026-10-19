package com.radustavila.nasaexplorer.core.astro

import com.radustavila.nasaexplorer.core.model.GeoCoordinate
import com.radustavila.nasaexplorer.core.model.OrbitalElements
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Fixed inputs for the reference tests. Expected values were produced once with the reference
 * implementations - Vallado's `sgp4` 2.25 (Python), Skyfield 1.55 with JPL's DE421 ephemeris - and
 * are hardcoded in the tests, so nothing here touches the network.
 */
internal object ReferenceElements {

    /**
     * ISS (ZARYA) from CelesTrak, 2026-10-19:
     * ```
     * 1 25544U 98067A   26292.17690985  .00007130  00000+0  13772-3 0  9999
     * 2 25544  51.6325  46.5189 0006763 282.0427  77.9804 15.48934931590976
     * ```
     */
    val ISS = OrbitalElements(
        noradId = 25544,
        name = "ISS (ZARYA)",
        epoch = tleEpoch(year = 2026, dayOfYear = 292.17690985),
        meanMotion = 15.48934931,
        eccentricity = 0.0006763,
        inclinationDegrees = 51.6325,
        raanDegrees = 46.5189,
        argOfPericenterDegrees = 282.0427,
        meanAnomalyDegrees = 77.9804,
        bstar = 0.13772e-3,
    )

    /** Vanguard 1 (00005), the near-Earth case of Vallado's SGP4 verification set. */
    val VANGUARD_1 = OrbitalElements(
        noradId = 5,
        name = "VANGUARD 1",
        epoch = tleEpoch(year = 2000, dayOfYear = 179.78495062),
        meanMotion = 10.82419157,
        eccentricity = 0.1859667,
        inclinationDegrees = 34.2682,
        raanDegrees = 348.7242,
        argOfPericenterDegrees = 331.7664,
        meanAnomalyDegrees = 19.3264,
        bstar = 0.28098e-4,
    )

    val BUCHAREST = GeoCoordinate(latitude = 44.4268, longitude = 26.1025)
    val LONDON = GeoCoordinate(latitude = 51.5072, longitude = -0.1276)

    private fun tleEpoch(year: Int, dayOfYear: Double): Instant {
        val wholeDay = dayOfYear.toLong()
        val fraction = dayOfYear - wholeDay
        return LocalDate.of(year, 1, 1).plusDays(wholeDay - 1).atStartOfDay().toInstant(ZoneOffset.UTC)
            .plusNanos(Math.round(fraction * NANOS_PER_DAY))
    }

    private const val NANOS_PER_DAY = 86_400e9
}

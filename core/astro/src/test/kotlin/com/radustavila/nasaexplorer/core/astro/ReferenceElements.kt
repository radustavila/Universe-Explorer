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

    /**
     * Hubble from CelesTrak (GP JSON), 2026-10-21:
     * ```
     * EPOCH 2026-10-21T07:25:27.668064, MEAN_MOTION 15.32029197, ECCENTRICITY 0.00021583,
     * INCLINATION 28.4737, RA_OF_ASC_NODE 297.7404, ARG_OF_PERICENTER 117.4266,
     * MEAN_ANOMALY 242.655, BSTAR 0.00022957401
     * ```
     */
    val HUBBLE = OrbitalElements(
        noradId = 20580,
        name = "HST",
        epoch = Instant.parse("2026-10-21T07:25:27.668064Z"),
        meanMotion = 15.32029197,
        eccentricity = 0.00021583,
        inclinationDegrees = 28.4737,
        raanDegrees = 297.7404,
        argOfPericenterDegrees = 117.4266,
        meanAnomalyDegrees = 242.655,
        bstar = 0.00022957401,
    )

    /** Tiangong's core module Tianhe from CelesTrak (GP JSON), 2026-10-21. */
    val TIANGONG = OrbitalElements(
        noradId = 48274,
        name = "CSS (TIANHE)",
        epoch = Instant.parse("2026-10-21T06:12:11.347488Z"),
        meanMotion = 15.61026916,
        eccentricity = 0.00020312,
        inclinationDegrees = 41.4687,
        raanDegrees = 276.1937,
        argOfPericenterDegrees = 190.5094,
        meanAnomalyDegrees = 169.5703,
        bstar = 0.00035037653,
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

    /** Far enough south for Hubble, which never climbs 10 degrees above Bucharest. */
    val CAIRO = GeoCoordinate(latitude = 30.0444, longitude = 31.2357)

    private fun tleEpoch(year: Int, dayOfYear: Double): Instant {
        val wholeDay = dayOfYear.toLong()
        val fraction = dayOfYear - wholeDay
        return LocalDate.of(year, 1, 1).plusDays(wholeDay - 1).atStartOfDay().toInstant(ZoneOffset.UTC)
            .plusNanos(Math.round(fraction * NANOS_PER_DAY))
    }

    private const val NANOS_PER_DAY = 86_400e9
}

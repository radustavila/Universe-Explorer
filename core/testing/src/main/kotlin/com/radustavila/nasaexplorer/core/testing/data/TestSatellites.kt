package com.radustavila.nasaexplorer.core.testing.data

import com.radustavila.nasaexplorer.core.model.OrbitalElements
import java.time.Instant

/** Orbital elements of the satellites besides the ISS (Hubble, Tiangong, Starlink), from CelesTrak. */
object TestSatellites {

    /** Hubble from CelesTrak, 2026-10-21. */
    fun hubbleElements(epoch: Instant = Instant.parse("2026-10-21T07:25:27Z")) = OrbitalElements(
        noradId = 20580,
        name = "HST",
        epoch = epoch,
        meanMotion = 15.32029197,
        eccentricity = 0.00021583,
        inclinationDegrees = 28.4737,
        raanDegrees = 297.7404,
        argOfPericenterDegrees = 117.4266,
        meanAnomalyDegrees = 242.655,
        bstar = 0.00022957401,
        internationalDesignator = "1990-037B",
        revolutionsAtEpoch = 80792,
    )

    /** Tiangong's core module Tianhe from CelesTrak, 2026-10-21. */
    fun tiangongElements(epoch: Instant = Instant.parse("2026-10-21T06:12:11Z")) = OrbitalElements(
        noradId = 48274,
        name = "CSS (TIANHE)",
        epoch = epoch,
        meanMotion = 15.61026916,
        eccentricity = 0.00020312,
        inclinationDegrees = 41.4687,
        raanDegrees = 276.1937,
        argOfPericenterDegrees = 190.5094,
        meanAnomalyDegrees = 169.5703,
        bstar = 0.00035037653,
        internationalDesignator = "2021-035A",
        revolutionsAtEpoch = 31285,
    )

    /**
     * A Starlink satellite of launch [launch] (e.g. `2026-232`), [launchedDaysAgo] days before
     * [epoch]; [alongTrack] spreads the satellites of one launch along the orbit.
     */
    fun starlinkElements(
        noradId: Int,
        launch: String = "2026-240",
        piece: String = "A",
        epoch: Instant = Instant.parse("2026-10-21T12:00:00Z"),
        launchedDaysAgo: Double = 3.0,
        alongTrack: Double = 0.0,
    ) = OrbitalElements(
        noradId = noradId,
        name = "STARLINK-$noradId",
        epoch = epoch,
        meanMotion = 15.9,
        eccentricity = 0.0001,
        inclinationDegrees = 43.0,
        raanDegrees = 120.0,
        argOfPericenterDegrees = 90.0,
        meanAnomalyDegrees = (270.0 + alongTrack) % 360.0,
        bstar = 0.001,
        internationalDesignator = "$launch$piece",
        revolutionsAtEpoch = (launchedDaysAgo * 15.9).toLong(),
    )
}

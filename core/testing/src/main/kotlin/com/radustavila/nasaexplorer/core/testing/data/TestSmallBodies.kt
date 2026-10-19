package com.radustavila.nasaexplorer.core.testing.data

import com.radustavila.nasaexplorer.core.model.CloseApproach
import com.radustavila.nasaexplorer.core.model.Fireball
import com.radustavila.nasaexplorer.core.model.SentryRisk
import com.radustavila.nasaexplorer.core.model.SmallBodyDetails
import java.time.Instant

object TestSmallBodies {

    val NOW: Instant = Instant.parse("2026-10-19T14:20:00Z")

    fun closeApproach(
        designation: String = "2020 FN3",
        time: Instant = Instant.parse("2026-10-20T10:06:00Z"),
        distanceAu: Double = 0.031638932980166,
        sentry: SentryRisk? = null,
    ) = CloseApproach(
        designation = designation,
        name = "($designation)",
        time = time,
        distanceAu = distanceAu,
        relativeVelocityKmPerSecond = 5.83233949714821,
        absoluteMagnitude = 24.49,
        diameterKm = null,
        sentry = sentry,
    )

    fun sentry(designation: String = "2020 FN3") = SentryRisk(
        designation = designation,
        impactProbability = 2.9e-6,
        palermoMax = -6.15,
        torinoMax = 0,
        potentialImpacts = 3,
        yearRange = "2064-2101",
    )

    fun details(designation: String = "2020 FN3") = SmallBodyDetails(
        designation = designation,
        name = "($designation)",
        orbitClass = "Apollo",
        isNearEarthObject = true,
        isPotentiallyHazardous = false,
        earthMoidAu = 0.00204,
        semiMajorAxisAu = 1.01,
        eccentricity = 0.208,
        inclinationDegrees = 0.896,
        perihelionAu = 0.802,
        aphelionAu = 1.22,
        orbitalPeriodDays = 372.0,
        absoluteMagnitude = 24.49,
        diameterKm = null,
        firstObserved = "2019-03-17",
        lastObserved = "2025-12-11",
        observationsUsed = 66,
        fetchedAt = NOW,
    )

    fun fireball(time: Instant = Instant.parse("2026-10-04T03:14:45Z")) = Fireball(
        time = time,
        radiatedEnergyJoules = 5.4e10,
        impactEnergyKilotons = 0.18,
        latitude = -41.8,
        longitude = -173.1,
        altitudeKm = 26.9,
        velocityKmPerSecond = 13.8,
    )
}

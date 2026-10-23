package com.radustavila.nasaexplorer.core.testing.data

import com.radustavila.nasaexplorer.core.model.DeepSkyObject
import com.radustavila.nasaexplorer.core.model.DeepSkyType
import com.radustavila.nasaexplorer.core.model.EquatorialCoordinate
import com.radustavila.nasaexplorer.core.model.ObservingConditions
import com.radustavila.nasaexplorer.core.model.ObservingLogEntry
import java.time.Instant
import java.time.LocalDate

/** A few well-known deep-sky objects (F23), with their OpenNGC data. */
@Suppress("MagicNumber")
object TestDeepSky {

    val M31 = DeepSkyObject(
        id = "NGC0224",
        designation = "NGC 224",
        commonName = "Andromeda Galaxy",
        type = DeepSkyType.GALAXY,
        position = EquatorialCoordinate(10.6848, 41.2690),
        constellation = "And",
        magnitude = 3.44f,
        majorAxisArcmin = 199.53f,
        minorAxisArcmin = 70.79f,
        positionAngleDegrees = 35,
        messier = 31,
    )

    val M42 = DeepSkyObject(
        id = "NGC1976",
        designation = "NGC 1976",
        commonName = "Great Orion Nebula",
        type = DeepSkyType.NEBULA,
        position = EquatorialCoordinate(83.8221, -5.3911),
        constellation = "Ori",
        magnitude = 4.0f,
        majorAxisArcmin = 90f,
        minorAxisArcmin = 60f,
        messier = 42,
    )

    val M13 = DeepSkyObject(
        id = "NGC6205",
        designation = "NGC 6205",
        commonName = "Hercules Globular Cluster",
        type = DeepSkyType.GLOBULAR_CLUSTER,
        position = EquatorialCoordinate(250.4235, 36.4613),
        constellation = "Her",
        magnitude = 5.78f,
        majorAxisArcmin = 16.5f,
        messier = 13,
    )

    val DOUBLE_CLUSTER = DeepSkyObject(
        id = "C014",
        designation = "",
        commonName = "Double Cluster",
        type = DeepSkyType.OPEN_CLUSTER,
        position = EquatorialCoordinate(35.0, 57.13),
        constellation = "Per",
        magnitude = 3.7f,
        majorAxisArcmin = 60f,
        caldwell = 14,
    )

    val NGC_7000 = DeepSkyObject(
        id = "NGC7000",
        designation = "NGC 7000",
        commonName = "North America Nebula",
        type = DeepSkyType.NEBULA,
        position = EquatorialCoordinate(314.7, 44.53),
        constellation = "Cyg",
        magnitude = 4.0f,
        majorAxisArcmin = 120f,
        minorAxisArcmin = 30f,
        caldwell = 20,
    )

    /** Brightest first, as the bundled catalogue is. */
    fun objects(): List<DeepSkyObject> = listOf(M31, DOUBLE_CLUSTER, M42, NGC_7000, M13)

    fun entry(
        objectId: String = M31.id,
        observedOn: LocalDate = LocalDate.of(2026, 10, 20),
        conditions: ObservingConditions? = ObservingConditions.GOOD,
        note: String? = null,
        loggedAt: Instant = Instant.parse("2026-10-20T22:00:00Z"),
    ) = ObservingLogEntry(objectId, observedOn, conditions, note, loggedAt)
}

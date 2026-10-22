package com.radustavila.nasaexplorer.core.testing.data

import com.radustavila.nasaexplorer.core.model.DeepSpaceProbe
import com.radustavila.nasaexplorer.core.model.DsnDish
import com.radustavila.nasaexplorer.core.model.DsnLink
import com.radustavila.nasaexplorer.core.model.DsnSignal
import com.radustavila.nasaexplorer.core.model.DsnSite
import com.radustavila.nasaexplorer.core.model.DsnSnapshot
import com.radustavila.nasaexplorer.core.model.DsnSpacecraft
import java.time.Instant

/** Builders for Deep Space Network test data, modelled on the feed recorded on 2026-10-22. */
object TestDeepSpaceNetwork {

    val OBSERVED_AT: Instant = Instant.parse("2026-10-22T16:13:18Z")

    /** Horizons/NAIF ids. */
    const val VOYAGER_1_ID = -31
    const val VOYAGER_2_ID = -32

    fun link(
        code: String = "VGR2",
        name: String? = "Voyager 2",
        naifId: Int? = VOYAGER_2_ID,
        downRate: Double? = 160.0,
        upRate: Double? = null,
        band: String = "X",
        active: Boolean = true,
        distanceKm: Double? = 21_600_000_000.0,
        distanceSource: DsnLink.DistanceSource? = distanceKm?.let { DsnLink.DistanceSource.DSN },
    ) = DsnLink(
        spacecraft = DsnSpacecraft(code = code, name = name, naifId = naifId),
        upSignals = listOfNotNull(upRate?.let { DsnSignal(band = "S", dataRateBitsPerSecond = it, isActive = active) }),
        downSignals = listOf(DsnSignal(band = band, dataRateBitsPerSecond = downRate, isActive = active)),
        distanceKm = distanceKm,
        distanceSource = distanceSource,
    )

    fun dish(
        name: String = "DSS43",
        diameterMeters: Int? = 70,
        azimuth: Double? = 207.0,
        elevation: Double? = 17.0,
        activity: String? = "Spacecraft Telemetry, Tracking, and Command",
        links: List<DsnLink> = listOf(link()),
    ) = DsnDish(
        name = name,
        diameterMeters = diameterMeters,
        azimuthDegrees = azimuth,
        elevationDegrees = elevation,
        activity = activity,
        links = links,
    )

    fun idleDish(name: String = "DSS14", activity: String = "Engineering Upgrades") =
        dish(name = name, azimuth = 0.0, elevation = 90.0, activity = activity, links = emptyList())

    val jwst = link(
        code = "JWST",
        name = "James Webb Space Telescope",
        naifId = -170,
        downRate = 28_000_000.0,
        upRate = 16_000.0,
        band = "K",
        distanceKm = 1_510_000.0,
    )

    /** Assigned to a dish but not transmitting yet. */
    val mro = link(code = "MRO", name = "Mars Reconnaissance Orbiter", naifId = -74, distanceKm = null, active = false)

    val goldstone = DsnSite(
        code = "gdscc",
        name = "Goldstone",
        dishes = listOf(
            idleDish(),
            dish(name = "DSS25", diameterMeters = 34, azimuth = 120.0, elevation = 40.0, links = listOf(mro)),
        ),
    )

    val madrid = DsnSite(code = "mdscc", name = "Madrid", dishes = listOf(idleDish(name = "DSS55")))

    val canberra = DsnSite(
        code = "cdscc",
        name = "Canberra",
        dishes = listOf(
            dish(),
            dish(name = "DSS34", diameterMeters = 34, azimuth = 354.0, elevation = 34.0, links = listOf(jwst)),
        ),
    )

    fun snapshot(
        observedAt: Instant = OBSERVED_AT,
        sites: List<DsnSite> = listOf(goldstone, madrid, canberra),
    ) = DsnSnapshot(observedAt = observedAt, sites = sites)

    fun probe(
        horizonsId: Int = VOYAGER_1_ID,
        name: String = "Voyager 1",
        distanceAu: Double = 172.652953067762,
        rangeRate: Double? = 34.8813474,
        at: Instant = Instant.parse("2026-10-22T00:00:00Z"),
    ) = DeepSpaceProbe(
        horizonsId = horizonsId,
        name = name,
        distanceAu = distanceAu,
        rangeRateKmPerSecond = rangeRate,
        at = at,
    )

    val farthest: List<DeepSpaceProbe> = listOf(
        probe(),
        probe(horizonsId = VOYAGER_2_ID, name = "Voyager 2", distanceAu = 144.366005614063, rangeRate = 38.1708375),
        probe(horizonsId = -98, name = "New Horizons", distanceAu = 66.0488605901971, rangeRate = 42.8143612),
    )
}

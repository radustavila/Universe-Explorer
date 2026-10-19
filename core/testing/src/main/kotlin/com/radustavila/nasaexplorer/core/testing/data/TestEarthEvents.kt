package com.radustavila.nasaexplorer.core.testing.data

import com.radustavila.nasaexplorer.core.model.EarthEvent
import com.radustavila.nasaexplorer.core.model.EarthEventCategory
import com.radustavila.nasaexplorer.core.model.EarthEventGeometry
import com.radustavila.nasaexplorer.core.model.EarthEventShape
import com.radustavila.nasaexplorer.core.model.EarthEventSource
import com.radustavila.nasaexplorer.core.model.EventImageryLayer
import com.radustavila.nasaexplorer.core.model.EventMagnitude
import com.radustavila.nasaexplorer.core.model.GeoCoordinate
import java.time.Duration
import java.time.Instant

/** Events shaped like EONET's on 2026-10-19. */
object TestEarthEvents {

    val NOW: Instant = Instant.parse("2026-10-19T10:00:00Z")

    fun wildfire(
        id: String = "EONET_25310",
        title: String = "Prescribed Fire Newman Piles RX, Coconino, Arizona",
        at: Instant = NOW.minus(Duration.ofDays(5)),
        position: GeoCoordinate = GeoCoordinate(latitude = 35.016667, longitude = -111.65),
    ): EarthEvent = EarthEvent(
        id = id,
        title = title,
        category = EarthEventCategory.WILDFIRES,
        sources = listOf(EarthEventSource("IRWIN", "https://irwin.doi.gov/observer/incidents/2026-AZCOF-001566")),
        geometry = listOf(
            EarthEventGeometry(at, EarthEventShape.Point(position), EventMagnitude(751.0, "acres")),
        ),
    )

    /** A storm with a [points]-long track, six hours between reports, ending at [lastAt]. */
    fun storm(
        id: String = "EONET_25340",
        title: String = "Tropical Cyclone Wati",
        points: Int = 3,
        lastAt: Instant = NOW.minus(Duration.ofHours(4)),
    ): EarthEvent = EarthEvent(
        id = id,
        title = title,
        category = EarthEventCategory.SEVERE_STORMS,
        sources = listOf(EarthEventSource("JTWC", "https://www.metoc.navy.mil/jtwc/products/sh0127.tcw")),
        geometry = (0 until points).map { index ->
            val stepsBeforeLast = (points - 1 - index).toLong()
            EarthEventGeometry(
                date = lastAt.minus(Duration.ofHours(6 * stepsBeforeLast)),
                shape = EarthEventShape.Point(GeoCoordinate(latitude = -10.8 - index, longitude = -163.3 + index)),
                magnitude = EventMagnitude(35.0 + 5 * index, "kts"),
            )
        },
    )

    fun volcano(
        id: String = "EONET_20710",
        at: Instant = NOW.minus(Duration.ofDays(30)),
    ): EarthEvent = EarthEvent(
        id = id,
        title = "Nevados del Chillan Volcano, Chile",
        category = EarthEventCategory.VOLCANOES,
        sources = listOf(EarthEventSource("SIVolcano", "https://volcano.si.edu/volcano.cfm?vn=357070")),
        geometry = listOf(
            EarthEventGeometry(at, EarthEventShape.Point(GeoCoordinate(latitude = -36.868, longitude = -71.378))),
        ),
    )

    val TRUE_COLOR_LAYER = EventImageryLayer(
        id = "VIIRS_SNPP_CorrectedReflectance_TrueColor",
        tileMatrixSet = "GoogleMapsCompatible_Level9",
        fileExtension = "jpg",
    )
}

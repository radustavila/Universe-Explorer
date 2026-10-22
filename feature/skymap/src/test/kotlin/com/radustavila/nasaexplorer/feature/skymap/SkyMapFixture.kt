package com.radustavila.nasaexplorer.feature.skymap

import com.radustavila.nasaexplorer.core.model.HorizontalCoordinate
import com.radustavila.nasaexplorer.core.model.LocationSource
import com.radustavila.nasaexplorer.core.model.ObserverLocation
import com.radustavila.nasaexplorer.core.model.SatelliteSkyPosition
import com.radustavila.nasaexplorer.core.model.SkyMapBodies
import com.radustavila.nasaexplorer.core.model.SolarSystemBody
import com.radustavila.nasaexplorer.core.skymap.ResourceSkyCatalog
import com.radustavila.nasaexplorer.core.skymap.SkyChart
import com.radustavila.nasaexplorer.core.testing.data.TestSky
import com.radustavila.nasaexplorer.core.testing.data.TestSkyMap
import java.time.Instant
import kotlinx.coroutines.runBlocking

/** The real bundled catalogue over Bucharest late on 2026-10-22, with a few bodies and the ISS. */
internal object SkyMapFixture {

    val INSTANT: Instant = Instant.parse("2026-10-22T21:30:00Z")

    private val chart by lazy { SkyChart(runBlocking { ResourceSkyCatalog().catalog() }) }

    val bodies = SkyMapBodies(
        solarSystem = listOf(
            TestSkyMap.position(
                SolarSystemBody.SUN,
                altitude = -45.0,
                azimuth = 0.0,
                magnitude = -26.7,
                angularDiameter = 0.53,
            ),
            TestSkyMap.position(
                SolarSystemBody.MOON,
                altitude = 22.0,
                azimuth = 200.0,
                magnitude = -10.5,
                illuminatedFraction = 0.62,
                angularDiameter = 0.52,
            ),
            TestSkyMap.position(SolarSystemBody.SATURN, altitude = 30.0, azimuth = 175.0, magnitude = 0.8),
            TestSkyMap.position(SolarSystemBody.MARS, altitude = 12.0, azimuth = 150.0, magnitude = 1.2),
        ),
        satellites = listOf(SatelliteSkyPosition(25544, "ISS (ZARYA)", HorizontalCoordinate(40.0, 160.0))),
    )

    fun state(
        selected: SkyTarget? = null,
        selectedConstellation: String? = null,
        isNightMode: Boolean = false,
    ) = SkyMapUiState(
        isLocationLoaded = true,
        location = ObserverLocation(TestSky.BUCHAREST, "Bucharest", LocationSource.MANUAL),
        sky = chart.at(TestSky.BUCHAREST, INSTANT),
        bodies = bodies,
        instant = INSTANT,
        isNightMode = isNightMode,
        selected = selected,
        selectedConstellation = selectedConstellation,
    )

    fun starIndex(name: String): Int = chart.catalog.stars.indexOfFirst { it.name == name }
}

package com.radustavila.nasaexplorer.feature.skymap

import com.radustavila.nasaexplorer.core.model.HorizontalCoordinate
import com.radustavila.nasaexplorer.core.model.SatelliteSkyPosition
import com.radustavila.nasaexplorer.core.model.SkyMapBodies
import com.radustavila.nasaexplorer.core.model.SolarSystemBody
import com.radustavila.nasaexplorer.core.skymap.SkyChart
import com.radustavila.nasaexplorer.core.skymap.SkyProjection
import com.radustavila.nasaexplorer.core.skymap.SkyView
import com.radustavila.nasaexplorer.core.testing.data.TestSky
import com.radustavila.nasaexplorer.core.testing.data.TestSkyMap
import com.radustavila.nasaexplorer.feature.skymap.render.SkyHitTest
import com.radustavila.nasaexplorer.feature.skymap.render.direction
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SkyHitTestTest {

    private val sky = SkyChart(TestSkyMap.catalog()).at(TestSky.BUCHAREST, Instant.parse("2026-10-22T23:00:00Z"))
    private val jupiter =
        TestSkyMap.position(SolarSystemBody.JUPITER, altitude = 30.0, azimuth = 120.0, magnitude = -2.5)
    private val bodies = SkyMapBodies(
        solarSystem = listOf(jupiter),
        satellites = listOf(SatelliteSkyPosition(25544, "ISS", HorizontalCoordinate(60.0, 200.0))),
    )

    @Test
    fun `a tap near a planet picks the planet`() {
        val projection = lookingAt(30.0, 120.0)
        projection.project(jupiter.direction())

        val target = SkyHitTest.find(projection.x + 10f, projection.y - 5f, 40f, sky, bodies, projection)

        assertEquals(SkyTarget.Body(SolarSystemBody.JUPITER), target)
    }

    @Test
    fun `a tap near a star picks the star, and empty sky picks nothing`() {
        val betelgeuse = sky.chart.catalog.stars.indexOf(TestSkyMap.BETELGEUSE)
        val direction = SkyTarget.StarTarget(betelgeuse).direction(sky, bodies)!!.toHorizontal()
        val projection = lookingAt(direction.altitudeDegrees, direction.azimuthDegrees)

        assertEquals(SkyTarget.StarTarget(betelgeuse), SkyHitTest.find(502f, 1003f, 40f, sky, bodies, projection))
        assertNull(SkyHitTest.find(50f, 50f, 40f, sky, bodies, projection))
    }

    @Test
    fun `the ISS can be tapped`() {
        val projection = lookingAt(60.0, 200.0)

        assertEquals(SkyTarget.Satellite(25544), SkyHitTest.find(500f, 1000f, 40f, sky, bodies, projection))
    }

    private fun lookingAt(altitude: Double, azimuth: Double) =
        SkyProjection(SkyView.looking(azimuth, altitude), width = 1000f, height = 2000f, fieldOfViewDegrees = 60.0)
}

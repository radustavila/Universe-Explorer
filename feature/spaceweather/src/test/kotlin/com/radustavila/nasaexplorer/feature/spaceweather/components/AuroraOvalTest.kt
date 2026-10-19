package com.radustavila.nasaexplorer.feature.spaceweather.components

import com.radustavila.nasaexplorer.core.model.AuroraForecast
import com.radustavila.nasaexplorer.core.testing.data.TestSpaceWeather
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AuroraOvalTest {

    @Test
    fun `a band all around the pole becomes one strip per hemisphere`() {
        val forecast = TestSpaceWeather.auroraForecast(probability = 40, band = 60..70)

        val rings = AuroraOval.polygons(forecast, minimum = 20)

        assertEquals(2, rings.size)
        val north = rings.first()
        assertEquals(59.5, north.minOf { it.latitude }, 1e-9)
        assertEquals(70.5, north.maxOf { it.latitude }, 1e-9)
        assertEquals(-180.0, north.minOf { it.longitude }, 1e-9)
        assertEquals(180.0, north.maxOf { it.longitude }, 1e-9)
        assertTrue(rings.last().all { it.latitude < 0 })
    }

    @Test
    fun `nothing above the threshold draws nothing, and stacked bands are separate overlays`() {
        val forecast = TestSpaceWeather.auroraForecast(probability = 10)

        assertTrue(AuroraOval.polygons(forecast, minimum = 20).isEmpty())
        val overlays = AuroraOval.overlays(forecast)
        assertEquals(listOf("aurora-5", "aurora-20", "aurora-50"), overlays.map { it.id })
        assertEquals(listOf(2, 0, 0), overlays.map { it.shapes.size })
    }

    @Test
    fun `columns that stop overlapping start a new polygon, two runs become rectangles`() {
        val grid = ByteArray(AuroraForecast.LONGITUDES * AuroraForecast.LATITUDES)
        fun set(longitude: Int, latitudes: IntRange) {
            latitudes.forEach { grid[AuroraForecast.index(Math.floorMod(longitude, 360), it)] = 60 }
        }
        set(0, 60..62)
        set(1, 61..63)
        set(2, 70..72) // no overlap with column 1
        set(3, 50..52)
        set(3, 70..72) // two runs in one column
        val forecast = AuroraForecast(Instant.EPOCH, Instant.EPOCH, grid)

        val rings = AuroraOval.polygons(forecast, minimum = 50)

        assertEquals(4, rings.size)
        // Columns 0 and 1 joined: a strip with two points per column on each edge.
        assertEquals(8, rings[0].size)
        assertTrue(rings.all { ring -> ring.all { it.latitude > 0 } })
    }

    @Test
    fun `cells beyond Web Mercator are clipped`() {
        val forecast = TestSpaceWeather.auroraForecast(probability = 80, band = 80..89)

        val rings = AuroraOval.polygons(forecast, minimum = 50)

        assertEquals(85.0, rings.first().maxOf { it.latitude }, 1e-9)
    }
}

package com.radustavila.nasaexplorer.core.skymap

import kotlin.math.hypot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SkyProjectionTest {

    private val width = 1000f
    private val height = 2000f

    private fun projection(azimuth: Double, altitude: Double, fieldOfView: Double) =
        SkyProjection(SkyView.looking(azimuthDegrees = azimuth, altitudeDegrees = altitude), width, height, fieldOfView)

    @Test
    fun `the view direction lands in the centre`() {
        val projection = projection(135.0, 30.0, 90.0)

        assertTrue(projection.project(SkyVector.horizontal(30.0, 135.0)))

        assertEquals(500f, projection.x, 0.01f)
        assertEquals(1000f, projection.y, 0.01f)
    }

    @Test
    fun `half the field of view reaches the edge of the shorter side`() {
        val projection = projection(0.0, 0.0, 90.0)

        assertTrue(projection.project(SkyVector.horizontal(0.0, 45.0)))
        assertEquals(1000f, projection.x, 0.01f)
        assertEquals(1000f, projection.y, 0.01f)

        // Up is up: a star above the centre is drawn above it.
        assertTrue(projection.project(SkyVector.horizontal(45.0, 0.0)))
        assertEquals(500f, projection.x, 0.01f)
        assertEquals(500f, projection.y, 0.01f)
    }

    @Test
    fun `east is on the right when facing south`() {
        val projection = projection(180.0, 20.0, 90.0)

        assertTrue(projection.project(SkyVector.horizontal(20.0, 160.0)))

        // Facing south, the east (azimuth 90) is to the left, as on a real sky.
        assertTrue(projection.x < 500f)
    }

    @Test
    fun `unproject inverts project`() {
        val projection = projection(300.0, 55.0, 70.0)
        val direction = SkyVector.horizontal(62.0, 287.0)

        assertTrue(projection.project(direction))
        val back = projection.unproject(projection.x, projection.y)

        assertEquals(direction.x, back.x, 1e-5)
        assertEquals(direction.y, back.y, 1e-5)
        assertEquals(direction.z, back.z, 1e-5)
    }

    @Test
    fun `directions behind the viewer are not projected`() {
        val projection = projection(0.0, 0.0, 60.0)

        assertFalse(projection.project(SkyVector.horizontal(0.0, 180.0)))
        assertFalse(projection.project(SkyVector.horizontal(0.0, 100.0)))
        assertNotNull(projection.projectUnbounded(SkyVector.horizontal(0.0, 100.0)))
    }

    @Test
    fun `the horizon is a line through the centre when looking level`() {
        val horizon = projection(45.0, 0.0, 90.0).horizon()

        val line = horizon as HorizonShape.Line
        assertEquals(500f, line.pointX, 0.01f)
        assertEquals(1000f, line.pointY, 0.01f)
        // The sky is towards the top of the screen.
        assertEquals(0f, line.normalX, 1e-4f)
        assertTrue(line.normalY < 0f)
    }

    @Test
    fun `the horizon is a circle through the projected horizon points when looking up or down`() {
        for (altitude in listOf(35.0, -35.0)) {
            val projection = projection(10.0, altitude, 100.0)
            val circle = projection.horizon() as HorizonShape.Circle

            assertEquals(altitude < 0.0, circle.isGroundInside)
            for (azimuth in listOf(-40.0, 10.0, 60.0)) {
                assertTrue(projection.project(SkyVector.horizontal(0.0, azimuth)))
                val distance = hypot(projection.x - circle.centerX, projection.y - circle.centerY).toDouble()
                assertEquals("altitude $altitude, azimuth $azimuth", circle.radius.toDouble(), distance, 0.5)
            }
        }
    }

    @Test
    fun `a view built from angles reports them back`() {
        val view = SkyView.looking(azimuthDegrees = 222.0, altitudeDegrees = -12.0)

        assertEquals(222.0, view.azimuthDegrees, 1e-9)
        assertEquals(-12.0, view.altitudeDegrees, 1e-9)
        assertEquals(1.0, view.right.length, 1e-12)
        assertEquals(0.0, view.right dot view.up, 1e-12)
        // Level camera: its right edge stays on the horizon.
        assertEquals(0.0, view.right.z, 1e-12)
    }

    @Test
    fun `a sensor basis is orthonormalised`() {
        val view = SkyView.fromBasis(forward = SkyVector(0.0, 2.0, 0.1), up = SkyVector(0.05, 0.1, 1.0))

        assertEquals(1.0, view.forward.length, 1e-12)
        assertEquals(1.0, view.up.length, 1e-12)
        assertEquals(0.0, view.forward dot view.up, 1e-12)
        assertEquals(0.0, view.azimuthDegrees, 1e-9)
    }

    @Test
    fun `straight up the azimuth follows the top of the screen`() {
        // Facing east and tilted back to the zenith, the top of the phone points west.
        val view = SkyView.fromBasis(forward = SkyVector(0.0, 0.0, 1.0), up = SkyVector(-1.0, 0.0, 0.0))

        assertEquals(90.0, view.altitudeDegrees, 1e-9)
        assertEquals(90.0, view.azimuthDegrees, 1e-9)
    }
}

package com.radustavila.nasaexplorer.feature.skymap

import com.radustavila.nasaexplorer.core.skymap.SkyView
import com.radustavila.nasaexplorer.feature.skymap.sensor.OrientationMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class OrientationMathTest {

    /** Device axes in the world (east, north, up) as the columns of a row-major matrix. */
    private fun matrix(x: DoubleArray, y: DoubleArray, z: DoubleArray) = FloatArray(9) { i ->
        val row = i / 3
        when (i % 3) {
            0 -> x[row]
            1 -> y[row]
            else -> z[row]
        }.toFloat()
    }

    /** Screen x to the east, screen y up, screen z (towards the user) to the south. */
    private val uprightFacingNorth =
        matrix(x = doubleArrayOf(1.0, 0.0, 0.0), y = doubleArrayOf(0.0, 0.0, 1.0), z = doubleArrayOf(0.0, -1.0, 0.0))

    @Test
    fun `a phone held upright facing north looks north at the horizon`() {
        val view = OrientationMath.view(uprightFacingNorth, 0f)

        assertEquals(0.0, view.azimuthDegrees, 1e-6)
        assertEquals(0.0, view.altitudeDegrees, 1e-6)
    }

    @Test
    fun `a phone lying face up looks at the ground`() {
        val view = OrientationMath.view(
            matrix(
                x = doubleArrayOf(1.0, 0.0, 0.0),
                y = doubleArrayOf(0.0, 1.0, 0.0),
                z = doubleArrayOf(0.0, 0.0, 1.0),
            ),
            0f,
        )

        assertEquals(-90.0, view.altitudeDegrees, 1e-6)
    }

    @Test
    fun `declination turns magnetic north into true north`() {
        // Facing magnetic north where it lies 6 degrees east of true north.
        val view = OrientationMath.view(uprightFacingNorth, 6f)

        assertEquals(6.0, view.azimuthDegrees, 1e-4)
    }

    @Test
    fun `small changes are smoothed, big turns are taken at once`() {
        val previous = SkyView.looking(0.0, 20.0)

        val nudged = OrientationMath.smooth(previous, SkyView.looking(4.0, 20.0))
        assertEquals(1.2, nudged.azimuthDegrees, 0.05)

        val turned = SkyView.looking(90.0, 20.0)
        assertSame(turned, OrientationMath.smooth(previous, turned))
    }
}

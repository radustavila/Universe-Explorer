package com.radustavila.nasaexplorer.feature.skymap

import com.radustavila.nasaexplorer.feature.skymap.ar.CameraFieldOfView
import com.radustavila.nasaexplorer.feature.skymap.ar.CameraFieldOfView.shorterSide
import com.radustavila.nasaexplorer.feature.skymap.ar.CameraLens
import org.junit.Assert.assertEquals
import org.junit.Test

/** Lens and sensor values as Camera2 reports them for the main camera of a few phones. */
class CameraFieldOfViewTest {

    // Pixel 4: 4.38 mm lens on a 1/2.55" 4:3 sensor.
    private val pixel4 = CameraLens(4.38, 5.64, 4.23)

    // Galaxy S21: 5.4 mm lens on a 1/1.76" 4:3 sensor.
    private val galaxyS21 = CameraLens(5.4, 7.3, 5.5)

    @Test
    fun `a preview the shape of the stream shows the whole sensor`() {
        val fieldOfView = CameraFieldOfView.preview(
            pixel4,
            streamWidth = 1440,
            streamHeight = 1080,
            rotationDegrees = 0,
            viewWidth = 1440,
            viewHeight = 1080,
        )

        assertEquals(65.55, fieldOfView.horizontalDegrees, 0.01)
        assertEquals(51.55, fieldOfView.verticalDegrees, 0.01)
    }

    @Test
    fun `a tall portrait screen crops the sides of a 4 by 3 picture`() {
        // Pixel 4 held upright: the sensor's long side runs up the 1080 x 2280 screen.
        val fieldOfView = CameraFieldOfView.preview(
            pixel4,
            streamWidth = 1440,
            streamHeight = 1080,
            rotationDegrees = 90,
            viewWidth = 1080,
            viewHeight = 2280,
        )

        assertEquals(33.92, fieldOfView.horizontalDegrees, 0.01)
        assertEquals(65.55, fieldOfView.verticalDegrees, 0.01)
        assertEquals(33.92, fieldOfView.shorterSide(1080, 2280), 0.01)
    }

    @Test
    fun `a 16 by 9 stream crops a 4 by 3 sensor top and bottom, the wide screen crops it again`() {
        // Galaxy S21 in landscape, 1920 x 1080 stream on a 2400 x 1080 screen.
        val fieldOfView = CameraFieldOfView.preview(
            galaxyS21,
            streamWidth = 1920,
            streamHeight = 1080,
            rotationDegrees = 0,
            viewWidth = 2400,
            viewHeight = 1080,
        )

        assertEquals(68.11, fieldOfView.horizontalDegrees, 0.01)
        assertEquals(33.84, fieldOfView.verticalDegrees, 0.01)
        assertEquals(33.84, fieldOfView.shorterSide(2400, 1080), 0.01)
    }

    @Test
    fun `only the active part of the pixel array counts`() {
        // Pixel 7: 6.81 mm lens, 9.8 x 7.4 mm pixel array of 4080 x 3072, 4032 x 3024 of them active.
        val pixel7 = CameraLens.fromSensor(6.81, 9.8, 7.4, 4080, 3072, 4032, 3024)

        assertEquals(9.685, pixel7.activeWidthMillimetres, 0.001)
        assertEquals(7.284, pixel7.activeHeightMillimetres, 0.001)

        val fieldOfView = CameraFieldOfView.preview(
            pixel7,
            streamWidth = 1440,
            streamHeight = 1080,
            rotationDegrees = 270,
            viewWidth = 1080,
            viewHeight = 2400,
        )
        assertEquals(35.49, fieldOfView.horizontalDegrees, 0.01)
        assertEquals(70.83, fieldOfView.verticalDegrees, 0.01)
    }

    @Test
    fun `zooming narrows the view as a crop of the middle`() {
        assertEquals(32.20, CameraFieldOfView.zoomed(60.0, 2.0), 0.01)
        assertEquals(60.0, CameraFieldOfView.zoomed(60.0, 1.0), 1e-9)

        val zoomedIn = CameraFieldOfView.preview(pixel4, 1440, 1080, 90, 1080, 2280, zoomRatio = 2.0)
        assertEquals(CameraFieldOfView.zoomed(33.92, 2.0), zoomedIn.horizontalDegrees, 0.01)
    }
}

package com.radustavila.nasaexplorer.feature.skymap

import com.radustavila.nasaexplorer.core.skymap.SkyVector
import com.radustavila.nasaexplorer.feature.skymap.ar.ArLens
import com.radustavila.nasaexplorer.feature.skymap.ar.ArProblem
import com.radustavila.nasaexplorer.feature.skymap.ar.SkyArState
import com.radustavila.nasaexplorer.feature.skymap.ar.StillnessDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SkyArStateTest {

    private val twoMinutes = StillnessDetector.TIMEOUT.toMillis()

    @Test
    fun `pinching zooms the camera within its range and narrows the view`() {
        val ar = SkyArState()
        ar.turnOn(nowMillis = 0)
        assertNull(ar.fieldOfViewDegrees)

        ar.onCameraReady(ArLens(fieldOfViewDegrees = 60.0, minZoomRatio = 1f, maxZoomRatio = 4f))
        assertEquals(60.0, ar.fieldOfViewDegrees!!, 1e-9)

        ar.zoom(2f)
        assertEquals(2f, ar.zoomRatio, 1e-6f)
        assertEquals(32.2, ar.fieldOfViewDegrees!!, 0.01)
        ar.zoom(10f)
        assertEquals(4f, ar.zoomRatio, 1e-6f)
        ar.zoom(0.01f)
        assertEquals(1f, ar.zoomRatio, 1e-6f)
    }

    @Test
    fun `switching off forgets the camera and says why`() {
        val ar = SkyArState()
        ar.turnOn(nowMillis = 0)
        ar.onCameraReady(ArLens(60.0))
        ar.isAligning = true

        ar.turnOff(ArProblem.CAMERA_FAILED)

        assertFalse(ar.isOn)
        assertFalse(ar.isAligning)
        assertNull(ar.lens)
        assertEquals(ArProblem.CAMERA_FAILED, ar.problem)

        ar.turnOn(nowMillis = 0)
        assertNull(ar.problem)
    }

    @Test
    fun `a phone put down is still after two minutes, one held up is not`() {
        val detector = StillnessDetector()
        val north = SkyVector.horizontal(30.0, 0.0)
        detector.reset(nowMillis = 0)
        assertFalse(detector.isStill(twoMinutes - 1))

        // Lying on a table: readings jitter a tenth of a degree.
        detector.onView(SkyVector.horizontal(30.1, 0.1), nowMillis = 1_000)
        detector.onView(north, nowMillis = 60_000)
        assertTrue(detector.isStill(1_000 + twoMinutes))

        // Held up, the phone wanders a couple of degrees.
        detector.onView(SkyVector.horizontal(32.0, 1.0), nowMillis = 100_000)
        assertFalse(detector.isStill(1_000 + twoMinutes))
        assertTrue(detector.isStill(100_000 + twoMinutes))
    }

    @Test
    fun `stillness waits for a reading after being restored`() {
        assertFalse(StillnessDetector().isStill(nowMillis = Long.MAX_VALUE / 2))
    }
}

package com.radustavila.nasaexplorer.feature.skymap

import com.radustavila.nasaexplorer.core.skymap.SkyLens
import com.radustavila.nasaexplorer.core.skymap.SkyProjection
import com.radustavila.nasaexplorer.core.skymap.SkyVector
import com.radustavila.nasaexplorer.core.skymap.SkyView
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SkyCameraStateTest {

    @Test
    fun `dragging keeps the sky under the finger`() {
        val camera = SkyCameraState(CameraMode.TOUCH, azimuthDegrees = 180.0, altitudeDegrees = 20.0)
        val projection = SkyProjection(camera.view, 1000f, 2000f, camera.fieldOfViewDegrees)
        val grabbed = projection.unproject(600f, 1000f)

        camera.drag(projection, 600f, 1000f, 400f, 1000f)

        val after = SkyProjection(camera.view, 1000f, 2000f, camera.fieldOfViewDegrees)
        after.project(grabbed)
        assertEquals(400f, after.x, 5f)
        assertEquals(1000f, after.y, 5f)
    }

    @Test
    fun `dragging in compass mode switches to touch from where the phone pointed`() {
        val camera = SkyCameraState(CameraMode.COMPASS)
        camera.sensorView = SkyView.looking(azimuthDegrees = 75.0, altitudeDegrees = 40.0)
        val projection = SkyProjection(camera.view, 1000f, 2000f, camera.fieldOfViewDegrees)

        camera.drag(projection, 500f, 1000f, 500f, 1000f)

        assertEquals(CameraMode.TOUCH, camera.mode)
        assertEquals(75.0, camera.azimuthDegrees, 1e-6)
        assertEquals(40.0, camera.altitudeDegrees, 1e-6)
    }

    @Test
    fun `zoom stays within limits`() {
        val camera = SkyCameraState(CameraMode.TOUCH)

        camera.zoom(2f)
        assertEquals(45.0, camera.fieldOfViewDegrees, 1e-9)
        camera.zoom(100f)
        assertEquals(SkyCameraState.MIN_FIELD_OF_VIEW, camera.fieldOfViewDegrees, 1e-9)
        camera.zoom(0.001f)
        assertEquals(SkyCameraState.MAX_FIELD_OF_VIEW, camera.fieldOfViewDegrees, 1e-9)
    }

    @Test
    fun `looking at a target turns the short way, at once with reduced motion`() = runTest {
        val camera = SkyCameraState(CameraMode.TOUCH, azimuthDegrees = 350.0, altitudeDegrees = 10.0)

        camera.lookAt(SkyVector.horizontal(50.0, 20.0), animate = false)

        assertEquals(20.0, camera.azimuthDegrees, 1e-6)
        assertEquals(50.0, camera.altitudeDegrees, 1e-6)
        assertEquals(30.0, SkyCameraState.shortestTurn(350.0, 20.0), 1e-9)
        assertEquals(-30.0, SkyCameraState.shortestTurn(20.0, 350.0), 1e-9)
    }

    @Test
    fun `over the camera the map takes the lens's field of view and gives it back`() {
        val camera = SkyCameraState(CameraMode.COMPASS, fieldOfViewDegrees = 90.0)

        camera.arFieldOfViewDegrees = 34.0
        camera.zoom(2f)
        assertEquals(34.0, camera.fieldOfViewDegrees, 1e-9)
        assertEquals(SkyLens.RECTILINEAR, camera.lens)

        camera.arFieldOfViewDegrees = null
        assertEquals(SkyLens.STEREOGRAPHIC, camera.lens)
        // The pinch made meanwhile still counts for the plain map.
        assertEquals(45.0, camera.fieldOfViewDegrees, 1e-9)
    }

    @Test
    fun `aligning drags the drawn Moon onto the real one while the phone stays still`() {
        val camera = SkyCameraState(CameraMode.COMPASS)
        camera.sensorView = SkyView.looking(azimuthDegrees = 120.0, altitudeDegrees = 30.0)
        camera.arFieldOfViewDegrees = 40.0
        val moon = SkyVector.horizontal(32.0, 118.0)
        val before = camera.projection(1000f, 2000f)
        assertTrue(before.project(moon))
        val drawnX = before.x
        val drawnY = before.y

        // The real Moon is 150 px left of and 80 px above the drawn one.
        camera.align(before, drawnX, drawnY, drawnX - 150f, drawnY - 80f)

        val after = camera.projection(1000f, 2000f)
        assertTrue(after.project(moon))
        assertEquals(drawnX - 150f, after.x, 1f)
        assertEquals(drawnY - 80f, after.y, 1f)
        assertEquals(CameraMode.COMPASS, camera.mode)
        assertNotEquals(SkyAlignment.NONE, camera.alignment)

        camera.resetAlignment()
        assertEquals(SkyAlignment.NONE, camera.alignment)
    }

    @Test
    fun `the alignment only corrects the view over the camera`() {
        val sensor = SkyView.looking(azimuthDegrees = 200.0, altitudeDegrees = 10.0)
        val alignment = SkyAlignment(azimuthDegrees = 12.0, altitudeDegrees = 3.0)
        val camera = SkyCameraState(CameraMode.COMPASS, alignment = alignment)
        camera.sensorView = sensor

        assertEquals(200.0, camera.view.azimuthDegrees, 1e-6)

        camera.arFieldOfViewDegrees = 40.0
        assertEquals(212.0, camera.view.azimuthDegrees, 1e-6)
        assertEquals(13.0, camera.view.altitudeDegrees, 1e-6)
    }

    @Test
    fun `alignment wraps the azimuth and bounds the tilt`() {
        val alignment = SkyAlignment(azimuthDegrees = 170.0, altitudeDegrees = 25.0).turnedBy(20.0, 20.0)

        assertEquals(-170.0, alignment.azimuthDegrees, 1e-9)
        assertEquals(30.0, alignment.altitudeDegrees, 1e-9)
    }

    @Test
    fun `compass mode does not swing the map to a target`() = runTest {
        val camera = SkyCameraState(CameraMode.COMPASS, azimuthDegrees = 100.0)

        camera.lookAt(SkyVector.horizontal(50.0, 20.0), animate = false)

        assertEquals(100.0, camera.azimuthDegrees, 1e-6)
    }
}

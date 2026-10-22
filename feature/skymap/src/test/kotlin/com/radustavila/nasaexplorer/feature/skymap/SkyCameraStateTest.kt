package com.radustavila.nasaexplorer.feature.skymap

import com.radustavila.nasaexplorer.core.skymap.SkyProjection
import com.radustavila.nasaexplorer.core.skymap.SkyVector
import com.radustavila.nasaexplorer.core.skymap.SkyView
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
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
    fun `compass mode does not swing the map to a target`() = runTest {
        val camera = SkyCameraState(CameraMode.COMPASS, azimuthDegrees = 100.0)

        camera.lookAt(SkyVector.horizontal(50.0, 20.0), animate = false)

        assertEquals(100.0, camera.azimuthDegrees, 1e-6)
    }
}

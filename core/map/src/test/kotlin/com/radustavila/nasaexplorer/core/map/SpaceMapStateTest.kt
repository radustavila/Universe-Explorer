package com.radustavila.nasaexplorer.core.map

import androidx.compose.runtime.saveable.SaverScope
import com.radustavila.nasaexplorer.core.map.overlay.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Test

class SpaceMapStateTest {

    @Test
    fun `the camera survives being saved and restored`() {
        val state = SpaceMapState(MapCamera(GeoPoint(latitude = 44.4, longitude = 26.1), zoom = 5.5))

        val saved = with(SpaceMapState.Saver) { SaverScope { true }.save(state) }
        val restored = SpaceMapState.Saver.restore(requireNotNull(saved))

        assertEquals(state.camera, restored?.camera)
    }

    @Test
    fun `camera requests wait for the map to pick them up`() {
        val state = SpaceMapState()

        state.animateTo(GeoPoint(latitude = 10.0, longitude = 20.0), zoom = 4.0)

        assertEquals(CameraMove(MapCamera(GeoPoint(10.0, 20.0), 4.0), animate = true), state.pendingMove)
    }
}

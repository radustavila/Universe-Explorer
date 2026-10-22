package com.radustavila.nasaexplorer.core.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalInspectionMode
import com.radustavila.nasaexplorer.core.map.internal.SpaceMapHost
import com.radustavila.nasaexplorer.core.map.internal.SpaceMapHostEffects
import com.radustavila.nasaexplorer.core.map.overlay.GeoPoint

/** Where the map looks: its centre and MapLibre zoom level (0 = whole world in one tile). */
@Immutable
data class MapCamera(val center: GeoPoint, val zoom: Double) {
    companion object {
        val World = MapCamera(center = GeoPoint(latitude = 20.0, longitude = 0.0), zoom = 1.0)
    }
}

/** Whether the imagery tiles the map asked for arrived. */
enum class TileLoadStatus {
    /** Every recent tile request succeeded, or none was needed. */
    Loaded,

    /** Requests failed for lack of a connection (or a server error): only cached tiles show. */
    NetworkError,

    /** GIBS has no imagery for the requested layer and date (HTTP 4xx). */
    ImageryUnavailable,
}

/**
 * Hoisted state of a [SpaceMap]: the camera (saved across configuration changes) and the health of
 * tile loading, which screens turn into an offline banner.
 *
 * State made by [rememberSpaceMapState] also owns the map's native view, for as long as the
 * composable that remembered it stays composed. A [SpaceMap] inside a lazy list therefore keeps its
 * tiles, style and camera when it scrolls out of view and back, instead of rebuilding the map.
 */
@Stable
class SpaceMapState(initialCamera: MapCamera = MapCamera.World) {

    /** Last settled camera position; updated when the user stops panning or zooming. */
    var camera: MapCamera by mutableStateOf(initialCamera)
        internal set

    var tileStatus: TileLoadStatus by mutableStateOf(TileLoadStatus.Loaded)
        internal set

    internal var pendingMove: CameraMove? by mutableStateOf(null)

    /** The map view this state owns; set while [rememberSpaceMapState]'s caller is composed. */
    internal var host: SpaceMapHost? = null

    /** Flies the map to [center], e.g. to the event a user picked from a list. */
    fun animateTo(center: GeoPoint, zoom: Double = camera.zoom) {
        pendingMove = CameraMove(MapCamera(center, zoom), animate = true)
    }

    /** Jumps to [camera] without animation. */
    fun moveTo(camera: MapCamera) {
        pendingMove = CameraMove(camera, animate = false)
    }

    companion object {
        val Saver: Saver<SpaceMapState, List<Double>> = Saver(
            save = { listOf(it.camera.center.latitude, it.camera.center.longitude, it.camera.zoom) },
            restore = { (latitude, longitude, zoom) ->
                SpaceMapState(MapCamera(GeoPoint(latitude, longitude), zoom))
            },
        )
    }
}

internal data class CameraMove(val camera: MapCamera, val animate: Boolean)

/**
 * Remembers a [SpaceMapState] together with the map view it owns. Call it at screen level, outside
 * any lazy list, so the view outlives the list item that shows it.
 */
@Composable
fun rememberSpaceMapState(initialCamera: MapCamera = MapCamera.World): SpaceMapState {
    val state = rememberSaveable(saver = SpaceMapState.Saver) { SpaceMapState(initialCamera) }
    if (!LocalInspectionMode.current) SpaceMapHostEffects(state)
    return state
}

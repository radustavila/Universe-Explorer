package com.radustavila.nasaexplorer.core.map

import androidx.compose.runtime.Immutable
import com.radustavila.nasaexplorer.core.map.gibs.GibsImagery

/** Which body a [SpaceMap] shows, and from which tiles. Overlays work the same on every body. */
@Immutable
sealed interface SpaceMapStyle {

    /** Earth from NASA GIBS: Blue Marble as the base, with [imagery] (or nothing more) above it. */
    data class Earth(val imagery: GibsImagery?) : SpaceMapStyle

    /**
     * The Moon from the app's own Web Mercator tiles of the LRO WAC global mosaic (see
     * `core/map/tools/build_moon_tiles.sh`). Zoom 0–4 ship in the APK, so the map works offline.
     * [deepTilesDirectory] is the folder holding the zoom 5–6 tiles (`{z}/{x}/{y}.jpg`), once the
     * on-demand pack is on the device; without it the map stretches zoom 4.
     *
     * Web Mercator stops at ±85.05° latitude: the polar caps are not on the map.
     */
    data class Moon(val deepTilesDirectory: String? = null) : SpaceMapStyle {
        companion object {
            /** Latitude where the Mercator tiles end, north and south. */
            const val MAX_LATITUDE = 85.05112878

            /** Deepest zoom with tiles of its own (from the on-demand pack). */
            const val MAX_TILE_ZOOM = 6

            /** Deepest zoom in the APK. */
            const val MAX_BUNDLED_ZOOM = 4
        }
    }
}

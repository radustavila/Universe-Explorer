package com.radustavila.nasaexplorer.core.map

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Moon tiles checked in by `core/map/tools/build_moon_tiles.sh`: every tile of zoom 0–4 in the
 * APK's assets (so the map works offline), and every tile of zoom 5–6 in the on-demand pack.
 */
class MoonTileAssetsTest {

    @Test
    fun `the APK carries every tile from zoom 0 to 4`() {
        assertCompleteZooms(File(APK_TILES), 0..SpaceMapStyle.Moon.MAX_BUNDLED_ZOOM)
        assertEquals(listOf("0", "1", "2", "3", "4"), File(APK_TILES).list()!!.sorted())
    }

    @Test
    fun `the on-demand pack carries every tile of zoom 5 and 6`() {
        val zooms = (SpaceMapStyle.Moon.MAX_BUNDLED_ZOOM + 1)..SpaceMapStyle.Moon.MAX_TILE_ZOOM
        assertCompleteZooms(File(PACK_TILES), zooms)
        assertEquals(listOf("5", "6"), File(PACK_TILES).list()!!.sorted())
    }

    private fun assertCompleteZooms(root: File, zooms: IntRange) {
        for (z in zooms) {
            val size = 1 shl z
            for (x in 0 until size) {
                for (y in 0 until size) {
                    val tile = File(root, "$z/$x/$y.jpg")
                    assertTrue("missing $tile", tile.isFile && tile.length() > 0)
                }
            }
        }
    }

    private companion object {
        // Unit tests run with the module directory as the working directory.
        const val APK_TILES = "src/main/assets/moon_tiles"
        const val PACK_TILES = "../../moontiles/src/main/assets/moon_tiles"
    }
}

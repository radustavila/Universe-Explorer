package com.radustavila.nasaexplorer.core.map.internal

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/** [BitmapNoDataKeyer] through Android's real JPEG and PNG codecs. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BitmapNoDataKeyerTest {

    @Test
    fun `a tile with a black polar band comes back as a PNG with the band transparent`() {
        // Like a VIIRS tile across the Arctic in October: no data above, imagery below.
        val tile = jpegTile { y -> if (y < BAND_ROWS) Color.BLACK else OCEAN }

        val png = checkNotNull(BitmapNoDataKeyer.key(tile)) { "expected a keyed tile" }
        val keyed = BitmapFactory.decodeByteArray(png, 0, png.size)

        assertEquals(0, Color.alpha(keyed.getPixel(TILE_SIZE / 2, 20)))
        assertEquals(255, Color.alpha(keyed.getPixel(TILE_SIZE / 2, 240)))
    }

    @Test
    fun `a tile without no-data, or one that does not decode, is left alone`() {
        assertNull(BitmapNoDataKeyer.key(jpegTile { OCEAN }))
        assertNull(BitmapNoDataKeyer.key(ByteArray(0)))
        assertNull(BitmapNoDataKeyer.key("not an image".encodeToByteArray()))
    }

    private fun jpegTile(colorOfRow: (Int) -> Int): ByteArray {
        val bitmap = Bitmap.createBitmap(TILE_SIZE, TILE_SIZE, Bitmap.Config.ARGB_8888)
        for (y in 0 until TILE_SIZE) for (x in 0 until TILE_SIZE) bitmap.setPixel(x, y, colorOfRow(y))
        return ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            out.toByteArray()
        }
    }

    private companion object {
        const val TILE_SIZE = 256
        const val BAND_ROWS = 192
        const val JPEG_QUALITY = 90
        val OCEAN = Color.rgb(40, 90, 160)
    }
}

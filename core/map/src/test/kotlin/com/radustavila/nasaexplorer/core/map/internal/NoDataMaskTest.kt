package com.radustavila.nasaexplorer.core.map.internal

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NoDataMaskTest {

    @Test
    fun `near-black pixels become transparent and the rest stay`() {
        val ocean = Color.rgb(12, 30, 70)
        val pixels = intArrayOf(Color.BLACK, Color.rgb(9, 4, 12), ocean, Color.WHITE)

        assertEquals(2, NoDataMask.clear(pixels))
        assertArrayEquals(intArrayOf(Color.TRANSPARENT, Color.TRANSPARENT, ocean, Color.WHITE), pixels)
    }

    @Test
    fun `a tile with a black polar band comes back as a PNG with the band transparent`() {
        // Like a VIIRS tile across the Arctic in October: black above, imagery below.
        val tile = jpegTile { y -> if (y < 192) Color.BLACK else Color.rgb(40, 90, 160) }

        val png = NoDataMask.transcode(tile)!!
        val masked = BitmapFactory.decodeByteArray(png, 0, png.size)

        assertEquals(0, Color.alpha(masked.getPixel(128, 20)))
        assertEquals(255, Color.alpha(masked.getPixel(128, 240)))
    }

    @Test
    fun `a tile with no no-data is left alone`() {
        assertNull(NoDataMask.transcode(jpegTile { Color.rgb(40, 90, 160) }))
        assertNull(NoDataMask.transcode(ByteArray(0)))
        assertNull(NoDataMask.transcode("not an image".encodeToByteArray()))
    }

    private fun jpegTile(colorOfRow: (Int) -> Int): ByteArray {
        val bitmap = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888)
        for (y in 0 until 256) for (x in 0 until 256) bitmap.setPixel(x, y, colorOfRow(y))
        return ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            out.toByteArray()
        }
    }
}

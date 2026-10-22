package com.radustavila.nasaexplorer.core.map.internal

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream

/**
 * GIBS daily imagery (VIIRS, MODIS) comes as opaque JPEG tiles that paint "no data" black: the
 * polar region where the Sun stays too low, and the gaps between a day's satellite passes. Drawn
 * as is, that black hides the Blue Marble base under it, and the map opens with a black band
 * across the Arctic (north of about 70° N in October). This turns those pixels transparent, so the
 * base shows through wherever the day's imagery has nothing.
 */
internal object NoDataMask {

    /**
     * JPEG leaves the no-data black a few levels above zero. Real imagery is rarely this dark in
     * every channel (night-time ocean is not imaged at all; daytime ocean is dark blue).
     */
    private const val MAX_NO_DATA_LEVEL = 12

    /** Below this share of no-data pixels a tile is left as it is: a few black specks don't show. */
    private const val MIN_NO_DATA_SHARE = 0.01

    /** PNG is lossless; the quality argument is ignored but must be in 0..100. */
    private const val PNG_QUALITY = 100

    /** Makes the near-black pixels of [pixels] (ARGB) transparent, in place; returns how many. */
    fun clear(pixels: IntArray): Int {
        var cleared = 0
        for (i in pixels.indices) {
            val pixel = pixels[i]
            val red = pixel shr 16 and 0xFF
            val green = pixel shr 8 and 0xFF
            val blue = pixel and 0xFF
            if (red <= MAX_NO_DATA_LEVEL && green <= MAX_NO_DATA_LEVEL && blue <= MAX_NO_DATA_LEVEL) {
                pixels[i] = 0
                cleared++
            }
        }
        return cleared
    }

    /**
     * The tile re-encoded as PNG with its no-data transparent, or null when it has (almost) no
     * no-data or cannot be decoded; the original bytes are then the better tile.
     */
    fun transcode(tile: ByteArray): ByteArray? {
        val bitmap = decodeMutable(tile) ?: return null
        try {
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            val worthMasking = clear(pixels) >= pixels.size * MIN_NO_DATA_SHARE
            return if (worthMasking) {
                bitmap.setHasAlpha(true)
                bitmap.setPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
                ByteArrayOutputStream().use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, out)
                    out.toByteArray()
                }
            } else {
                null
            }
        } finally {
            bitmap.recycle()
        }
    }

    private fun decodeMutable(tile: ByteArray): Bitmap? {
        if (tile.isEmpty()) return null
        val options = BitmapFactory.Options().apply {
            inMutable = true
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return BitmapFactory.decodeByteArray(tile, 0, tile.size, options)
    }
}

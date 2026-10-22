package com.radustavila.nasaexplorer.core.map.internal

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream

/**
 * Makes the no-data areas of a GIBS daily mosaic tile transparent, so the layer under it (another
 * satellite's pass, or the Blue Marble base) shows through instead of black wedges and seams.
 */
internal fun interface NoDataKeyer {
    /** The tile as a PNG with transparent no-data pixels, or `null` when it has none to key out. */
    fun key(tile: ByteArray): ByteArray?
}

/** [NoDataKeyer] on Android's bitmap codecs: decode the JPEG, key out black, encode PNG. */
internal object BitmapNoDataKeyer : NoDataKeyer {
    override fun key(tile: ByteArray): ByteArray? {
        val decoded = BitmapFactory.decodeByteArray(tile, 0, tile.size) ?: return null
        val width = decoded.width
        val height = decoded.height
        val pixels = IntArray(width * height)
        decoded.getPixels(pixels, 0, width, 0, 0, width, height)
        decoded.recycle()
        return if (NoDataPixels.keyOut(pixels)) encodePng(pixels, width, height, sizeHint = tile.size * 2) else null
    }

    private fun encodePng(pixels: IntArray, width: Int, height: Int, sizeHint: Int): ByteArray {
        val keyed = Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
        return ByteArrayOutputStream(sizeHint).use { out ->
            keyed.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY_IGNORED, out)
            keyed.recycle()
            out.toByteArray()
        }
    }

    private const val PNG_QUALITY_IGNORED = 100
}

/**
 * GIBS fills areas without data with black (0, 0, 0), which JPEG smears into near-black. Pixels
 * whose brightest channel is at most [TRANSPARENT_UP_TO] become transparent; up to [OPAQUE_FROM]
 * they fade in, so the JPEG ringing along a swath edge leaves no dark seam. Real imagery is never
 * that dark in daylight true colour: even deep ocean has a blue channel well above it.
 */
internal object NoDataPixels {
    const val TRANSPARENT_UP_TO = 6
    const val OPAQUE_FROM = 24

    /** Rewrites [argb] in place; true when any pixel changed. */
    fun keyOut(argb: IntArray): Boolean {
        var changed = false
        for (index in argb.indices) {
            val color = argb[index]
            val red = (color shr RED_SHIFT) and CHANNEL
            val green = (color shr GREEN_SHIFT) and CHANNEL
            val brightest = maxOf(red, green, color and CHANNEL)
            if (brightest >= OPAQUE_FROM) continue
            val alpha = ((brightest - TRANSPARENT_UP_TO).coerceAtLeast(0) * CHANNEL) / (OPAQUE_FROM - TRANSPARENT_UP_TO)
            argb[index] = (alpha shl ALPHA_SHIFT) or (color and RGB)
            changed = true
        }
        return changed
    }

    private const val CHANNEL = 0xFF
    private const val RGB = 0xFFFFFF
    private const val ALPHA_SHIFT = 24
    private const val RED_SHIFT = 16
    private const val GREEN_SHIFT = 8
}

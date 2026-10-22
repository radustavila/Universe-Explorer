package com.radustavila.nasaexplorer.feature.widget.sky

import android.graphics.Bitmap
import android.graphics.Color
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MoonBitmapTest {

    private val size = 100

    /** Whether the point a tenth of the way in from the left or right edge, at mid height, is lit. */
    private fun Bitmap.litAt(right: Boolean): Boolean {
        val x = if (right) size * 9 / 10 else size / 10
        return Color.red(getPixel(x, size / 2)) > LIT_THRESHOLD
    }

    @Test
    fun `a waxing crescent is lit on the right, a waning one on the left`() {
        val waxing = drawMoon(phaseAngleDegrees = 45.0, sizePx = size)
        val waning = drawMoon(phaseAngleDegrees = 315.0, sizePx = size)

        assertTrue(waxing.litAt(right = true))
        assertFalse(waxing.litAt(right = false))
        assertTrue(waning.litAt(right = false))
        assertFalse(waning.litAt(right = true))
    }

    @Test
    fun `new moon is dark and full moon lit all over`() {
        val new = drawMoon(phaseAngleDegrees = 0.0, sizePx = size)
        val full = drawMoon(phaseAngleDegrees = 180.0, sizePx = size)

        assertFalse(new.litAt(right = true) || new.litAt(right = false))
        assertTrue(full.litAt(right = true) && full.litAt(right = false))
    }

    @Test
    fun `a gibbous moon is lit past the middle`() {
        val gibbous = drawMoon(phaseAngleDegrees = 135.0, sizePx = size)

        assertTrue(Color.red(gibbous.getPixel(size * 2 / 5, size / 2)) > LIT_THRESHOLD)
        assertFalse(gibbous.litAt(right = false))
    }

    @Test
    fun `seen from the south the waxing Moon is lit on the left`() {
        val waxing = drawMoon(phaseAngleDegrees = 45.0, sizePx = size, southernHemisphere = true)

        assertTrue(waxing.litAt(right = false))
        assertFalse(waxing.litAt(right = true))
    }

    private companion object {
        /** The lit side is near white, the dark side a dim grey (0x2B). */
        const val LIT_THRESHOLD = 0x80
    }
}

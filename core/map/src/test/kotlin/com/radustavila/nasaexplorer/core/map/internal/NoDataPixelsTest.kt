package com.radustavila.nasaexplorer.core.map.internal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NoDataPixelsTest {

    private fun rgb(red: Int, green: Int, blue: Int) = (0xFF shl 24) or (red shl 16) or (green shl 8) or blue

    private fun alphaOf(color: Int) = color ushr 24

    @Test
    fun `black no-data and its JPEG noise turn transparent`() {
        val pixels = intArrayOf(rgb(0, 0, 0), rgb(3, 5, NoDataPixels.TRANSPARENT_UP_TO))

        assertTrue(NoDataPixels.keyOut(pixels))

        assertEquals(listOf(0, 0), pixels.map(::alphaOf))
    }

    @Test
    fun `the ringing along a swath edge fades in, keeping its colour`() {
        val pixels = intArrayOf(rgb(10, 12, 15))

        NoDataPixels.keyOut(pixels)

        val alpha = alphaOf(pixels.single())
        assertTrue(alpha in 1..254)
        assertEquals(rgb(10, 12, 15) and 0xFFFFFF, pixels.single() and 0xFFFFFF)
    }

    @Test
    fun `imagery, even dark ocean, stays opaque and untouched`() {
        val ocean = rgb(8, 20, NoDataPixels.OPAQUE_FROM + 20)
        val cloud = rgb(240, 240, 245)
        val pixels = intArrayOf(ocean, cloud)

        assertFalse(NoDataPixels.keyOut(pixels))

        assertEquals(listOf(ocean, cloud), pixels.toList())
    }
}

package com.radustavila.nasaexplorer.core.skymap

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StarStyleTest {

    @Test
    fun `colours run from blue to red with B-V`() {
        assertEquals(0, StarStyle.colourBucket(-0.3f))
        assertEquals(3, StarStyle.colourBucket(0.65f))
        assertEquals(6, StarStyle.colourBucket(1.85f))
        assertEquals(StarStyle.COLOUR_BUCKETS - 1, StarStyle.colourBucket(Float.NaN))
    }

    @Test
    fun `zooming in shows fainter stars`() {
        assertEquals(6.5f, StarStyle.magnitudeLimit(20.0), 0.0f)
        assertTrue(StarStyle.magnitudeLimit(120.0) < StarStyle.magnitudeLimit(60.0))
        assertEquals(4.5f, StarStyle.magnitudeLimit(500.0), 0.0f)
    }

    @Test
    fun `brighter stars are bigger`() {
        assertTrue(StarStyle.radiusDp(-1.4f, 6f) > StarStyle.radiusDp(2f, 6f))
        assertEquals(0.7f, StarStyle.radiusDp(6f, 6f), 0.0f)
    }
}

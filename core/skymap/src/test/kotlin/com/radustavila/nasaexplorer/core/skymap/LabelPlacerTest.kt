package com.radustavila.nasaexplorer.core.skymap

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LabelPlacerTest {

    private val placer = LabelPlacer(initialCapacity = 1).apply { reset(width = 400f, height = 300f) }

    @Test
    fun `a label goes to the right of its anchor first`() {
        assertTrue(placer.place(anchorX = 100f, anchorY = 100f, labelWidth = 50f, labelHeight = 10f, gap = 4f))

        assertEquals(104f, placer.left)
        assertEquals(95f, placer.top)
    }

    @Test
    fun `a label moves to a free side when the first is taken`() {
        placer.place(anchorX = 100f, anchorY = 100f, labelWidth = 50f, labelHeight = 10f, gap = 4f)

        // Same anchor: right is taken, so it goes left.
        assertTrue(placer.place(anchorX = 100f, anchorY = 100f, labelWidth = 50f, labelHeight = 10f, gap = 4f))
        assertEquals(46f, placer.left)
    }

    @Test
    fun `a label with no free side is dropped`() {
        placer.reserve(0f, 0f, 400f, 300f)

        assertFalse(placer.place(anchorX = 100f, anchorY = 100f, labelWidth = 20f, labelHeight = 10f, gap = 2f))
    }

    @Test
    fun `labels stay on the screen`() {
        // Near the right edge only the left side fits.
        assertTrue(placer.place(anchorX = 390f, anchorY = 100f, labelWidth = 50f, labelHeight = 10f, gap = 4f))
        assertEquals(336f, placer.left)

        assertFalse(placer.place(anchorX = 10f, anchorY = 2f, labelWidth = 500f, labelHeight = 10f, gap = 4f))
    }

    @Test
    fun `reset forgets earlier labels and grows past the initial capacity`() {
        repeat(10) { i -> assertTrue(placer.place(20f + i * 30f, 20f, 10f, 10f, 1f)) }
        placer.reset(400f, 300f)

        assertTrue(placer.place(anchorX = 20f, anchorY = 20f, labelWidth = 10f, labelHeight = 10f, gap = 1f))
        assertEquals(21f, placer.left)
    }
}

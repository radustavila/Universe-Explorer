package com.radustavila.nasaexplorer.core.skymap

/**
 * Greedy label placement for one frame: labels are offered most important first, each tries a few
 * spots around its anchor (right, left, above, below) and takes the first that overlaps nothing
 * placed or reserved before it; a label with no free spot is left out.
 *
 * Reused from frame to frame without allocating: call [reset], [reserve] what labels must not
 * cover (a planet's disc, the selection ring), then [place] each label.
 */
class LabelPlacer(initialCapacity: Int = DEFAULT_CAPACITY) {

    private var rects = FloatArray(initialCapacity * RECT_FLOATS)
    private var count = 0
    private var width = 0f
    private var height = 0f

    /** Top-left corner of the last label [place] accepted. */
    var left: Float = 0f
        private set
    var top: Float = 0f
        private set

    /** Forgets every rectangle; labels must stay inside a [width] by [height] screen. */
    fun reset(width: Float, height: Float) {
        count = 0
        this.width = width
        this.height = height
    }

    /** Keeps labels off a rectangle. */
    fun reserve(left: Float, top: Float, right: Float, bottom: Float) {
        add(left, top, right, bottom)
    }

    /**
     * Places a [labelWidth] by [labelHeight] label next to the point ([anchorX], [anchorY]), [gap]
     * pixels away. True, with the spot in [left] and [top], when one was free.
     */
    fun place(anchorX: Float, anchorY: Float, labelWidth: Float, labelHeight: Float, gap: Float): Boolean {
        for (side in 0 until SIDES) {
            val candidateLeft = when (side) {
                RIGHT -> anchorX + gap
                LEFT -> anchorX - gap - labelWidth
                else -> anchorX - labelWidth / 2f
            }
            val candidateTop = when (side) {
                ABOVE -> anchorY - gap - labelHeight
                BELOW -> anchorY + gap
                else -> anchorY - labelHeight / 2f
            }
            val candidateRight = candidateLeft + labelWidth
            val candidateBottom = candidateTop + labelHeight
            if (fitsOnScreen(candidateLeft, candidateTop, candidateRight, candidateBottom) &&
                !overlapsAny(candidateLeft, candidateTop, candidateRight, candidateBottom)
            ) {
                add(candidateLeft, candidateTop, candidateRight, candidateBottom)
                left = candidateLeft
                top = candidateTop
                return true
            }
        }
        return false
    }

    /** Places a label centred on ([centerX], [centerY]), or nowhere: for names of areas, like constellations. */
    fun placeCentered(centerX: Float, centerY: Float, labelWidth: Float, labelHeight: Float): Boolean {
        val candidateLeft = centerX - labelWidth / 2f
        val candidateTop = centerY - labelHeight / 2f
        val candidateRight = candidateLeft + labelWidth
        val candidateBottom = candidateTop + labelHeight
        if (!fitsOnScreen(candidateLeft, candidateTop, candidateRight, candidateBottom)) return false
        if (overlapsAny(candidateLeft, candidateTop, candidateRight, candidateBottom)) return false
        add(candidateLeft, candidateTop, candidateRight, candidateBottom)
        left = candidateLeft
        top = candidateTop
        return true
    }

    private fun fitsOnScreen(left: Float, top: Float, right: Float, bottom: Float): Boolean =
        left >= 0f && top >= 0f && right <= width && bottom <= height

    private fun overlapsAny(left: Float, top: Float, right: Float, bottom: Float): Boolean {
        for (i in 0 until count) {
            val base = i * RECT_FLOATS
            val overlapsHorizontally = left < rects[base + RIGHT_EDGE] && right > rects[base + LEFT_EDGE]
            if (overlapsHorizontally && top < rects[base + BOTTOM_EDGE] && bottom > rects[base + TOP_EDGE]) return true
        }
        return false
    }

    private fun add(left: Float, top: Float, right: Float, bottom: Float) {
        if ((count + 1) * RECT_FLOATS > rects.size) rects = rects.copyOf(rects.size * 2)
        val base = count * RECT_FLOATS
        rects[base + LEFT_EDGE] = left
        rects[base + TOP_EDGE] = top
        rects[base + RIGHT_EDGE] = right
        rects[base + BOTTOM_EDGE] = bottom
        count++
    }

    private companion object {
        const val DEFAULT_CAPACITY = 128
        const val RECT_FLOATS = 4
        const val LEFT_EDGE = 0
        const val TOP_EDGE = 1
        const val RIGHT_EDGE = 2
        const val BOTTOM_EDGE = 3
        const val SIDES = 4
        const val RIGHT = 0
        const val LEFT = 1
        const val ABOVE = 2
        const val BELOW = 3
    }
}

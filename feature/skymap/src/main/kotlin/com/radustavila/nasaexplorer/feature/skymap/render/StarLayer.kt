package com.radustavila.nasaexplorer.feature.skymap.render

import android.graphics.Paint
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import com.radustavila.nasaexplorer.core.skymap.SkyChart
import com.radustavila.nasaexplorer.core.skymap.SkyProjection
import com.radustavila.nasaexplorer.core.skymap.StarStyle
import kotlin.math.roundToInt

/**
 * The stars: projected into reused arrays, grouped by dot size and colour, and drawn with one
 * `drawPoints` call per group, a few dozen calls for thousands of stars. Nothing is allocated per
 * star or per frame.
 */
internal class StarLayer {

    private var chart: SkyChart? = null
    private var colourBuckets = IntArray(0)
    private var screenX = FloatArray(0)
    private var screenY = FloatArray(0)
    private var buckets = IntArray(0)
    private var points = FloatArray(0)
    private val counts = IntArray(BUCKETS)
    private val offsets = IntArray(BUCKETS)

    /** This frame's faintest magnitude and off-screen margin. */
    private var limit = 0f
    private var margin = 0f

    private var paintsFor: Pair<SkyPalette, Float>? = null
    private var paints = emptyArray<Paint>()

    fun draw(scope: DrawScope, frame: SkyFrame, projection: SkyProjection) = with(scope) {
        prepare(frame)
        limit = StarStyle.magnitudeLimit(frame.fieldOfViewDegrees)
        margin = MAX_RADIUS_DP.dp.toPx()
        counts.fill(0)
        var visible = 0
        // Brightest first, so the stars to draw are a prefix of the catalogue.
        for (i in 0 until frame.sky.chart.starsBrighterThan(limit)) {
            if (place(frame, projection, i, visible)) visible++
        }
        var offset = 0
        for (bucket in 0 until BUCKETS) {
            offsets[bucket] = offset
            offset += counts[bucket]
        }
        for (i in 0 until visible) {
            val slot = offsets[buckets[i]]++
            points[slot * 2] = screenX[i]
            points[slot * 2 + 1] = screenY[i]
        }
        drawIntoCanvas { canvas ->
            var start = 0
            for (bucket in 0 until BUCKETS) {
                val count = counts[bucket]
                if (count > 0) canvas.nativeCanvas.drawPoints(points, start * 2, count * 2, paints[bucket])
                start += count
            }
        }
    }

    /** Projects star [index] into slot [slot]; false when it is off the screen. */
    private fun DrawScope.place(frame: SkyFrame, projection: SkyProjection, index: Int, slot: Int): Boolean {
        val isOnScreen = projection.project(frame.sky.stars, index * SkyChart.VECTOR_FLOATS) &&
            projection.x in -margin..size.width + margin && projection.y in -margin..size.height + margin
        if (!isOnScreen) return false
        val x = projection.x
        val y = projection.y
        val radius = StarStyle.radiusDp(frame.sky.chart.starMagnitudes[index], limit)
        val sizeBucket = ((radius - MIN_RADIUS_DP) / RADIUS_STEP_DP).roundToInt().coerceIn(0, SIZE_BUCKETS - 1)
        val bucket = sizeBucket * StarStyle.COLOUR_BUCKETS + colourBuckets[index]
        screenX[slot] = x
        screenY[slot] = y
        buckets[slot] = bucket
        counts[bucket]++
        return true
    }

    /** Rebuilds what depends on the catalogue or the palette, only when they change. */
    private fun DrawScope.prepare(frame: SkyFrame) {
        val chart = frame.sky.chart
        if (this@StarLayer.chart !== chart) {
            colourBuckets = IntArray(chart.starCount) { StarStyle.colourBucket(chart.starColourIndices[it]) }
            screenX = FloatArray(chart.starCount)
            screenY = FloatArray(chart.starCount)
            buckets = IntArray(chart.starCount)
            points = FloatArray(chart.starCount * 2)
            this@StarLayer.chart = chart
        }
        val key = frame.palette to density
        if (paintsFor == key) return
        paints = Array(BUCKETS) { bucket ->
            val sizeBucket = bucket / StarStyle.COLOUR_BUCKETS
            val radius = (MIN_RADIUS_DP + sizeBucket * RADIUS_STEP_DP).dp.toPx()
            val alpha = MIN_STAR_ALPHA + (1f - MIN_STAR_ALPHA) * sizeBucket / (SIZE_BUCKETS - 1)
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
                strokeWidth = radius * 2f
                color = frame.palette.star(bucket % StarStyle.COLOUR_BUCKETS).copy(alpha = alpha).toArgb()
            }
        }
        paintsFor = key
    }

    private companion object {
        const val MIN_RADIUS_DP = 0.7f
        const val MAX_RADIUS_DP = 4.2f
        const val RADIUS_STEP_DP = 0.25f
        val SIZE_BUCKETS = ((MAX_RADIUS_DP - MIN_RADIUS_DP) / RADIUS_STEP_DP).roundToInt() + 1
        val BUCKETS = SIZE_BUCKETS * StarStyle.COLOUR_BUCKETS
        const val MIN_STAR_ALPHA = 0.5f
    }
}

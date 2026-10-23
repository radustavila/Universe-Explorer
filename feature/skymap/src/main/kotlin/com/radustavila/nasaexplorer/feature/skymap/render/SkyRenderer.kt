package com.radustavila.nasaexplorer.feature.skymap.render

import android.graphics.Paint
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.unit.dp
import com.radustavila.nasaexplorer.core.model.SolarSystemBody
import com.radustavila.nasaexplorer.core.skymap.HorizonShape
import com.radustavila.nasaexplorer.core.skymap.SkyChart
import com.radustavila.nasaexplorer.core.skymap.SkyProjection
import com.radustavila.nasaexplorer.feature.skymap.SkyTarget

/**
 * Draws [SkyFrame]s on a Compose canvas at frame rate, back to front: sky colour, constellation
 * figures, stars ([StarLayer]), the Sun, Moon, planets and ISS ([SkyBodyPainter]), the ground, then
 * names ([LabelLayer]) and the selection on top.
 */
internal class SkyRenderer(textMeasurer: TextMeasurer, styles: SkyTextStyles) {

    private val stars = StarLayer()
    private val labels = LabelLayer(textMeasurer, styles)

    private var linesFor: SkyChart? = null
    private var linePoints = FloatArray(0)
    private var highlightPoints = FloatArray(0)
    private var paintsFor: Pair<SkyPalette, Float>? = null
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

    fun draw(scope: DrawScope, frame: SkyFrame) = with(scope) {
        val projection = SkyProjection(frame.view, size.width, size.height, frame.fieldOfViewDegrees, frame.lens)
        val sun = frame.bodies?.solarSystem?.firstOrNull { it.body == SolarSystemBody.SUN }
        drawRect(frame.palette.background(sun?.horizontal?.altitudeDegrees))
        drawFigures(frame, projection)
        stars.draw(this, frame, projection)
        val bodies = SkyBodyPainter(this, frame, projection)
        bodies.drawBodies()
        drawGround(frame.palette, projection)
        labels.drawCardinals(this, frame, projection)
        bodies.reserveDiscs(labels)
        labels.drawLabels(this, frame, projection)
        bodies.drawSelection()
    }

    private fun DrawScope.drawFigures(frame: SkyFrame, projection: SkyProjection) {
        prepareLines(frame)
        val chart = frame.sky.chart
        val constellations = chart.catalog.constellations
        // Both halves of Serpens light up together: figures are matched by abbreviation.
        val highlighted = (frame.selected as? SkyTarget.Constellation)?.let { constellations[it.index].id }
        var lineFloats = 0
        var highlightFloats = 0
        for (segment in 0 until chart.segmentCount) {
            val isHighlighted = highlighted != null && constellations[chart.segmentFigures[segment]].id == highlighted
            val written = if (isHighlighted) {
                projectSegment(frame, projection, segment, highlightPoints, highlightFloats)
            } else {
                projectSegment(frame, projection, segment, linePoints, lineFloats)
            }
            if (isHighlighted) highlightFloats += written else lineFloats += written
        }
        drawIntoCanvas { canvas ->
            if (lineFloats > 0) canvas.nativeCanvas.drawLines(linePoints, 0, lineFloats, linePaint)
            if (highlightFloats > 0) canvas.nativeCanvas.drawLines(highlightPoints, 0, highlightFloats, highlightPaint)
        }
    }

    /** Writes figure [segment]'s two screen points into [out] at [at]; the number of floats written. */
    private fun projectSegment(
        frame: SkyFrame,
        projection: SkyProjection,
        segment: Int,
        out: FloatArray,
        at: Int,
    ): Int {
        val base = segment * SEGMENT_FLOATS
        if (!projection.project(frame.sky.segments, base)) return 0
        val startX = projection.x
        val startY = projection.y
        val isVisible = projection.project(frame.sky.segments, base + SkyChart.VECTOR_FLOATS)
        if (isVisible) {
            out[at] = startX
            out[at + 1] = startY
            out[at + 2] = projection.x
            out[at + LINE_FLOATS - 1] = projection.y
        }
        return if (isVisible) LINE_FLOATS else 0
    }

    private fun DrawScope.prepareLines(frame: SkyFrame) {
        val chart = frame.sky.chart
        if (linesFor !== chart) {
            linePoints = FloatArray(chart.segmentCount * LINE_FLOATS)
            highlightPoints = FloatArray(chart.segmentCount * LINE_FLOATS)
            linesFor = chart
        }
        val key = frame.palette to density
        if (paintsFor == key) return
        linePaint.strokeWidth = LINE_WIDTH_DP.dp.toPx()
        linePaint.color = frame.palette.constellationLine.toArgb()
        highlightPaint.strokeWidth = HIGHLIGHT_WIDTH_DP.dp.toPx()
        highlightPaint.color = frame.palette.highlightedLine.toArgb()
        paintsFor = key
    }

    private fun DrawScope.drawGround(palette: SkyPalette, projection: SkyProjection) {
        val stroke = Stroke(width = HORIZON_WIDTH_DP.dp.toPx())
        when (val horizon = projection.horizon()) {
            is HorizonShape.Circle -> {
                val center = Offset(horizon.centerX, horizon.centerY)
                val ground = Path().apply {
                    addOval(Rect(center, horizon.radius))
                    if (!horizon.isGroundInside) {
                        addRect(Rect(0f, 0f, size.width, size.height))
                        fillType = PathFillType.EvenOdd
                    }
                }
                drawPath(ground, palette.ground)
                drawCircle(palette.horizon, horizon.radius, center, style = stroke)
            }
            is HorizonShape.Line -> {
                // Far along the line both ways, then across to the ground's side; far enough to cover
                // the screen when the line lies beyond it, as a camera's horizon does looking down.
                val point = Offset(horizon.pointX, horizon.pointY)
                val reach = size.width + size.height + (point - center).getDistance()
                val along = Offset(-horizon.normalY, horizon.normalX) * reach
                val down = Offset(-horizon.normalX, -horizon.normalY) * reach
                val start = point - along
                val end = point + along
                val ground = Path().apply {
                    moveTo(start.x, start.y)
                    lineTo(end.x, end.y)
                    lineTo(end.x + down.x, end.y + down.y)
                    lineTo(start.x + down.x, start.y + down.y)
                    close()
                }
                drawPath(ground, palette.ground)
                drawLine(palette.horizon, start, end, strokeWidth = stroke.width)
            }
        }
    }

    private companion object {
        /** Two points per figure segment, x, y, z each. */
        const val SEGMENT_FLOATS = 2 * SkyChart.VECTOR_FLOATS

        /** Two screen points per line: x, y each. */
        const val LINE_FLOATS = 4
        const val LINE_WIDTH_DP = 1f
        const val HIGHLIGHT_WIDTH_DP = 1.6f
        const val HORIZON_WIDTH_DP = 1.5f
    }
}

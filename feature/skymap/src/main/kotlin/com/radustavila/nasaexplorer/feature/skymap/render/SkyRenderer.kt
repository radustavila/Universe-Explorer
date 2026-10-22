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
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.dp
import com.radustavila.nasaexplorer.core.model.SolarSystemBody
import com.radustavila.nasaexplorer.core.skymap.HorizonShape
import com.radustavila.nasaexplorer.core.skymap.LabelPlacer
import com.radustavila.nasaexplorer.core.skymap.SkyChart
import com.radustavila.nasaexplorer.core.skymap.SkyProjection
import com.radustavila.nasaexplorer.core.skymap.SkyVector
import com.radustavila.nasaexplorer.core.skymap.StarStyle
import com.radustavila.nasaexplorer.feature.skymap.SkyTarget
import kotlin.math.roundToInt

/** Text styles of the map's labels. */
internal data class SkyTextStyles(
    val star: TextStyle,
    val constellation: TextStyle,
    val body: TextStyle,
    val cardinal: TextStyle,
)

/**
 * Draws [SkyFrame]s on a Compose canvas at frame rate. Stars are projected into reused arrays and
 * drawn in one `drawPoints` call per size and colour, a few dozen calls for thousands of stars;
 * label layouts are measured once and cached. Nothing is allocated per star.
 */
internal class SkyRenderer(
    private val textMeasurer: TextMeasurer,
    private val styles: SkyTextStyles,
) {
    private val placer = LabelPlacer()
    private val layouts = HashMap<TextStyle, HashMap<String, TextLayoutResult>>()

    private var starX = FloatArray(0)
    private var starY = FloatArray(0)
    private var starBucket = IntArray(0)
    private var starPoints = FloatArray(0)
    private val bucketCounts = IntArray(BUCKETS)
    private val bucketOffsets = IntArray(BUCKETS)
    private var segmentPoints = FloatArray(0)
    private var highlightPoints = FloatArray(0)

    private var paintsFor: Pair<SkyPalette, Float>? = null
    private var starPaints = emptyArray<Paint>()
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

    private var colourBucketsFor: SkyChart? = null
    private var starColourBuckets = IntArray(0)
    private var namedStars = IntArray(0)

    fun draw(scope: DrawScope, frame: SkyFrame) = with(scope) {
        val projection = SkyProjection(frame.view, size.width, size.height, frame.fieldOfViewDegrees)
        prepare(frame)
        val sun = frame.bodies?.solarSystem?.firstOrNull { it.body == SolarSystemBody.SUN }
        drawRect(frame.palette.background(sun?.horizontal?.altitudeDegrees))
        drawFigures(frame, projection)
        drawStars(frame, projection)
        val bodyParts = SkyBodyPainter(this, frame, projection)
        bodyParts.drawBodies()
        drawGround(frame.palette, projection)
        placer.reset(size.width, size.height)
        drawCardinals(frame, projection)
        bodyParts.reserveDiscs(placer)
        drawLabels(frame, projection)
        bodyParts.drawSelection()
    }

    /** Rebuilds what depends on the catalogue or the palette, only when they change. */
    private fun DrawScope.prepare(frame: SkyFrame) {
        val chart = frame.sky.chart
        if (colourBucketsFor !== chart) {
            starColourBuckets = IntArray(chart.starCount) { StarStyle.colourBucket(chart.starColourIndices[it]) }
            namedStars = chart.catalog.stars.indices.filter { chart.catalog.stars[it].name != null }.toIntArray()
            starX = FloatArray(chart.starCount)
            starY = FloatArray(chart.starCount)
            starBucket = IntArray(chart.starCount)
            starPoints = FloatArray(chart.starCount * 2)
            segmentPoints = FloatArray(chart.segmentCount * 4)
            highlightPoints = FloatArray(chart.segmentCount * 4)
            colourBucketsFor = chart
        }
        val key = frame.palette to density
        if (paintsFor != key) {
            starPaints = Array(BUCKETS) { bucket ->
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
            linePaint.strokeWidth = LINE_WIDTH_DP.dp.toPx()
            linePaint.color = frame.palette.constellationLine.toArgb()
            highlightPaint.strokeWidth = HIGHLIGHT_WIDTH_DP.dp.toPx()
            highlightPaint.color = frame.palette.highlightedLine.toArgb()
            paintsFor = key
        }
    }

    private fun DrawScope.drawFigures(frame: SkyFrame, projection: SkyProjection) {
        val segments = frame.sky.segments
        val figures = frame.sky.chart.segmentFigures
        val highlighted = (frame.selected as? SkyTarget.Constellation)?.let { frame.sky.chart.catalog.constellations[it.index].id }
        val constellations = frame.sky.chart.catalog.constellations
        var lineFloats = 0
        var highlightFloats = 0
        for (segment in figures.indices) {
            val base = segment * 6
            if (!projection.project(segments[base].toDouble(), segments[base + 1].toDouble(), segments[base + 2].toDouble())) continue
            val x1 = projection.x
            val y1 = projection.y
            if (!projection.project(segments[base + 3].toDouble(), segments[base + 4].toDouble(), segments[base + 5].toDouble())) continue
            val isHighlighted = highlighted != null && constellations[figures[segment]].id == highlighted
            val target = if (isHighlighted) highlightPoints else segmentPoints
            val at = if (isHighlighted) highlightFloats else lineFloats
            target[at] = x1
            target[at + 1] = y1
            target[at + 2] = projection.x
            target[at + 3] = projection.y
            if (isHighlighted) highlightFloats += 4 else lineFloats += 4
        }
        drawIntoCanvas { canvas ->
            if (lineFloats > 0) canvas.nativeCanvas.drawLines(segmentPoints, 0, lineFloats, linePaint)
            if (highlightFloats > 0) canvas.nativeCanvas.drawLines(highlightPoints, 0, highlightFloats, highlightPaint)
        }
    }

    private fun DrawScope.drawStars(frame: SkyFrame, projection: SkyProjection) {
        val stars = frame.sky.stars
        val magnitudes = frame.sky.chart.starMagnitudes
        val limit = StarStyle.magnitudeLimit(frame.fieldOfViewDegrees)
        val margin = MAX_RADIUS_DP.dp.toPx()
        bucketCounts.fill(0)
        var visible = 0
        for (i in 0 until frame.sky.chart.starCount) {
            val magnitude = magnitudes[i]
            // Brightest first: everything after the first too-faint star is fainter still.
            if (magnitude > limit) break
            if (!projection.project(stars[i * 3].toDouble(), stars[i * 3 + 1].toDouble(), stars[i * 3 + 2].toDouble())) continue
            val x = projection.x
            val y = projection.y
            if (x < -margin || y < -margin || x > size.width + margin || y > size.height + margin) continue
            val radius = StarStyle.radiusDp(magnitude, limit)
            val sizeBucket = ((radius - MIN_RADIUS_DP) / RADIUS_STEP_DP).roundToInt().coerceIn(0, SIZE_BUCKETS - 1)
            val bucket = sizeBucket * StarStyle.COLOUR_BUCKETS + starColourBuckets[i]
            starX[visible] = x
            starY[visible] = y
            starBucket[visible] = bucket
            bucketCounts[bucket]++
            visible++
        }
        var offset = 0
        for (bucket in 0 until BUCKETS) {
            bucketOffsets[bucket] = offset
            offset += bucketCounts[bucket]
        }
        for (i in 0 until visible) {
            val slot = bucketOffsets[starBucket[i]]++
            starPoints[slot * 2] = starX[i]
            starPoints[slot * 2 + 1] = starY[i]
        }
        drawIntoCanvas { canvas ->
            var start = 0
            for (bucket in 0 until BUCKETS) {
                val count = bucketCounts[bucket]
                if (count > 0) canvas.nativeCanvas.drawPoints(starPoints, start * 2, count * 2, starPaints[bucket])
                start += count
            }
        }
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
                // Far corners on both sides of the line, then the half on the ground's side.
                val reach = size.width + size.height
                val alongX = -horizon.normalY
                val alongY = horizon.normalX
                val start = Offset(horizon.pointX - alongX * reach, horizon.pointY - alongY * reach)
                val end = Offset(horizon.pointX + alongX * reach, horizon.pointY + alongY * reach)
                val ground = Path().apply {
                    moveTo(start.x, start.y)
                    lineTo(end.x, end.y)
                    lineTo(end.x - horizon.normalX * reach, end.y - horizon.normalY * reach)
                    lineTo(start.x - horizon.normalX * reach, start.y - horizon.normalY * reach)
                    close()
                }
                drawPath(ground, palette.ground)
                drawLine(palette.horizon, start, end, strokeWidth = stroke.width)
            }
        }
    }

    private fun DrawScope.drawCardinals(frame: SkyFrame, projection: SkyProjection) {
        val gap = LABEL_GAP_DP.dp.toPx()
        frame.names.cardinals.forEachIndexed { i, name ->
            val direction = SkyVector.horizontal(0.0, i * CARDINAL_STEP)
            if (!projection.project(direction)) return@forEachIndexed
            val layout = layout(name, styles.cardinal)
            val left = projection.x - layout.size.width / 2f
            val top = projection.y - layout.size.height - gap
            placer.reserve(left, top, left + layout.size.width, top + layout.size.height)
            val colour = if (i == 0) frame.palette.north else frame.palette.cardinal
            drawText(layout, color = colour, topLeft = Offset(left, top))
        }
    }

    private fun DrawScope.drawLabels(frame: SkyFrame, projection: SkyProjection) {
        val gap = LABEL_GAP_DP.dp.toPx()
        frame.selected?.let { selected -> selectedLabel(frame, selected)?.let { label(frame, it, selected, projection, gap) } }
        frame.bodies?.solarSystem?.sortedBy { it.magnitude }?.forEach { position ->
            val name = frame.names.bodies[position.body] ?: return@forEach
            label(frame, name, SkyTarget.Body(position.body), projection, gap)
        }
        frame.bodies?.satellites?.forEach { label(frame, frame.names.iss, SkyTarget.Satellite(it.noradId), projection, gap) }
        val starLimit = starLabelLimit(frame.fieldOfViewDegrees)
        for (index in namedStars) {
            if (frame.sky.chart.starMagnitudes[index] > starLimit) break
            val name = frame.sky.chart.catalog.stars[index].name ?: continue
            label(frame, name, SkyTarget.StarTarget(index), projection, gap)
        }
        val rankLimit = constellationRankLimit(frame.fieldOfViewDegrees)
        frame.sky.chart.catalog.constellations.forEachIndexed { index, figure ->
            if (figure.rank <= rankLimit) label(frame, figure.name, SkyTarget.Constellation(index), projection, gap)
        }
    }

    private fun selectedLabel(frame: SkyFrame, selected: SkyTarget): String? = when (selected) {
        is SkyTarget.StarTarget -> frame.sky.chart.catalog.stars.getOrNull(selected.index)?.name
        is SkyTarget.Constellation -> frame.sky.chart.catalog.constellations.getOrNull(selected.index)?.name
        is SkyTarget.Body -> frame.names.bodies[selected.body]
        is SkyTarget.Satellite -> frame.names.iss
    }

    private fun DrawScope.label(frame: SkyFrame, text: String, target: SkyTarget, projection: SkyProjection, gap: Float) {
        val direction = target.direction(frame.sky, frame.bodies) ?: return
        val isBody = target is SkyTarget.Body || target is SkyTarget.Satellite
        // Only what the Sun, Moon and planets do is worth labelling below the ground.
        if (!isBody && direction.z < BELOW_HORIZON) return
        if (!projection.project(direction)) return
        val style = when (target) {
            is SkyTarget.StarTarget -> styles.star
            is SkyTarget.Constellation -> styles.constellation
            else -> styles.body
        }
        val layout = layout(text, style)
        val width = layout.size.width.toFloat()
        val height = layout.size.height.toFloat()
        val placed = if (target is SkyTarget.Constellation) {
            // A constellation's name sits on its label point, in the middle of the figure.
            placer.placeCentered(projection.x, projection.y, width, height)
        } else {
            placer.place(projection.x, projection.y, width, height, gap + bodyRadius(frame, target, projection))
        }
        if (!placed) return
        val colour = when (target) {
            is SkyTarget.StarTarget -> frame.palette.starLabel
            is SkyTarget.Constellation -> frame.palette.constellationLabel
            is SkyTarget.Body -> frame.palette.bodyLabel
            is SkyTarget.Satellite -> frame.palette.iss
        }
        val alpha = if (direction.z < BELOW_HORIZON) BELOW_HORIZON_ALPHA else 1f
        drawText(layout, color = colour, topLeft = Offset(placer.left, placer.top), alpha = alpha)
    }

    private fun DrawScope.bodyRadius(frame: SkyFrame, target: SkyTarget, projection: SkyProjection): Float {
        val body = (target as? SkyTarget.Body)?.body ?: return 0f
        val position = frame.bodies?.solarSystem?.firstOrNull { it.body == body } ?: return 0f
        return SkyBodyPainter.discRadius(this, position, projection)
    }

    private fun layout(text: String, style: TextStyle): TextLayoutResult =
        layouts.getOrPut(style) { HashMap() }.getOrPut(text) { textMeasurer.measure(text, style) }

    private fun starLabelLimit(fieldOfView: Double): Float = when {
        fieldOfView > WIDE_VIEW -> 0.6f
        fieldOfView > MEDIUM_VIEW -> 1.6f
        fieldOfView > NARROW_VIEW -> 2.6f
        else -> 4.5f
    }

    private fun constellationRankLimit(fieldOfView: Double): Int = when {
        fieldOfView > WIDE_VIEW -> 1
        fieldOfView > MEDIUM_VIEW -> 2
        else -> 3
    }

    internal companion object {
        const val MIN_RADIUS_DP = 0.7f
        const val MAX_RADIUS_DP = 4.2f
        const val RADIUS_STEP_DP = 0.25f
        val SIZE_BUCKETS = ((MAX_RADIUS_DP - MIN_RADIUS_DP) / RADIUS_STEP_DP).roundToInt() + 1
        val BUCKETS = SIZE_BUCKETS * StarStyle.COLOUR_BUCKETS
        const val MIN_STAR_ALPHA = 0.5f
        const val LINE_WIDTH_DP = 1f
        const val HIGHLIGHT_WIDTH_DP = 1.6f
        const val HORIZON_WIDTH_DP = 1.5f
        const val LABEL_GAP_DP = 4f
        const val CARDINAL_STEP = 45.0
        const val WIDE_VIEW = 110.0
        const val MEDIUM_VIEW = 70.0
        const val NARROW_VIEW = 35.0

        /** About one degree below the horizon. */
        const val BELOW_HORIZON = -0.017
        const val BELOW_HORIZON_ALPHA = 0.5f
    }
}

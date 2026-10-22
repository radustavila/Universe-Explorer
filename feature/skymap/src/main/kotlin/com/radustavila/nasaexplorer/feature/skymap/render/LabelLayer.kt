package com.radustavila.nasaexplorer.feature.skymap.render

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.dp
import com.radustavila.nasaexplorer.core.skymap.LabelPlacer
import com.radustavila.nasaexplorer.core.skymap.SkyProjection
import com.radustavila.nasaexplorer.core.skymap.SkyVector
import com.radustavila.nasaexplorer.core.skymap.StarStyle
import com.radustavila.nasaexplorer.feature.skymap.SkyTarget

/** Text styles of the map's labels. */
internal data class SkyTextStyles(
    val star: TextStyle,
    val constellation: TextStyle,
    val body: TextStyle,
    val cardinal: TextStyle,
)

/**
 * Names on the map, most important first so they win the space: the selection, the Sun, Moon and
 * planets, the ISS, bright named stars, then constellations. How many stars and constellations get a
 * name follows the zoom. Text is measured once per name and style and cached.
 */
internal class LabelLayer(
    private val textMeasurer: TextMeasurer,
    private val styles: SkyTextStyles,
) {
    private val placer = LabelPlacer()
    private val layouts = HashMap<TextStyle, HashMap<String, TextLayoutResult>>()
    private var namedStarsFor: List<*>? = null
    private var namedStars = IntArray(0)

    /** Cardinal points on the horizon; call first, as other labels keep off them. */
    fun drawCardinals(scope: DrawScope, frame: SkyFrame, projection: SkyProjection) = with(scope) {
        placer.reset(size.width, size.height)
        val gap = LABEL_GAP_DP.dp.toPx()
        frame.names.cardinals.forEachIndexed { i, name ->
            if (!projection.project(SkyVector.horizontal(0.0, i * CARDINAL_STEP))) return@forEachIndexed
            val layout = layout(name, styles.cardinal)
            val left = projection.x - layout.size.width / 2f
            val top = projection.y - layout.size.height - gap
            placer.reserve(left, top, left + layout.size.width, top + layout.size.height)
            val colour = if (i == 0) frame.palette.north else frame.palette.cardinal
            drawText(layout, color = colour, topLeft = Offset(left, top))
        }
    }

    /** Keeps labels off a disc, like the Moon's. */
    fun reserve(center: Offset, radius: Float) {
        placer.reserve(center.x - radius, center.y - radius, center.x + radius, center.y + radius)
    }

    fun drawLabels(scope: DrawScope, frame: SkyFrame, projection: SkyProjection) {
        val catalog = frame.sky.chart.catalog
        if (namedStarsFor !== catalog.stars) {
            namedStars = catalog.stars.indices.filter { catalog.stars[it].name != null }.toIntArray()
            namedStarsFor = catalog.stars
        }
        val selected = frame.selected
        selected?.let { scope.label(frame, projection, it) }
        frame.bodies?.solarSystem?.sortedBy { it.magnitude }?.forEach { position ->
            SkyTarget.Body(position.body).takeIf { it != selected }?.let { scope.label(frame, projection, it) }
        }
        frame.bodies?.satellites?.forEach { satellite ->
            SkyTarget.Satellite(satellite.noradId).takeIf { it != selected }?.let { scope.label(frame, projection, it) }
        }
        val starLimit = starLabelLimit(frame.fieldOfViewDegrees)
        namedStars.asSequence()
            .takeWhile { frame.sky.chart.starMagnitudes[it] <= starLimit }
            .map(SkyTarget::StarTarget)
            .filter { it != selected }
            .forEach { scope.label(frame, projection, it) }
        val rankLimit = constellationRankLimit(frame.fieldOfViewDegrees)
        catalog.constellations.forEachIndexed { index, figure ->
            val target = SkyTarget.Constellation(index)
            if (figure.rank <= rankLimit && target != selected) scope.label(frame, projection, target)
        }
    }

    private fun DrawScope.label(frame: SkyFrame, projection: SkyProjection, target: SkyTarget) {
        val text = frame.nameOf(target)
        val direction = target.direction(frame.sky, frame.bodies)
            ?.takeIf { isNamedThere(target, it) && projection.project(it) }
        if (text == null || direction == null) return
        val layout = layout(text, styleOf(target))
        val width = layout.size.width.toFloat()
        val height = layout.size.height.toFloat()
        val placed = if (target is SkyTarget.Constellation) {
            // A constellation's name sits on its label point, in the middle of the figure.
            placer.placeCentered(projection.x, projection.y, width, height)
        } else {
            val gap = LABEL_GAP_DP.dp.toPx() + markerRadius(frame, target, projection)
            placer.place(projection.x, projection.y, width, height, gap)
        }
        if (placed) {
            val alpha = if (direction.z < BELOW_HORIZON) BELOW_HORIZON_ALPHA else 1f
            val topLeft = Offset(placer.left, placer.top)
            drawText(layout, color = colourOf(frame.palette, target), topLeft = topLeft, alpha = alpha)
        }
    }

    /** Only the Sun, Moon, planets and the ISS are worth naming below the ground. */
    private fun isNamedThere(target: SkyTarget, direction: SkyVector): Boolean =
        direction.z >= BELOW_HORIZON || target is SkyTarget.Body || target is SkyTarget.Satellite

    private fun SkyFrame.nameOf(target: SkyTarget): String? = when (target) {
        is SkyTarget.StarTarget -> sky.chart.catalog.stars.getOrNull(target.index)?.name
        is SkyTarget.Constellation -> sky.chart.catalog.constellations.getOrNull(target.index)?.name
        is SkyTarget.Body -> names.bodies[target.body]
        is SkyTarget.Satellite -> names.satellite(target.noradId, bodies)
    }

    private fun styleOf(target: SkyTarget): TextStyle = when (target) {
        is SkyTarget.StarTarget -> styles.star
        is SkyTarget.Constellation -> styles.constellation
        is SkyTarget.Body, is SkyTarget.Satellite -> styles.body
    }

    private fun colourOf(palette: SkyPalette, target: SkyTarget): Color = when (target) {
        is SkyTarget.StarTarget -> palette.starLabel
        is SkyTarget.Constellation -> palette.constellationLabel
        is SkyTarget.Body -> palette.bodyLabel
        is SkyTarget.Satellite -> palette.iss
    }

    /** Radius of the dot or disc a label sits next to, so the label clears it. */
    private fun DrawScope.markerRadius(frame: SkyFrame, target: SkyTarget, projection: SkyProjection): Float =
        when (target) {
            is SkyTarget.StarTarget -> StarStyle.radiusDp(
                frame.sky.chart.starMagnitudes[target.index],
                StarStyle.magnitudeLimit(frame.fieldOfViewDegrees),
            ).dp.toPx()
            is SkyTarget.Body -> frame.bodies?.solarSystem?.firstOrNull { it.body == target.body }
                ?.let { SkyBodyPainter.discRadius(this, it, projection) } ?: 0f
            is SkyTarget.Satellite -> SkyBodyPainter.satelliteRadius(this)
            is SkyTarget.Constellation -> 0f
        }

    private fun layout(text: String, style: TextStyle): TextLayoutResult =
        layouts.getOrPut(style) { HashMap() }.getOrPut(text) { textMeasurer.measure(text, style) }

    private companion object {
        const val LABEL_GAP_DP = 4f
        const val CARDINAL_STEP = 45.0
        const val WIDE_VIEW = 110.0
        const val MEDIUM_VIEW = 70.0
        const val NARROW_VIEW = 35.0

        /** Faintest named star labelled at each zoom: a handful wide, most of them zoomed in. */
        const val WIDE_STAR_LIMIT = 0.6f
        const val MEDIUM_STAR_LIMIT = 1.6f
        const val NARROW_STAR_LIMIT = 2.6f
        const val ZOOMED_STAR_LIMIT = 4.5f

        /** d3-celestial ranks figures 1 (most prominent) to 3. */
        const val MAJOR_CONSTELLATIONS = 1
        const val ALL_CONSTELLATIONS = 3

        /** About one degree below the horizon. */
        const val BELOW_HORIZON = -0.017
        const val BELOW_HORIZON_ALPHA = 0.5f

        fun starLabelLimit(fieldOfView: Double): Float = when {
            fieldOfView > WIDE_VIEW -> WIDE_STAR_LIMIT
            fieldOfView > MEDIUM_VIEW -> MEDIUM_STAR_LIMIT
            fieldOfView > NARROW_VIEW -> NARROW_STAR_LIMIT
            else -> ZOOMED_STAR_LIMIT
        }

        fun constellationRankLimit(fieldOfView: Double): Int = when {
            fieldOfView > WIDE_VIEW -> MAJOR_CONSTELLATIONS
            fieldOfView > MEDIUM_VIEW -> MAJOR_CONSTELLATIONS + 1
            else -> ALL_CONSTELLATIONS
        }
    }
}

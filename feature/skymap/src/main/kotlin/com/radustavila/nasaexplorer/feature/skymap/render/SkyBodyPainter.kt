package com.radustavila.nasaexplorer.feature.skymap.render

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotateRad
import androidx.compose.ui.unit.dp
import com.radustavila.nasaexplorer.core.model.SolarSystemBody
import com.radustavila.nasaexplorer.core.model.SolarSystemPosition
import com.radustavila.nasaexplorer.core.skymap.SkyChart
import com.radustavila.nasaexplorer.core.skymap.SkyProjection
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.tan

/** The Sun, the Moon with its phase, the planets, the ISS and the selection ring or arrow. */
internal class SkyBodyPainter(
    private val scope: DrawScope,
    private val frame: SkyFrame,
    private val projection: SkyProjection,
) {
    private val discs = mutableListOf<Pair<Offset, Float>>()

    fun drawBodies() = with(scope) {
        val positions = frame.bodies?.solarSystem.orEmpty()
        // Faint first, so the Sun and the Moon end up on top.
        positions.sortedByDescending { it.magnitude }.forEach { position ->
            if (!projection.project(position.direction())) return@forEach
            val center = Offset(projection.x, projection.y)
            val radius = discRadius(this, position, projection)
            when (position.body) {
                SolarSystemBody.SUN -> drawCircle(frame.palette.sun, radius, center)
                SolarSystemBody.MOON -> drawMoon(center, radius, position)
                else -> drawCircle(frame.palette.planet(position.body), radius, center)
            }
            discs += center to radius
        }
        frame.bodies?.satellites?.forEach { satellite ->
            if (!projection.project(SkyChart.apparent(satellite.horizontal))) return@forEach
            val center = Offset(projection.x, projection.y)
            val ring = satelliteRadius(this)
            drawCircle(frame.palette.iss, ISS_RADIUS_DP.dp.toPx(), center)
            drawCircle(frame.palette.iss, ring, center, style = Stroke(width = 1.dp.toPx()), alpha = ISS_RING_ALPHA)
            discs += center to ring
        }
    }

    fun reserveDiscs(labels: LabelLayer) {
        discs.forEach { (center, radius) -> labels.reserve(center, radius) }
    }

    /** A ring around the selection, or an arrow at the edge pointing the way to it. */
    fun drawSelection() = with(scope) {
        val selected = frame.selected ?: return
        val direction = selected.direction(frame.sky, frame.bodies) ?: return
        val inset = ARROW_INSET_DP.dp.toPx()
        val onScreen = projection.project(direction) &&
            projection.x in 0f..size.width && projection.y in 0f..size.height
        if (onScreen) {
            val center = Offset(projection.x, projection.y)
            val ring = Stroke(width = SELECTION_STROKE_DP.dp.toPx())
            drawCircle(frame.palette.selection, SELECTION_RADIUS_DP.dp.toPx(), center, style = ring)
            return
        }
        // Straight behind has no screen direction; point down, the way most people turn first.
        val (x, y) = projection.projectUnbounded(direction) ?: (projection.centerX to size.height * 2f)
        val dx = x - projection.centerX
        val dy = y - projection.centerY
        val length = hypot(dx, dy).takeIf { it > 0f } ?: return
        val reach = min(size.width, size.height) / 2f - inset
        val tip = Offset(projection.centerX + dx / length * reach, projection.centerY + dy / length * reach)
        rotateRad(atan2(dy, dx), pivot = tip) {
            val arrowLength = ARROW_LENGTH_DP.dp.toPx()
            val arrowHalfWidth = ARROW_HALF_WIDTH_DP.dp.toPx()
            val arrow = Path().apply {
                moveTo(tip.x, tip.y)
                lineTo(tip.x - arrowLength, tip.y - arrowHalfWidth)
                lineTo(tip.x - arrowLength * ARROW_NOTCH, tip.y)
                lineTo(tip.x - arrowLength, tip.y + arrowHalfWidth)
                close()
            }
            drawPath(arrow, frame.palette.selection)
        }
    }

    /** The lit part towards the Sun: a half disc and an elliptical terminator. */
    private fun DrawScope.drawMoon(center: Offset, radius: Float, moon: SolarSystemPosition) {
        drawCircle(frame.palette.moonDark, radius, center)
        val sun = frame.bodies?.solarSystem?.firstOrNull { it.body == SolarSystemBody.SUN } ?: return
        val towardsSun = projection.projectUnbounded(sun.direction())
            ?.let { (x, y) -> atan2(y - center.y, x - center.x) } ?: 0f
        val lit = moon.illuminatedFraction.toFloat().coerceIn(0f, 1f)
        val terminator = radius * abs(1f - 2f * lit)
        val disc = Rect(center, radius)
        val ellipse = Rect(center.x - terminator, center.y - radius, center.x + terminator, center.y + radius)
        // A gibbous Moon's terminator bulges into the dark half, a crescent's into the lit one.
        val terminatorSweep = if (lit > HALF_LIT) HALF_TURN else -HALF_TURN
        val litPart = Path().apply {
            // Top of the disc, round the sunward side to the bottom, then back up along the terminator.
            arcTo(disc, startAngleDegrees = -QUARTER_TURN, sweepAngleDegrees = HALF_TURN, forceMoveTo = true)
            arcTo(ellipse, startAngleDegrees = QUARTER_TURN, sweepAngleDegrees = terminatorSweep, forceMoveTo = false)
            close()
        }
        rotateRad(towardsSun, pivot = center) { drawPath(litPart, frame.palette.moonLit) }
    }

    companion object {
        private const val ISS_RADIUS_DP = 2.5f
        private const val ISS_RING_DP = 5.5f
        private const val ISS_RING_ALPHA = 0.6f
        private const val SELECTION_RADIUS_DP = 14f
        private const val SELECTION_STROKE_DP = 2f
        private const val HALF_LIT = 0.5f
        private const val QUARTER_TURN = 90f
        private const val HALF_TURN = 180f
        private const val ARROW_INSET_DP = 40f
        private const val ARROW_LENGTH_DP = 22f
        private const val ARROW_HALF_WIDTH_DP = 10f
        private const val ARROW_NOTCH = 0.65f
        private const val MIN_DISC_DP = 5f
        private const val MAX_PLANET_DP = 4.5f
        private const val MIN_PLANET_DP = 2f
        private const val BRIGHTEST_PLANET = -4.5
        private const val PLANET_RADIUS_PER_MAGNITUDE = 0.4f

        /** Outer radius of a satellite's marker: a dot in a ring. */
        fun satelliteRadius(scope: DrawScope): Float = with(scope) { ISS_RING_DP.dp.toPx() }

        /**
         * On-screen radius of a body's disc: its true size when zoomed in enough to see it, else a
         * dot sized by brightness (planets) or a readable minimum (the Sun and the Moon).
         */
        fun discRadius(scope: DrawScope, position: SolarSystemPosition, projection: SkyProjection): Float =
            with(scope) {
                // A stereographic radius of tan(angle / 2), for half the apparent diameter.
                val halfDiameter = Math.toRadians(position.angularDiameterDegrees) / 2.0
                val trueRadius = (projection.scale * tan(halfDiameter / 2.0)).toFloat()
                val minimum = when (position.body) {
                    SolarSystemBody.SUN, SolarSystemBody.MOON -> MIN_DISC_DP.dp.toPx()
                    else -> planetDotDp(position.magnitude).dp.toPx()
                }
                maxOf(trueRadius, minimum)
            }

        private fun planetDotDp(magnitude: Double): Float =
            (MAX_PLANET_DP - PLANET_RADIUS_PER_MAGNITUDE * (magnitude - BRIGHTEST_PLANET).toFloat())
                .coerceIn(MIN_PLANET_DP, MAX_PLANET_DP)
    }
}

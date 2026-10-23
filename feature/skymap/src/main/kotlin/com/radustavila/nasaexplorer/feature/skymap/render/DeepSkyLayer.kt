package com.radustavila.nasaexplorer.feature.skymap.render

import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.core.graphics.withRotation
import com.radustavila.nasaexplorer.core.model.DeepSkyObject
import com.radustavila.nasaexplorer.core.model.DeepSkyType
import com.radustavila.nasaexplorer.core.skymap.HorizontalSky
import com.radustavila.nasaexplorer.core.skymap.SkyChart
import com.radustavila.nasaexplorer.core.skymap.SkyProjection
import kotlin.math.atan2
import kotlin.math.hypot

/**
 * Which deep-sky objects the map shows at a zoom (F23): the Messier and Caldwell objects from the
 * widest view, then fainter ones as the view narrows, down to magnitude 12.
 */
internal object DeepSkyStyle {

    private const val WIDE_VIEW = 110.0
    private const val MEDIUM_VIEW = 70.0
    private const val NARROW_VIEW = 35.0
    private const val ZOOMED_VIEW = 15.0
    private const val MEDIUM_LIMIT = 7f
    private const val NARROW_LIMIT = 8.5f
    private const val ZOOMED_LIMIT = 10.5f

    fun isShown(item: DeepSkyObject, magnitude: Float, fieldOfView: Double): Boolean =
        item.messier != null || item.caldwell != null || magnitude <= magnitudeLimit(fieldOfView)

    private fun magnitudeLimit(fieldOfView: Double): Float = when {
        fieldOfView > WIDE_VIEW -> Float.NEGATIVE_INFINITY
        fieldOfView > MEDIUM_VIEW -> MEDIUM_LIMIT
        fieldOfView > NARROW_VIEW -> NARROW_LIMIT
        fieldOfView > ZOOMED_VIEW -> ZOOMED_LIMIT
        else -> Float.POSITIVE_INFINITY
    }

    /** Messier objects are named from the widest view, Caldwell ones a step in, the rest zoomed in. */
    fun isLabelled(item: DeepSkyObject, fieldOfView: Double): Boolean = when {
        item.messier != null -> fieldOfView <= WIDE_VIEW
        item.caldwell != null -> fieldOfView <= MEDIUM_VIEW
        else -> fieldOfView <= ZOOMED_VIEW
    }

    /** "M31", "C14", else the common name or the NGC/IC designation. */
    fun shortName(item: DeepSkyObject): String = when {
        item.messier != null -> "M${item.messier}"
        item.caldwell != null -> "C${item.caldwell}"
        else -> item.commonName ?: item.designation.ifEmpty { item.id }
    }
}

/**
 * Screen geometry of one deep-sky object this frame: centre, radius along the major axis, and the
 * screen angle (radians) of the sky's north there. False when it is off the screen.
 */
internal class DeepSkyPlacement {
    var x = 0f
    var y = 0f
    var pixelsPerDegree = 0f
    var northAngle = 0f

    fun place(sky: HorizontalSky, index: Int, projection: SkyProjection): Boolean {
        val offset = index * SkyChart.VECTOR_FLOATS
        if (!projection.project(sky.deepSky, offset)) return false
        x = projection.x
        y = projection.y
        val isPlaced = projection.project(sky.deepSkyNorth, offset)
        if (isPlaced) {
            val dx = projection.x - x
            val dy = projection.y - y
            // Past the pole the marker sits south of the object instead.
            val declination = sky.chart.catalog.deepSky[index].position.declinationDegrees
            val northward = if (declination + SkyChart.NORTH_STEP_DEGREES <= MAX_DECLINATION) 1f else -1f
            pixelsPerDegree = (hypot(dx, dy) / SkyChart.NORTH_STEP_DEGREES).toFloat()
            northAngle = atan2(dy * northward, dx * northward)
        }
        return isPlaced
    }

    private companion object {
        const val MAX_DECLINATION = 90.0
    }
}

/**
 * Galaxies, clusters and nebulae (F23) with the symbols of printed charts: galaxies as ellipses
 * sized and turned by their axes and position angle, open clusters as dashed circles, globulars as
 * circles with a cross, planetary nebulae as rings with four ticks, bright nebulae as squares, dark
 * ones as dashed squares, star groups as dotted circles. Nothing is allocated per object or frame.
 */
internal class DeepSkyLayer {

    private val placement = DeepSkyPlacement()
    private val oval = RectF()
    private var paintsFor: Pair<SkyPalette, Float>? = null
    private val galaxyPaint = strokePaint()
    private val clusterPaint = strokePaint()
    private val dashedClusterPaint = strokePaint()
    private val dottedPaint = strokePaint()
    private val nebulaPaint = strokePaint()
    private val darkNebulaPaint = strokePaint()
    private var minRadius = 0f

    fun draw(scope: DrawScope, frame: SkyFrame, projection: SkyProjection) {
        val chart = frame.sky.chart
        if (frame.showDeepSky && chart.deepSkyCount > 0) scope.drawAll(frame, projection)
    }

    private fun DrawScope.drawAll(frame: SkyFrame, projection: SkyProjection) {
        val chart = frame.sky.chart
        preparePaints(frame.palette)
        minRadius = MIN_RADIUS_DP.dp.toPx()
        val margin = MAX_SYMBOL_MARGIN_DP.dp.toPx()
        drawIntoCanvas { canvas ->
            for (index in 0 until chart.deepSkyCount) drawObject(canvas.nativeCanvas, frame, projection, index, margin)
        }
    }

    private fun DrawScope.drawObject(
        native: android.graphics.Canvas,
        frame: SkyFrame,
        projection: SkyProjection,
        index: Int,
        margin: Float,
    ) {
        val chart = frame.sky.chart
        val item = chart.catalog.deepSky[index]
        val isPlaced = DeepSkyStyle.isShown(item, chart.deepSkyMagnitudes[index], frame.fieldOfViewDegrees) &&
            placement.place(frame.sky, index, projection)
        if (!isPlaced) return
        val radius = symbolRadius(item, placement.pixelsPerDegree, minRadius)
        val reach = radius + margin
        if (placement.x in -reach..size.width + reach && placement.y in -reach..size.height + reach) {
            drawSymbol(native, item, radius)
        }
    }

    private fun drawSymbol(native: android.graphics.Canvas, item: DeepSkyObject, radius: Float) {
        val x = placement.x
        val y = placement.y
        when (item.type) {
            DeepSkyType.GALAXY -> drawGalaxy(native, item, radius)
            DeepSkyType.OPEN_CLUSTER -> native.drawCircle(x, y, radius, dashedClusterPaint)
            DeepSkyType.GLOBULAR_CLUSTER -> {
                native.drawCircle(x, y, radius, clusterPaint)
                native.drawLine(x - radius, y, x + radius, y, clusterPaint)
                native.drawLine(x, y - radius, x, y + radius, clusterPaint)
            }
            DeepSkyType.PLANETARY_NEBULA -> {
                val ring = radius * PLANETARY_RING
                native.drawCircle(x, y, ring, nebulaPaint)
                native.drawLine(x - radius, y, x - ring, y, nebulaPaint)
                native.drawLine(x + ring, y, x + radius, y, nebulaPaint)
                native.drawLine(x, y - radius, x, y - ring, nebulaPaint)
                native.drawLine(x, y + ring, x, y + radius, nebulaPaint)
            }
            DeepSkyType.NEBULA, DeepSkyType.SUPERNOVA_REMNANT ->
                native.drawRect(x - radius, y - radius, x + radius, y + radius, nebulaPaint)
            DeepSkyType.DARK_NEBULA -> native.drawRect(x - radius, y - radius, x + radius, y + radius, darkNebulaPaint)
            DeepSkyType.STAR_GROUP -> native.drawCircle(x, y, radius, dottedPaint)
        }
    }

    /** An ellipse whose major axis points [DeepSkyObject.positionAngleDegrees] east of north. */
    private fun drawGalaxy(native: android.graphics.Canvas, item: DeepSkyObject, radius: Float) {
        val major = item.majorAxisArcmin ?: 0f
        val minor = item.minorAxisArcmin ?: major
        val ratio = if (major > 0f) (minor / major).coerceIn(MIN_AXIS_RATIO, 1f) else 1f
        // East is a quarter turn anticlockwise from north on a chart seen from below, as the sky is.
        val angle = placement.northAngle - Math.toRadians((item.positionAngleDegrees ?: 0).toDouble()).toFloat()
        oval.set(placement.x - radius, placement.y - radius * ratio, placement.x + radius, placement.y + radius * ratio)
        native.withRotation(Math.toDegrees(angle.toDouble()).toFloat(), placement.x, placement.y) {
            drawOval(oval, galaxyPaint)
        }
    }

    private fun DrawScope.preparePaints(palette: SkyPalette) {
        val key = palette to density
        if (paintsFor == key) return
        val width = STROKE_DP.dp.toPx()
        val dash = DASH_DP.dp.toPx()
        val dot = DOT_DP.dp.toPx()
        galaxyPaint.setup(palette.galaxy, width)
        clusterPaint.setup(palette.cluster, width)
        dashedClusterPaint.setup(palette.cluster, width).pathEffect = DashPathEffect(floatArrayOf(dash, dash), 0f)
        dottedPaint.setup(palette.cluster, width).pathEffect = DashPathEffect(floatArrayOf(dot, dot * 2f), 0f)
        nebulaPaint.setup(palette.nebula, width)
        darkNebulaPaint.setup(palette.nebula, width).pathEffect = DashPathEffect(floatArrayOf(dash, dash), 0f)
        darkNebulaPaint.alpha = DARK_NEBULA_ALPHA
        paintsFor = key
    }

    private fun Paint.setup(colour: androidx.compose.ui.graphics.Color, width: Float): Paint = apply {
        color = colour.toArgb()
        strokeWidth = width
        pathEffect = null
    }

    private fun strokePaint() = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

    companion object {
        /** Radius of the smallest symbol, so a small object is still a target for a finger. */
        const val MIN_RADIUS_DP = 4.5f
        private const val MAX_SYMBOL_MARGIN_DP = 2f
        private const val ARCMIN_PER_DEGREE = 60f
        private const val STROKE_DP = 1.1f
        private const val DASH_DP = 2.5f
        private const val DOT_DP = 1f
        private const val PLANETARY_RING = 0.55f
        private const val MIN_AXIS_RATIO = 0.2f
        private const val DARK_NEBULA_ALPHA = 150

        /** On-screen radius along the major axis: the real size once zoomed in enough, else [minRadius]. */
        fun symbolRadius(item: DeepSkyObject, pixelsPerDegree: Float, minRadius: Float): Float {
            val trueRadius = (item.majorAxisArcmin ?: 0f) / ARCMIN_PER_DEGREE / 2f * pixelsPerDegree
            return maxOf(trueRadius, minRadius)
        }
    }
}

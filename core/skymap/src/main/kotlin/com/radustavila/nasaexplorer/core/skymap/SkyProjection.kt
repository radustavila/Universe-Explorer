package com.radustavila.nasaexplorer.core.skymap

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.tan

/**
 * Where the map looks, as an orthonormal camera basis in the horizontal frame (east, north, up):
 * [forward] into the screen, [up] towards the top of the screen, [right] towards its right edge.
 */
class SkyView private constructor(val forward: SkyVector, val up: SkyVector) {

    val right: SkyVector = forward cross up

    /** Compass direction of the centre of the screen, degrees from north through east. */
    val azimuthDegrees: Double
        get() {
            // Looking straight up or down, the top of the screen says which way the phone faces.
            val heading = if (abs(forward.z) > POLE_LIMIT) up * -forward.z else forward
            val azimuth = Math.toDegrees(atan2(heading.x, heading.y))
            return if (azimuth < 0.0) azimuth + FULL_TURN else azimuth
        }

    /** Altitude of the centre of the screen, degrees. */
    val altitudeDegrees: Double get() = Math.toDegrees(asin(forward.z.coerceIn(-1.0, 1.0)))

    companion object {
        private const val FULL_TURN = 360.0
        private const val POLE_LIMIT = 0.999
        private const val MAX_ALTITUDE = 89.9

        /** Looking towards [azimuthDegrees] at [altitudeDegrees], the horizon level on the screen. */
        fun looking(azimuthDegrees: Double, altitudeDegrees: Double): SkyView {
            val altitude = Math.toRadians(altitudeDegrees.coerceIn(-MAX_ALTITUDE, MAX_ALTITUDE))
            val azimuth = Math.toRadians(azimuthDegrees)
            val forward = SkyVector(sin(azimuth) * cos(altitude), cos(azimuth) * cos(altitude), sin(altitude))
            val up = SkyVector(-sin(azimuth) * sin(altitude), -cos(azimuth) * sin(altitude), cos(altitude))
            return SkyView(forward, up)
        }

        /**
         * From a device orientation: [forward] out of the back of the screen and [up] along its long
         * edge, both in the horizontal frame. Re-orthonormalised, so smoothed sensor vectors can be
         * passed as they are.
         */
        fun fromBasis(forward: SkyVector, up: SkyVector): SkyView {
            val f = forward.normalized()
            val u = (up - f * (up dot f)).normalized()
            return SkyView(f, u)
        }
    }
}

/**
 * A stereographic projection of the sky centred on [view]: angles are kept, so constellations keep
 * their shapes even at the edges of a wide view, and circles on the sky (the horizon among them)
 * stay circles on the screen.
 *
 * [fieldOfViewDegrees] spans the shorter side of the [width] by [height] pixel screen. Projecting
 * writes into [x] and [y] instead of allocating, as the renderer projects thousands of stars a frame.
 */
class SkyProjection(
    val view: SkyView,
    val width: Float,
    val height: Float,
    val fieldOfViewDegrees: Double,
) {
    val centerX: Float = width / 2f
    val centerY: Float = height / 2f

    /** Pixels per unit of the stereographic plane: `tan(angle / 2)` from the centre. */
    val scale: Double = min(width, height) / 2.0 / tan(Math.toRadians(fieldOfViewDegrees) / 4.0)

    private val rx = view.right.x
    private val ry = view.right.y
    private val rz = view.right.z
    private val ux = view.up.x
    private val uy = view.up.y
    private val uz = view.up.z
    private val fx = view.forward.x
    private val fy = view.forward.y
    private val fz = view.forward.z

    /** Screen position of the last direction [project] accepted. */
    var x: Float = 0f
        private set
    var y: Float = 0f
        private set

    /** Cosine of the largest angle from the centre that can still land on the screen. */
    private val visibleCosine: Double = run {
        val halfDiagonal = hypot(width.toDouble(), height.toDouble()) / 2.0
        cos(min(PI - BEHIND_MARGIN, 2.0 * atan(halfDiagonal / scale) + EDGE_MARGIN))
    }

    /**
     * Projects the unit vector ([east], [north], [up]) of the horizontal frame. False, leaving [x]
     * and [y] alone, when it falls outside the screen by more than a small margin.
     */
    fun project(east: Double, north: Double, up: Double): Boolean {
        val depth = east * fx + north * fy + up * fz
        if (depth < visibleCosine) return false
        val factor = scale / (1.0 + depth)
        x = (centerX + (east * rx + north * ry + up * rz) * factor).toFloat()
        y = (centerY - (east * ux + north * uy + up * uz) * factor).toFloat()
        return true
    }

    fun project(direction: SkyVector): Boolean = project(direction.x, direction.y, direction.z)

    /**
     * Projects any direction, however far behind the viewer; null only straight behind. For arrows
     * and gestures, which need a screen direction even off screen.
     */
    fun projectUnbounded(direction: SkyVector): Pair<Float, Float>? {
        val depth = direction dot view.forward
        if (depth <= -1.0 + BEHIND_EPSILON) return null
        val factor = scale / (1.0 + depth)
        return (centerX + (direction dot view.right) * factor).toFloat() to
            (centerY - (direction dot view.up) * factor).toFloat()
    }

    /** The direction (horizontal frame) shown at screen point [screenX], [screenY]. */
    fun unproject(screenX: Float, screenY: Float): SkyVector {
        val px = (screenX - centerX) / scale
        val py = (centerY - screenY) / scale
        val norm = 1.0 + px * px + py * py
        val cameraX = 2.0 * px / norm
        val cameraY = 2.0 * py / norm
        val cameraZ = (1.0 - px * px - py * py) / norm
        return view.right * cameraX + view.up * cameraY + view.forward * cameraZ
    }

    /** Angle between the centre of the screen and its nearest edge, degrees. */
    val halfFieldOfViewDegrees: Double get() = fieldOfViewDegrees / 2.0

    /**
     * The horizon on the screen, and on which side of it the ground is. The horizon is a great
     * circle, so it projects to a circle, or to a straight line through the centre when the view is
     * level with it.
     */
    fun horizon(): HorizonShape {
        // The zenith in camera coordinates.
        val nx = view.right.z
        val ny = view.up.z
        val nz = view.forward.z
        val radius = scale / abs(nz)
        if (abs(nz) < LINE_EPSILON || radius > max(width, height) * LINE_RADIUS_FACTOR) {
            return HorizonShape.Line(centerX, centerY, normalX = nx.toFloat(), normalY = (-ny).toFloat())
        }
        return HorizonShape.Circle(
            centerX = (centerX + scale * nx / nz).toFloat(),
            centerY = (centerY - scale * ny / nz).toFloat(),
            radius = radius.toFloat(),
            isGroundInside = nz < 0.0,
        )
    }

    private companion object {
        /** About two degrees, so a star's dot and label do not pop at the edge. */
        const val EDGE_MARGIN = 0.035
        const val BEHIND_EPSILON = 1e-9

        /** Directions within this angle (radians) of straight behind would land absurdly far off screen. */
        const val BEHIND_MARGIN = 0.05
        const val LINE_EPSILON = 1e-6
        const val LINE_RADIUS_FACTOR = 50
    }
}

/** The horizon as drawn on the screen, in pixels. */
sealed interface HorizonShape {

    /** Ground on the side of the circle given by [isGroundInside]. */
    data class Circle(
        val centerX: Float,
        val centerY: Float,
        val radius: Float,
        val isGroundInside: Boolean,
    ) : HorizonShape

    /**
     * A line through ([pointX], [pointY]); the sky is on the side ([normalX], [normalY]) points to,
     * the ground on the other.
     */
    data class Line(val pointX: Float, val pointY: Float, val normalX: Float, val normalY: Float) : HorizonShape
}

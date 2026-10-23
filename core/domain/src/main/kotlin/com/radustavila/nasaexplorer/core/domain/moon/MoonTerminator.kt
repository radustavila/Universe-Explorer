package com.radustavila.nasaexplorer.core.domain.moon

import com.radustavila.nasaexplorer.core.model.SelenographicPoint
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.tan

/**
 * The Moon's night side as polygons a Web Mercator map can draw: everything more than 90° from the
 * subsolar point, cut at the map's latitude limit and split at the ±180° meridian.
 *
 * Row by row: at latitude φ a point is dark when cos(λ − λs) < −tan φ · tan φs, so the night spans
 * a band of half-width `π − acos(−tan φ tan φs)` centred on the antisolar longitude. The subsolar
 * latitude φs never exceeds about 1.6°, so the terminator runs nearly pole to pole.
 */
object MoonTerminator {

    /** Polygons (outer rings, unclosed) covering the night side within ±[maxLatitude]. */
    fun nightSide(
        subsolar: SelenographicPoint,
        maxLatitude: Double = DEFAULT_MAX_LATITUDE,
        stepDegrees: Double = DEFAULT_STEP_DEGREES,
    ): List<List<SelenographicPoint>> {
        val rows = latitudes(maxLatitude, stepDegrees).map { latitude ->
            latitude to halfWidthDegrees(latitude, subsolar.latitude)
        }
        val centre = wrap(subsolar.longitude + HALF_TURN)
        // The night band may run past ±180°: draw its copies one turn east and west, each clipped.
        return listOf(-FULL_TURN, 0.0, FULL_TURN).flatMap { shift ->
            polygons(
                rows.map { (latitude, halfWidth) ->
                    val west = max(centre + shift - halfWidth, -HALF_TURN)
                    val east = min(centre + shift + halfWidth, HALF_TURN)
                    Row(latitude, west, east)
                },
            )
        }
    }

    /** True when [point] is on the night side for [subsolar]. */
    fun isDark(point: SelenographicPoint, subsolar: SelenographicPoint): Boolean {
        val halfWidth = halfWidthDegrees(point.latitude, subsolar.latitude)
        if (halfWidth >= HALF_TURN) return true
        val fromAntisolar = abs(wrap(point.longitude - (subsolar.longitude + HALF_TURN)))
        return fromAntisolar < halfWidth
    }

    /** Half-width of the night band at [latitude], 0..180°. */
    internal fun halfWidthDegrees(latitude: Double, subsolarLatitude: Double): Double {
        val k = -tan(Math.toRadians(latitude)) * tan(Math.toRadians(subsolarLatitude))
        return when {
            k <= -1.0 -> 0.0 // polar day: the Sun never sets on this row
            k >= 1.0 -> HALF_TURN // polar night
            else -> Math.toDegrees(PI - acos(k))
        }
    }

    private data class Row(val latitude: Double, val west: Double, val east: Double) {
        val isEmpty: Boolean get() = east - west <= EMPTY_WIDTH
    }

    /** Runs of consecutive non-empty rows become polygons: up the east edge, down the west edge. */
    private fun polygons(rows: List<Row>): List<List<SelenographicPoint>> {
        val runs = mutableListOf<MutableList<Row>>()
        var run: MutableList<Row>? = null
        for (row in rows) {
            if (row.isEmpty) {
                run = null
            } else {
                run ?: mutableListOf<Row>().also { run = it; runs += it }
                run!!.add(row)
            }
        }
        return runs.filter { it.size >= 2 }.map { band ->
            band.map { SelenographicPoint(it.latitude, it.east) } +
                band.asReversed().map { SelenographicPoint(it.latitude, it.west) }
        }
    }

    private fun latitudes(maxLatitude: Double, step: Double): List<Double> {
        val count = (2 * maxLatitude / step).toInt()
        return (0..count).map { -maxLatitude + it * (2 * maxLatitude / count) }
    }

    private fun wrap(longitude: Double): Double {
        var value = (longitude + HALF_TURN) % FULL_TURN
        if (value < 0) value += FULL_TURN
        return value - HALF_TURN
    }

    /** Where Web Mercator tiles end. */
    const val DEFAULT_MAX_LATITUDE = 85.05112878
    private const val DEFAULT_STEP_DEGREES = 1.0
    private const val HALF_TURN = 180.0
    private const val FULL_TURN = 360.0
    private const val EMPTY_WIDTH = 1e-9
}

package com.radustavila.nasaexplorer.feature.spaceweather.components

import androidx.compose.ui.graphics.Color
import com.radustavila.nasaexplorer.core.map.overlay.GeoPoint
import com.radustavila.nasaexplorer.core.map.overlay.MapOverlay
import com.radustavila.nasaexplorer.core.map.overlay.MapPolygon
import com.radustavila.nasaexplorer.core.model.AuroraForecast

/**
 * Turns NOAA's aurora grid into map polygons: one overlay per probability band, stacked so the
 * likelier core glows brighter than the faint edge.
 *
 * The grid is walked column by column (one degree of longitude). Within a hemisphere, each column's
 * run of cells at or above a band's probability becomes a vertical span; neighbouring columns whose
 * spans overlap are joined into one x-monotone strip, so the oval draws as a few smooth polygons
 * rather than thousands of cells. A column with two separate runs falls back to one rectangle each.
 */
internal object AuroraOval {

    data class Band(val minProbability: Int, val color: Color)

    val BANDS = listOf(
        Band(minProbability = 5, color = Color(0xFF3DDC84).copy(alpha = 0.30f)),
        Band(minProbability = 20, color = Color(0xFFC6FF00).copy(alpha = 0.40f)),
        Band(minProbability = 50, color = Color(0xFFFF4081).copy(alpha = 0.50f)),
    )

    /** Web Mercator stops just short of the poles. */
    private const val MAX_LATITUDE = 85.0
    private const val HALF_CELL = 0.5
    private const val WEST = -180
    private const val EAST = 179

    fun overlays(forecast: AuroraForecast): List<MapOverlay> = BANDS.map { band ->
        val rings = polygons(forecast, band.minProbability)
        MapOverlay(
            id = "aurora-${band.minProbability}",
            shapes = rings.mapIndexed { index, ring ->
                MapPolygon(id = "aurora-${band.minProbability}-$index", outline = ring, fillColor = band.color)
            },
        )
    }

    /** Outline rings of every area where the probability is at least [minimum] percent. */
    fun polygons(forecast: AuroraForecast, minimum: Int): List<List<GeoPoint>> =
        hemispherePolygons(forecast, minimum, 1..89) + hemispherePolygons(forecast, minimum, -89..-1)

    private data class Span(val longitude: Int, val south: Double, val north: Double)

    private fun hemispherePolygons(forecast: AuroraForecast, minimum: Int, latitudes: IntRange): List<List<GeoPoint>> {
        val rings = mutableListOf<List<GeoPoint>>()
        var strip = mutableListOf<Span>()
        fun flush() {
            if (strip.isNotEmpty()) rings += stripRing(strip)
            strip = mutableListOf()
        }

        for (longitude in WEST..EAST) {
            val runs = runs(forecast, minimum, longitude, latitudes)
            when {
                runs.size == 1 -> {
                    val span = runs.single()
                    val last = strip.lastOrNull()
                    if (last != null && (span.south > last.north || span.north < last.south)) flush()
                    strip += span
                }

                else -> {
                    flush()
                    runs.forEach { rings += stripRing(listOf(it)) }
                }
            }
        }
        flush()
        return rings
    }

    /** Contiguous latitude runs at or above [minimum] in one column, as cell-edge spans. */
    private fun runs(forecast: AuroraForecast, minimum: Int, longitude: Int, latitudes: IntRange): List<Span> {
        val spans = mutableListOf<Span>()
        var start: Int? = null
        for (latitude in latitudes.first..latitudes.last + 1) {
            val inside = latitude <= latitudes.last && forecast.probabilityAt(longitude, latitude) >= minimum
            if (inside && start == null) start = latitude
            if (!inside && start != null) {
                val south = (start - HALF_CELL).coerceIn(-MAX_LATITUDE, MAX_LATITUDE)
                val north = (latitude - 1 + HALF_CELL).coerceIn(-MAX_LATITUDE, MAX_LATITUDE)
                if (north > south) spans += Span(longitude, south, north)
                start = null
            }
        }
        return spans
    }

    /** South edge west to east, then north edge back east to west. */
    private fun stripRing(strip: List<Span>): List<GeoPoint> {
        val south = strip.flatMap { span ->
            listOf(GeoPoint(span.south, west(span.longitude)), GeoPoint(span.south, east(span.longitude)))
        }
        val north = strip.asReversed().flatMap { span ->
            listOf(GeoPoint(span.north, east(span.longitude)), GeoPoint(span.north, west(span.longitude)))
        }
        return south + north
    }

    private fun west(longitude: Int): Double = maxOf(longitude - HALF_CELL, WEST.toDouble())

    private fun east(longitude: Int): Double = if (longitude == EAST) -WEST.toDouble() else longitude + HALF_CELL
}

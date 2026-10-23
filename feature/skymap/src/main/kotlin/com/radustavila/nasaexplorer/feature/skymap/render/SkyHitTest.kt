package com.radustavila.nasaexplorer.feature.skymap.render

import com.radustavila.nasaexplorer.core.model.GalacticCentre
import com.radustavila.nasaexplorer.core.model.SkyMapBodies
import com.radustavila.nasaexplorer.core.skymap.HorizontalSky
import com.radustavila.nasaexplorer.core.skymap.SkyChart
import com.radustavila.nasaexplorer.core.skymap.SkyProjection
import com.radustavila.nasaexplorer.core.skymap.StarStyle
import com.radustavila.nasaexplorer.feature.skymap.SkyTarget
import kotlin.math.hypot

/**
 * What a tap at a screen point means: the Sun, Moon, a planet, the ISS, a comet or the galactic
 * centre first, then a deep-sky object whose symbol the tap falls inside, then the nearest drawn
 * star, then the nearest deep-sky symbol within reach.
 */
/** The deep-sky layer as a tap sees it, when it is on: the smallest symbol's radius in pixels. */
internal class DeepSkyHitArea(val minRadius: Float)

internal object SkyHitTest {

    /** A star this many magnitudes brighter wins over one this many pixels per magnitude nearer. */
    private const val PIXELS_PER_MAGNITUDE = 4f

    fun find(
        x: Float,
        y: Float,
        radius: Float,
        sky: HorizontalSky,
        bodies: SkyMapBodies?,
        projection: SkyProjection,
        deepSky: DeepSkyHitArea? = null,
    ): SkyTarget? = findBody(x, y, radius, sky, bodies, projection)
        ?: deepSky?.let { findDeepSky(x, y, 0f, sky, projection, it) }
        ?: findStar(x, y, radius, sky, projection)
        ?: deepSky?.let { findDeepSky(x, y, radius, sky, projection, it) }

    /**
     * The nearest shown deep-sky object whose symbol, grown by [reach] pixels, holds the point; the
     * smallest symbol wins among those that hold it, so a galaxy inside a big nebula can be picked.
     */
    private fun findDeepSky(
        x: Float,
        y: Float,
        reach: Float,
        sky: HorizontalSky,
        projection: SkyProjection,
        area: DeepSkyHitArea,
    ): SkyTarget? {
        val chart = sky.chart
        val placement = DeepSkyPlacement()
        var best = -1
        var bestScore = Float.MAX_VALUE
        for (index in 0 until chart.deepSkyCount) {
            val item = chart.catalog.deepSky[index]
            val isPlaced = DeepSkyStyle.isShown(item, chart.deepSkyMagnitudes[index], projection.fieldOfViewDegrees) &&
                placement.place(sky, index, projection)
            if (!isPlaced) continue
            val symbol = DeepSkyLayer.symbolRadius(item, placement.pixelsPerDegree, area.minRadius)
            val distance = hypot(placement.x - x, placement.y - y)
            val score = if (reach == 0f) symbol else distance
            if (distance <= symbol + reach && score < bestScore) {
                bestScore = score
                best = index
            }
        }
        return if (best >= 0) SkyTarget.DeepSky(chart.catalog.deepSky[best].id) else null
    }

    private fun findBody(
        x: Float,
        y: Float,
        radius: Float,
        sky: HorizontalSky,
        bodies: SkyMapBodies?,
        projection: SkyProjection,
    ): SkyTarget? {
        var best: SkyTarget? = null
        var bestDistance = radius
        bodies?.solarSystem?.forEach { position ->
            if (!projection.project(position.direction())) return@forEach
            val distance = hypot(projection.x - x, projection.y - y)
            if (distance <= bestDistance) {
                bestDistance = distance
                best = SkyTarget.Body(position.body)
            }
        }
        bodies?.satellites?.forEach { satellite ->
            if (!projection.project(SkyChart.apparent(satellite.horizontal))) return@forEach
            val distance = hypot(projection.x - x, projection.y - y)
            if (distance <= bestDistance) {
                bestDistance = distance
                best = SkyTarget.Satellite(satellite.noradId)
            }
        }
        bodies?.comets?.forEach { comet ->
            if (!projection.project(SkyChart.apparent(comet.horizontal))) return@forEach
            val distance = hypot(projection.x - x, projection.y - y)
            if (distance <= bestDistance) {
                bestDistance = distance
                best = SkyTarget.Comet(comet.designation)
            }
        }
        if (projection.project(sky.direction(GalacticCentre.POSITION))) {
            val distance = hypot(projection.x - x, projection.y - y)
            if (distance <= bestDistance) best = SkyTarget.GalacticCentre
        }
        return best
    }

    private fun findStar(x: Float, y: Float, radius: Float, sky: HorizontalSky, projection: SkyProjection): SkyTarget? {
        val magnitudes = sky.chart.starMagnitudes
        var best = -1
        var bestScore = Float.MAX_VALUE
        // Only the stars drawn at this zoom, a prefix of the brightest-first catalogue.
        for (i in 0 until sky.chart.starsBrighterThan(StarStyle.magnitudeLimit(projection.fieldOfViewDegrees))) {
            val distance = screenDistance(sky, projection, i, x, y)
            val score = distance + magnitudes[i] * PIXELS_PER_MAGNITUDE
            if (distance <= radius && score < bestScore) {
                bestScore = score
                best = i
            }
        }
        return if (best >= 0) SkyTarget.StarTarget(best) else null
    }

    private fun screenDistance(sky: HorizontalSky, projection: SkyProjection, star: Int, x: Float, y: Float): Float =
        if (projection.project(sky.stars, star * SkyChart.VECTOR_FLOATS)) {
            hypot(projection.x - x, projection.y - y)
        } else {
            Float.MAX_VALUE
        }
}

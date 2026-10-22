package com.radustavila.nasaexplorer.feature.skymap.render

import com.radustavila.nasaexplorer.core.model.SkyMapBodies
import com.radustavila.nasaexplorer.core.skymap.HorizontalSky
import com.radustavila.nasaexplorer.core.skymap.SkyChart
import com.radustavila.nasaexplorer.core.skymap.SkyProjection
import com.radustavila.nasaexplorer.core.skymap.StarStyle
import com.radustavila.nasaexplorer.feature.skymap.SkyTarget
import kotlin.math.hypot

/** What a tap at a screen point means: the Sun, Moon, a planet or the ISS first, then the nearest drawn star. */
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
    ): SkyTarget? = findBody(x, y, radius, bodies, projection) ?: findStar(x, y, radius, sky, projection)

    private fun findBody(x: Float, y: Float, radius: Float, bodies: SkyMapBodies?, projection: SkyProjection): SkyTarget? {
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
        return best
    }

    private fun findStar(x: Float, y: Float, radius: Float, sky: HorizontalSky, projection: SkyProjection): SkyTarget? {
        val limit = StarStyle.magnitudeLimit(projection.fieldOfViewDegrees)
        val magnitudes = sky.chart.starMagnitudes
        var best = -1
        var bestScore = Float.MAX_VALUE
        for (i in 0 until sky.chart.starCount) {
            if (magnitudes[i] > limit) break
            if (!projection.project(sky.star(i))) continue
            val distance = hypot(projection.x - x, projection.y - y)
            if (distance > radius) continue
            val score = distance + magnitudes[i] * PIXELS_PER_MAGNITUDE
            if (score < bestScore) {
                bestScore = score
                best = i
            }
        }
        return if (best >= 0) SkyTarget.StarTarget(best) else null
    }
}

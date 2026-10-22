package com.radustavila.nasaexplorer.core.skymap

import com.radustavila.nasaexplorer.core.model.EquatorialCoordinate
import com.radustavila.nasaexplorer.core.model.GeoCoordinate
import com.radustavila.nasaexplorer.core.model.HorizontalCoordinate
import com.radustavila.nasaexplorer.core.model.SkyCatalog
import java.time.Instant
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The catalogue as flat arrays of J2000 unit vectors, so a renderer can turn the whole sky for a new
 * place or instant with one matrix ([at]) and then project it every frame without allocating.
 */
class SkyChart(val catalog: SkyCatalog) {

    val starCount: Int = catalog.stars.size

    /** x, y, z of each star, in catalogue order (brightest first). */
    private val starVectors = FloatArray(starCount * VECTOR_FLOATS).also { vectors ->
        catalog.stars.forEachIndexed { i, star -> vectors.put(i, SkyVector.of(star.position)) }
    }

    val starMagnitudes = FloatArray(starCount) { catalog.stars[it].magnitude }

    /** NaN where the colour is unknown. */
    val starColourIndices = FloatArray(starCount) { catalog.stars[it].colourIndex ?: Float.NaN }

    /** Index into [SkyCatalog.constellations] of each figure segment. */
    val segmentFigures: IntArray

    /** Two points (x, y, z twice) per segment of every figure. */
    private val segmentVectors: FloatArray

    init {
        val figures = mutableListOf<Int>()
        val points = mutableListOf<SkyVector>()
        catalog.constellations.forEachIndexed { figure, constellation ->
            constellation.lines.forEach { line ->
                line.zipWithNext { from, to ->
                    figures += figure
                    points += SkyVector.of(from)
                    points += SkyVector.of(to)
                }
            }
        }
        segmentFigures = figures.toIntArray()
        segmentVectors = FloatArray(points.size * VECTOR_FLOATS).also { vectors ->
            points.forEachIndexed { i, point -> vectors.put(i, point) }
        }
    }

    val segmentCount: Int get() = segmentFigures.size

    private val labelVectors = FloatArray(catalog.constellations.size * VECTOR_FLOATS).also { vectors ->
        catalog.constellations.forEachIndexed { i, figure -> vectors.put(i, SkyVector.of(figure.labelPosition)) }
    }

    /** The whole chart in [observer]'s sky at [at], with refraction. */
    fun at(observer: GeoCoordinate, at: Instant): HorizontalSky {
        val matrix = CelestialFrames.j2000ToHorizontal(observer, at)
        return HorizontalSky(
            chart = this,
            matrix = matrix,
            stars = rotate(matrix, starVectors),
            segments = rotate(matrix, segmentVectors),
            labels = rotate(matrix, labelVectors),
        )
    }

    private fun rotate(matrix: RotationMatrix, vectors: FloatArray): FloatArray {
        val m00 = matrix[0, 0]
        val m01 = matrix[0, 1]
        val m02 = matrix[0, 2]
        val m10 = matrix[1, 0]
        val m11 = matrix[1, 1]
        val m12 = matrix[1, 2]
        val m20 = matrix[2, 0]
        val m21 = matrix[2, 1]
        val m22 = matrix[2, 2]
        val out = FloatArray(vectors.size)
        var i = 0
        while (i < vectors.size) {
            val x = vectors[i].toDouble()
            val y = vectors[i + 1].toDouble()
            val z = vectors[i + 2].toDouble()
            writeApparent(
                out,
                i,
                m00 * x + m01 * y + m02 * z,
                m10 * x + m11 * y + m12 * z,
                m20 * x + m21 * y + m22 * z,
            )
            i += VECTOR_FLOATS
        }
        return out
    }

    private fun FloatArray.put(index: Int, vector: SkyVector) {
        this[index * VECTOR_FLOATS] = vector.x.toFloat()
        this[index * VECTOR_FLOATS + 1] = vector.y.toFloat()
        this[index * VECTOR_FLOATS + 2] = vector.z.toFloat()
    }

    companion object {
        /** Floats per vector in the flat arrays: east, north, up (or x, y, z). */
        const val VECTOR_FLOATS = 3

        /** Refraction is below a hundredth of a degree above this height (sin 60 degrees). */
        private const val REFRACTION_LIMIT = 0.866

        /**
         * Writes the geometric horizontal direction ([east], [north], [up]) into [out] at [offset],
         * raised by atmospheric refraction near the horizon, as the eye sees it.
         */
        fun writeApparent(out: FloatArray, offset: Int, east: Double, north: Double, up: Double) {
            var e = east
            var n = north
            var u = up
            if (u < REFRACTION_LIMIT) {
                val altitude = Math.toDegrees(asin(u.coerceIn(-1.0, 1.0)))
                val refraction = CelestialFrames.refraction(altitude)
                if (refraction > 0.0) {
                    val apparent = Math.toRadians(altitude + refraction)
                    val horizontal = sqrt(e * e + n * n)
                    if (horizontal > 0.0) {
                        val factor = cos(apparent) / horizontal
                        e *= factor
                        n *= factor
                        u = sin(apparent)
                    }
                }
            }
            out[offset] = e.toFloat()
            out[offset + 1] = n.toFloat()
            out[offset + 2] = u.toFloat()
        }

        /** [geometric] as seen through the atmosphere. */
        fun apparent(geometric: HorizontalCoordinate): SkyVector {
            val vector = SkyVector.of(geometric)
            val out = FloatArray(VECTOR_FLOATS)
            writeApparent(out, 0, vector.x, vector.y, vector.z)
            return out.vectorAt(0)
        }
    }
}

/** A [SkyChart] turned into one observer's sky at one instant: unit vectors east, north, up. */
class HorizontalSky internal constructor(
    val chart: SkyChart,
    /** J2000 to horizontal, without refraction. */
    val matrix: RotationMatrix,
    /** x, y, z per star. */
    val stars: FloatArray,
    /** Two points per figure segment. */
    val segments: FloatArray,
    /** One point per constellation name. */
    val labels: FloatArray,
) {
    fun star(index: Int) = stars.vectorAt(index)

    fun label(index: Int) = labels.vectorAt(index)

    /** Any J2000 direction, refracted. */
    fun direction(position: EquatorialCoordinate): SkyVector {
        val geometric = matrix * SkyVector.of(position)
        val out = FloatArray(SkyChart.VECTOR_FLOATS)
        SkyChart.writeApparent(out, 0, geometric.x, geometric.y, geometric.z)
        return out.vectorAt(0)
    }
}

private fun FloatArray.vectorAt(index: Int): SkyVector {
    val base = index * SkyChart.VECTOR_FLOATS
    return SkyVector(this[base].toDouble(), this[base + 1].toDouble(), this[base + 2].toDouble())
}

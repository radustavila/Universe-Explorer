package com.radustavila.nasaexplorer.core.skymap

import com.radustavila.nasaexplorer.core.model.EquatorialCoordinate
import com.radustavila.nasaexplorer.core.model.HorizontalCoordinate
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A direction or position in 3D. In the horizontal frame the axes are east, north and up; in the
 * equatorial frame x points to the March equinox, z to the north celestial pole.
 */
data class SkyVector(val x: Double, val y: Double, val z: Double) {

    operator fun plus(other: SkyVector) = SkyVector(x + other.x, y + other.y, z + other.z)

    operator fun minus(other: SkyVector) = SkyVector(x - other.x, y - other.y, z - other.z)

    operator fun times(factor: Double) = SkyVector(x * factor, y * factor, z * factor)

    infix fun dot(other: SkyVector): Double = x * other.x + y * other.y + z * other.z

    infix fun cross(other: SkyVector) = SkyVector(
        y * other.z - z * other.y,
        z * other.x - x * other.z,
        x * other.y - y * other.x,
    )

    val length: Double get() = sqrt(this dot this)

    fun normalized(): SkyVector = times(1.0 / length)

    /** Reads a horizontal-frame vector as altitude and azimuth. */
    fun toHorizontal(): HorizontalCoordinate {
        val altitude = Math.toDegrees(asin((z / length).coerceIn(-1.0, 1.0)))
        var azimuth = Math.toDegrees(atan2(x, y))
        if (azimuth < 0.0) azimuth += FULL_TURN
        return HorizontalCoordinate(altitude, azimuth)
    }

    /** Reads an equatorial-frame vector as right ascension and declination. */
    fun toEquatorial(): EquatorialCoordinate {
        val declination = Math.toDegrees(asin((z / length).coerceIn(-1.0, 1.0)))
        var rightAscension = Math.toDegrees(atan2(y, x))
        if (rightAscension < 0.0) rightAscension += FULL_TURN
        return EquatorialCoordinate(rightAscension, declination)
    }

    companion object {
        private const val FULL_TURN = 360.0

        val UP = SkyVector(0.0, 0.0, 1.0)

        fun of(coordinate: HorizontalCoordinate): SkyVector =
            horizontal(coordinate.altitudeDegrees, coordinate.azimuthDegrees)

        fun horizontal(altitudeDegrees: Double, azimuthDegrees: Double): SkyVector {
            val altitude = Math.toRadians(altitudeDegrees)
            val azimuth = Math.toRadians(azimuthDegrees)
            return SkyVector(sin(azimuth) * cos(altitude), cos(azimuth) * cos(altitude), sin(altitude))
        }

        fun of(coordinate: EquatorialCoordinate): SkyVector {
            val rightAscension = Math.toRadians(coordinate.rightAscensionDegrees)
            val declination = Math.toRadians(coordinate.declinationDegrees)
            return SkyVector(
                cos(declination) * cos(rightAscension),
                cos(declination) * sin(rightAscension),
                sin(declination),
            )
        }
    }
}

/** A 3x3 rotation, row-major. */
@Suppress("MagicNumber")
class RotationMatrix(private val elements: DoubleArray) {

    init {
        require(elements.size == SIZE) { "A 3x3 matrix has 9 elements" }
    }

    operator fun get(row: Int, column: Int): Double = elements[row * 3 + column]

    operator fun times(vector: SkyVector) = SkyVector(
        get(0, 0) * vector.x + get(0, 1) * vector.y + get(0, 2) * vector.z,
        get(1, 0) * vector.x + get(1, 1) * vector.y + get(1, 2) * vector.z,
        get(2, 0) * vector.x + get(2, 1) * vector.y + get(2, 2) * vector.z,
    )

    operator fun times(other: RotationMatrix): RotationMatrix {
        val product = DoubleArray(SIZE)
        for (row in 0 until 3) {
            for (column in 0 until 3) {
                product[row * 3 + column] =
                    get(row, 0) * other[0, column] + get(row, 1) * other[1, column] + get(row, 2) * other[2, column]
            }
        }
        return RotationMatrix(product)
    }

    /** One row as a vector: the image of that output axis in the input frame. */
    fun row(row: Int) = SkyVector(get(row, 0), get(row, 1), get(row, 2))

    companion object {
        private const val SIZE = 9

        fun ofRows(first: SkyVector, second: SkyVector, third: SkyVector) = RotationMatrix(
            doubleArrayOf(first.x, first.y, first.z, second.x, second.y, second.z, third.x, third.y, third.z),
        )
    }
}

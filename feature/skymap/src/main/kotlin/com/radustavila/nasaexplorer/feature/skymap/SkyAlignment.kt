package com.radustavila.nasaexplorer.feature.skymap

import androidx.compose.runtime.Immutable
import com.radustavila.nasaexplorer.core.skymap.SkyVector
import com.radustavila.nasaexplorer.core.skymap.SkyView
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * The correction the user lined up in AR: the compass reads [azimuthDegrees] too far west (often
 * 5 to 20 degrees indoors or near metal) and the tilt [altitudeDegrees] too low. Applying it turns
 * the sensor's view that much east and up.
 */
@Immutable
data class SkyAlignment(val azimuthDegrees: Double, val altitudeDegrees: Double) {

    /** [view] corrected: tilted up by [altitudeDegrees] about its level right-hand axis, then turned. */
    fun applyTo(view: SkyView): SkyView {
        if (this == NONE) return view
        var forward = view.forward
        var up = view.up
        val levelRight = SkyVector(view.right.x, view.right.y, 0.0)
        val levelLength = hypot(levelRight.x, levelRight.y)
        if (altitudeDegrees != 0.0 && levelLength > MIN_AXIS_LENGTH) {
            val axis = levelRight * (1.0 / levelLength)
            forward = forward.rotated(axis, altitudeDegrees)
            up = up.rotated(axis, altitudeDegrees)
        }
        return SkyView.fromBasis(forward.turned(azimuthDegrees), up.turned(azimuthDegrees))
    }

    /** This correction, moved [azimuthDegrees] east and [altitudeDegrees] up more, within bounds. */
    fun turnedBy(azimuthDegrees: Double, altitudeDegrees: Double) = SkyAlignment(
        azimuthDegrees = SkyCameraState.shortestTurn(0.0, this.azimuthDegrees + azimuthDegrees),
        altitudeDegrees = (this.altitudeDegrees + altitudeDegrees).coerceIn(-MAX_TILT, MAX_TILT),
    )

    companion object {
        val NONE = SkyAlignment(0.0, 0.0)

        /** Tilt sensors are good to a degree or two; a bigger correction is a mistaken drag. */
        private const val MAX_TILT = 30.0
        private const val MIN_AXIS_LENGTH = 1e-6
    }
}

/** This vector turned [degrees] about the zenith, east of north being positive. */
private fun SkyVector.turned(degrees: Double): SkyVector {
    if (degrees == 0.0) return this
    val angle = Math.toRadians(degrees)
    val c = cos(angle)
    val s = sin(angle)
    return SkyVector(x * c + y * s, -x * s + y * c, z)
}

/** Rodrigues' rotation of this vector by [degrees] about the unit [axis], right-handed. */
private fun SkyVector.rotated(axis: SkyVector, degrees: Double): SkyVector {
    val angle = Math.toRadians(degrees)
    val c = cos(angle)
    return this * c + (axis cross this) * sin(angle) + axis * ((axis dot this) * (1.0 - c))
}

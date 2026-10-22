package com.radustavila.nasaexplorer.feature.skymap.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.radustavila.nasaexplorer.core.skymap.SkyVector
import com.radustavila.nasaexplorer.core.skymap.SkyView
import kotlin.math.cos
import kotlin.math.sin

/** How much to trust the rotation-vector sensor's readings. */
@Stable
internal class DeviceOrientationState {
    /** The compass needs calibrating: the sensor reports low accuracy or a wide heading error. */
    var isAccuracyLow: Boolean by mutableStateOf(false)
        internal set
}

/** Whether the device has the fused rotation-vector sensor that compass mode needs. */
internal fun Context.hasRotationSensor(): Boolean =
    getSystemService(SensorManager::class.java)?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) != null

/**
 * Follows the device's orientation while [enabled], passing each reading to [onView] from the
 * sensor thread's callback, not from composition, so only what reads the view (the canvas, while
 * drawing) updates: the view out of the back of the screen, the top of the screen up, corrected
 * from magnetic to true north by [declinationDegrees] and for the screen's rotation. Readings are
 * smoothed lightly; big turns are followed at once.
 */
@Composable
internal fun rememberDeviceOrientation(
    enabled: Boolean,
    declinationDegrees: Float,
    onView: (SkyView) -> Unit,
): DeviceOrientationState {
    val context = LocalContext.current
    val state = remember { DeviceOrientationState() }
    val currentOnView = rememberUpdatedState(onView)
    DisposableEffect(context, enabled, declinationDegrees) {
        val sensorManager = context.getSystemService(SensorManager::class.java)
        val sensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        if (!enabled || sensorManager == null || sensor == null) return@DisposableEffect onDispose { }
        val listener = OrientationListener(context, state, declinationDegrees) { currentOnView.value(it) }
        sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
        onDispose { sensorManager.unregisterListener(listener) }
    }
    return state
}

private class OrientationListener(
    private val context: Context,
    private val state: DeviceOrientationState,
    private val declinationDegrees: Float,
    private val onView: (SkyView) -> Unit,
) : SensorEventListener {

    private val rotation = FloatArray(MATRIX_SIZE)
    private val remapped = FloatArray(MATRIX_SIZE)
    private var smoothed: SkyView? = null

    override fun onSensorChanged(event: SensorEvent) {
        SensorManager.getRotationMatrixFromVector(rotation, event.values)
        val (axisX, axisY) = when (ContextCompat.getDisplayOrDefault(context).rotation) {
            Surface.ROTATION_90 -> SensorManager.AXIS_Y to SensorManager.AXIS_MINUS_X
            Surface.ROTATION_180 -> SensorManager.AXIS_MINUS_X to SensorManager.AXIS_MINUS_Y
            Surface.ROTATION_270 -> SensorManager.AXIS_MINUS_Y to SensorManager.AXIS_X
            else -> SensorManager.AXIS_X to SensorManager.AXIS_Y
        }
        SensorManager.remapCoordinateSystem(rotation, axisX, axisY, remapped)
        val reading = OrientationMath.view(remapped, declinationDegrees)
        val view = OrientationMath.smooth(smoothed, reading)
        smoothed = view
        onView(view)
        // values[4], when present, is the estimated heading error in radians (-1 when unknown).
        val headingError = event.values.getOrNull(HEADING_ACCURACY_INDEX) ?: -1f
        state.isAccuracyLow = event.accuracy <= SensorManager.SENSOR_STATUS_ACCURACY_LOW ||
            headingError > MAX_HEADING_ERROR_RADIANS
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
        state.isAccuracyLow = accuracy <= SensorManager.SENSOR_STATUS_ACCURACY_LOW
    }

    private companion object {
        const val MATRIX_SIZE = 9
        const val HEADING_ACCURACY_INDEX = 4

        /** About 20 degrees. */
        const val MAX_HEADING_ERROR_RADIANS = 0.35f
    }
}

/** The sensor maths, kept free of Android types so it can be tested on the JVM. */
internal object OrientationMath {

    private const val SMOOTHING = 0.3
    private const val SNAP_COSINE = 0.94

    /**
     * The view of a device whose rotation matrix (row-major, device to east-north-up world, already
     * remapped for the screen's rotation) is [matrix]: the camera looks out of the back (-z) with the
     * top of the screen (+y) up.
     */
    fun view(matrix: FloatArray, declinationDegrees: Float): SkyView {
        val forward = SkyVector(-matrix[2].toDouble(), -matrix[5].toDouble(), -matrix[8].toDouble())
        val up = SkyVector(matrix[1].toDouble(), matrix[4].toDouble(), matrix[7].toDouble())
        return SkyView.fromBasis(toTrueNorth(forward, declinationDegrees), toTrueNorth(up, declinationDegrees))
    }

    /** Eases [previous] towards [reading]; a turn of more than about 20 degrees is taken at once. */
    fun smooth(previous: SkyView?, reading: SkyView): SkyView {
        if (previous == null || (previous.forward dot reading.forward) < SNAP_COSINE) return reading
        return SkyView.fromBasis(
            forward = previous.forward + (reading.forward - previous.forward) * SMOOTHING,
            up = previous.up + (reading.up - previous.up) * SMOOTHING,
        )
    }

    /** Magnetic north is [declinationDegrees] east of true north: azimuths grow by that much. */
    private fun toTrueNorth(vector: SkyVector, declinationDegrees: Float): SkyVector {
        val angle = Math.toRadians(declinationDegrees.toDouble())
        val c = cos(angle)
        val s = sin(angle)
        return SkyVector(vector.x * c + vector.y * s, -vector.x * s + vector.y * c, vector.z)
    }
}

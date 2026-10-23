package com.radustavila.nasaexplorer.feature.skymap

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.radustavila.nasaexplorer.core.skymap.SkyLens
import com.radustavila.nasaexplorer.core.skymap.SkyProjection
import com.radustavila.nasaexplorer.core.skymap.SkyVector
import com.radustavila.nasaexplorer.core.skymap.SkyView

/** How the map is aimed. */
enum class CameraMode {
    /** It follows where the phone points. */
    COMPASS,

    /** Drag to look around: for devices without the sensor, an emulator, or a phone on a table. */
    TOUCH,
}

/**
 * Where the map looks and how far it is zoomed. In [CameraMode.COMPASS] the sensor's view wins;
 * dragging switches to [CameraMode.TOUCH], starting from wherever the phone pointed.
 *
 * Over the camera (AR, F22) the map is drawn as the lens draws the sky: its field of view is the
 * camera's ([arFieldOfViewDegrees]) and the sensor's view is corrected by the user's [alignment].
 */
@Stable
class SkyCameraState(
    mode: CameraMode,
    azimuthDegrees: Double = DEFAULT_AZIMUTH,
    altitudeDegrees: Double = DEFAULT_ALTITUDE,
    fieldOfViewDegrees: Double = DEFAULT_FIELD_OF_VIEW,
    alignment: SkyAlignment = SkyAlignment.NONE,
) {
    var mode: CameraMode by mutableStateOf(mode)
    var azimuthDegrees: Double by mutableDoubleStateOf(azimuthDegrees)
        private set
    var altitudeDegrees: Double by mutableDoubleStateOf(altitudeDegrees)
        private set

    /** The field of view pinching chose, kept while AR locks the map to the camera's. */
    private var mapFieldOfViewDegrees: Double by mutableDoubleStateOf(fieldOfViewDegrees)

    /** The camera preview's field of view across the shorter side while AR draws over it, else null. */
    internal var arFieldOfViewDegrees: Double? by mutableStateOf(null)

    /** How far the compass is off, as the user lined it up in AR; kept until the screen goes. */
    var alignment: SkyAlignment by mutableStateOf(alignment)
        private set

    val fieldOfViewDegrees: Double get() = arFieldOfViewDegrees ?: mapFieldOfViewDegrees

    /** Rectilinear over the camera, so the drawn sky matches the picture to its corners. */
    val lens: SkyLens get() = if (arFieldOfViewDegrees != null) SkyLens.RECTILINEAR else SkyLens.STEREOGRAPHIC

    /** The latest view from the rotation sensor, set by the screen while in compass mode. */
    internal var sensorView: SkyView? by mutableStateOf(null)

    /**
     * The view to draw: the sensor's in compass mode once it has read (aligned in AR), else the
     * touch angles.
     */
    val view: SkyView
        get() {
            val sensor = sensorView.takeIf { mode == CameraMode.COMPASS }
                ?: return SkyView.looking(azimuthDegrees, altitudeDegrees)
            return if (arFieldOfViewDegrees != null) alignment.applyTo(sensor) else sensor
        }

    fun projection(width: Float, height: Float): SkyProjection =
        SkyProjection(view, width, height, fieldOfViewDegrees, lens)

    /** Turns the sky so the point under the finger at ([fromX], [fromY]) follows it to ([toX], [toY]). */
    fun drag(projection: SkyProjection, fromX: Float, fromY: Float, toX: Float, toY: Float) {
        if (mode == CameraMode.COMPASS) {
            val current = projection.view
            mode = CameraMode.TOUCH
            azimuthDegrees = current.azimuthDegrees
            altitudeDegrees = current.altitudeDegrees
        }
        val from = projection.unproject(fromX, fromY).toHorizontal()
        val to = projection.unproject(toX, toY).toHorizontal()
        azimuthDegrees = (azimuthDegrees + shortestTurn(to.azimuthDegrees, from.azimuthDegrees)).mod(FULL_TURN)
        val climb = from.altitudeDegrees - to.altitudeDegrees
        altitudeDegrees = (altitudeDegrees + climb).coerceIn(-MAX_ALTITUDE, MAX_ALTITUDE)
    }

    /** Pinch: a [zoom] above 1 narrows the view. */
    fun zoom(zoom: Float) {
        if (zoom <= 0f) return
        mapFieldOfViewDegrees = (mapFieldOfViewDegrees / zoom).coerceIn(MIN_FIELD_OF_VIEW, MAX_FIELD_OF_VIEW)
    }

    /**
     * Align mode: moves the drawn sky so what is drawn at ([fromX], [fromY]) follows the finger to
     * ([toX], [toY]) while the phone stays still, as the user drags the drawn Moon onto the real one.
     */
    fun align(projection: SkyProjection, fromX: Float, fromY: Float, toX: Float, toY: Float) {
        val grabbed = projection.unproject(fromX, fromY).toHorizontal()
        var current = projection
        // Turning and tilting interact away from the centre: a few refinements land it under the finger.
        repeat(ALIGN_STEPS) {
            val to = current.unproject(toX, toY).toHorizontal()
            alignment = alignment.turnedBy(
                azimuthDegrees = shortestTurn(to.azimuthDegrees, grabbed.azimuthDegrees),
                altitudeDegrees = grabbed.altitudeDegrees - to.altitudeDegrees,
            )
            current = projection(projection.width, projection.height)
        }
    }

    fun resetAlignment() {
        alignment = SkyAlignment.NONE
    }

    /**
     * Turns towards [direction] in touch mode, in a short glide, or at once when [animate] is false
     * (reduced motion). In compass mode the user turns the phone instead, following the arrow.
     */
    suspend fun lookAt(direction: SkyVector, animate: Boolean) {
        if (mode == CameraMode.COMPASS) return
        val target = direction.toHorizontal()
        val startAzimuth = azimuthDegrees
        val startAltitude = altitudeDegrees
        val turn = shortestTurn(startAzimuth, target.azimuthDegrees)
        val climb = target.altitudeDegrees.coerceIn(-MAX_ALTITUDE, MAX_ALTITUDE) - startAltitude
        if (!animate) {
            azimuthDegrees = (startAzimuth + turn).mod(FULL_TURN)
            altitudeDegrees = startAltitude + climb
            return
        }
        animate(0f, 1f, animationSpec = tween(LOOK_AT_MILLIS)) { fraction, _ ->
            azimuthDegrees = (startAzimuth + turn * fraction).mod(FULL_TURN)
            altitudeDegrees = startAltitude + climb * fraction
        }
    }

    companion object {
        const val DEFAULT_AZIMUTH = 180.0
        const val DEFAULT_ALTITUDE = 35.0
        const val DEFAULT_FIELD_OF_VIEW = 90.0
        const val MIN_FIELD_OF_VIEW = 8.0
        const val MAX_FIELD_OF_VIEW = 150.0
        private const val MAX_ALTITUDE = 89.9
        private const val FULL_TURN = 360.0
        private const val HALF_TURN = 180.0
        private const val LOOK_AT_MILLIS = 700
        private const val ALIGN_STEPS = 3

        /** Degrees to turn from [from] to [to] the short way, -180 to 180. */
        internal fun shortestTurn(from: Double, to: Double): Double = (to - from + HALF_TURN).mod(FULL_TURN) - HALF_TURN

        val Saver = listSaver<SkyCameraState, Any>(
            save = {
                listOf(
                    it.mode.name,
                    it.azimuthDegrees,
                    it.altitudeDegrees,
                    it.mapFieldOfViewDegrees,
                    it.alignment.azimuthDegrees,
                    it.alignment.altitudeDegrees,
                )
            },
            restore = {
                SkyCameraState(
                    mode = CameraMode.valueOf(it[0] as String),
                    azimuthDegrees = it[1] as Double,
                    altitudeDegrees = it[2] as Double,
                    fieldOfViewDegrees = it[3] as Double,
                    alignment = SkyAlignment(it[4] as Double, it[5] as Double),
                )
            },
        )
    }
}

@Composable
fun rememberSkyCameraState(initialMode: CameraMode): SkyCameraState =
    rememberSaveable(saver = SkyCameraState.Saver) { SkyCameraState(initialMode) }

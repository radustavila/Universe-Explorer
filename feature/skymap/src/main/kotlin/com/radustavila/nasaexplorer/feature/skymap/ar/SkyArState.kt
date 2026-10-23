package com.radustavila.nasaexplorer.feature.skymap.ar

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.radustavila.nasaexplorer.core.skymap.SkyVector
import java.time.Duration
import kotlin.math.cos

/** Why AR is off although the user asked for it, each with its own message. */
enum class ArProblem {
    NO_ROTATION_SENSOR,
    NO_BACK_CAMERA,
    CAMERA_PERMISSION_DENIED,

    /** The camera would not open: another app holds it, or it failed. */
    CAMERA_FAILED,

    /** Two minutes without moving: the phone was put down, so the camera is off to save the battery. */
    TURNED_OFF_WHILE_STILL,
}

/**
 * What the running camera reports: the angle its preview spans across the shorter side of the
 * screen at zoom 1, and the zoom ratios pinching may use.
 */
@Immutable
data class ArLens(
    val fieldOfViewDegrees: Double,
    val minZoomRatio: Float = 1f,
    val maxZoomRatio: Float = 1f,
)

/**
 * The AR switch of the sky map (F22): whether the camera fills the background, whether the user is
 * aligning the drawn sky with the real one, the camera's lens once it runs and the zoom. The camera
 * itself is a slot of the screen, so tests show a stand-in that reports a lens.
 */
@Stable
class SkyArState(isOn: Boolean = false, zoomRatio: Float = 1f) {

    var isOn: Boolean by mutableStateOf(isOn)
        private set

    /** Drags move the drawn sky against the camera instead of being ignored. */
    var isAligning: Boolean by mutableStateOf(false)

    /** Why AR just switched off or would not switch on; shown until dismissed. */
    var problem: ArProblem? by mutableStateOf(null)
        private set

    /** Null until the camera runs. */
    var lens: ArLens? by mutableStateOf(null)
        private set

    var zoomRatio: Float by mutableFloatStateOf(zoomRatio)
        private set

    /** The preview's angle across the shorter side of the screen, zoom included; null until the camera runs. */
    val fieldOfViewDegrees: Double?
        get() = lens?.let { CameraFieldOfView.zoomed(it.fieldOfViewDegrees, zoomRatio.toDouble()) }

    private val stillness = StillnessDetector()

    fun turnOn(nowMillis: Long) {
        isOn = true
        problem = null
        stillness.reset(nowMillis)
    }

    /** Switches AR off, saying why when it was not the user's choice. */
    fun turnOff(problem: ArProblem? = null) {
        isOn = false
        isAligning = false
        lens = null
        this.problem = problem
    }

    fun dismissProblem() {
        problem = null
    }

    fun onCameraReady(lens: ArLens) {
        this.lens = lens
        zoomRatio = zoomRatio.coerceIn(lens.minZoomRatio, lens.maxZoomRatio)
    }

    /** Pinch: camera and map zoom together, within what the camera allows. */
    fun zoom(zoom: Float) {
        val lens = lens ?: return
        if (zoom <= 0f) return
        zoomRatio = (zoomRatio * zoom).coerceIn(lens.minZoomRatio, lens.maxZoomRatio)
    }

    /** Each sensor reading, with where the phone points. */
    fun onView(forward: SkyVector, nowMillis: Long) {
        stillness.onView(forward, nowMillis)
    }

    /** True once the phone has not moved for [StillnessDetector.TIMEOUT]. */
    fun isStill(nowMillis: Long): Boolean = stillness.isStill(nowMillis)

    companion object {
        val Saver = listSaver<SkyArState, Any>(
            save = { listOf(it.isOn, it.zoomRatio) },
            restore = { SkyArState(isOn = it[0] as Boolean, zoomRatio = it[1] as Float) },
        )
    }
}

@Composable
fun rememberSkyArState(): SkyArState = rememberSaveable(saver = SkyArState.Saver) { SkyArState() }

/**
 * Tells a phone held up (which always shakes a little) from one put down: the phone counts as moved
 * once it points more than [THRESHOLD_DEGREES] away from where it pointed when it last moved.
 */
internal class StillnessDetector {

    private var anchor: SkyVector? = null

    /** Null until the first reading or [reset], so a state restored after the app was away never starts still. */
    private var movedAtMillis: Long? = null

    fun reset(nowMillis: Long) {
        anchor = null
        movedAtMillis = nowMillis
    }

    fun onView(forward: SkyVector, nowMillis: Long) {
        val last = anchor
        if (last == null || (last dot forward) < THRESHOLD_COSINE) {
            anchor = forward
            movedAtMillis = nowMillis
        }
    }

    fun isStill(nowMillis: Long): Boolean = movedAtMillis?.let { nowMillis - it >= TIMEOUT.toMillis() } ?: false

    companion object {
        val TIMEOUT: Duration = Duration.ofMinutes(2)
        const val THRESHOLD_DEGREES = 1.0
        private val THRESHOLD_COSINE = cos(Math.toRadians(THRESHOLD_DEGREES))
    }
}

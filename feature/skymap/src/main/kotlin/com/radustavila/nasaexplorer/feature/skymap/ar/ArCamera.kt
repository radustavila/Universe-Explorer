package com.radustavila.nasaexplorer.feature.skymap.ar

import android.content.Context
import android.hardware.camera2.CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS
import android.hardware.camera2.CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE
import android.hardware.camera2.CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE
import android.hardware.camera2.CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE
import androidx.annotation.OptIn
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.compose.CameraXViewfinder
import androidx.camera.core.Camera
import androidx.camera.core.CameraInfoUnavailableException
import androidx.camera.core.CameraSelector
import androidx.camera.core.CameraState
import androidx.camera.core.InitializationException
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceRequest
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.radustavila.nasaexplorer.feature.skymap.ar.CameraFieldOfView.shorterSide
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

/**
 * The back camera's live picture, filling [modifier] and cropping the overflow, for AR. The picture
 * only goes to the screen: nothing is recorded, analysed or saved. The camera opens while this is
 * composed and the screen is started, and is released when it leaves composition.
 *
 * Reports the preview's field of view to [ar] once it runs, applies [SkyArState.zoomRatio], and
 * switches AR off with a reason when there is no back camera or it fails.
 */
@Composable
internal fun ArCameraPreview(ar: SkyArState, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var surfaceRequest by remember { mutableStateOf<SurfaceRequest?>(null) }
    var viewSize by remember { mutableStateOf(IntSize.Zero) }
    LaunchedEffect(context, lifecycleOwner, ar) {
        val preview = Preview.Builder().build()
        preview.setSurfaceProvider { surfaceRequest = it }
        val (provider, camera) = when (val opened = openBackCamera(context, lifecycleOwner, preview)) {
            is OpenedCamera.Failed -> return@LaunchedEffect ar.turnOff(opened.problem)
            is OpenedCamera.Running -> opened.provider to opened.camera
        }
        val failures = Observer<CameraState> { state ->
            if (state.error?.isFatal() == true) ar.turnOff(ArProblem.CAMERA_FAILED)
        }
        camera.cameraInfo.cameraState.observe(lifecycleOwner, failures)
        try {
            launch { snapshotFlow { ar.zoomRatio }.collect { camera.cameraControl.setZoomRatio(it) } }
            launch {
                snapshotFlow { viewSize to surfaceRequest }
                    .filter { (size, request) -> size != IntSize.Zero && request != null }
                    .collect { (size, request) ->
                        val rotation = ContextCompat.getDisplayOrDefault(context).rotation
                        preview.targetRotation = rotation
                        ar.onCameraReady(camera.lens(checkNotNull(request).resolution, rotation, size))
                    }
            }
            awaitCancellation()
        } finally {
            camera.cameraInfo.cameraState.removeObserver(failures)
            provider.unbind(preview)
            surfaceRequest = null
        }
    }
    surfaceRequest?.let { request ->
        CameraXViewfinder(surfaceRequest = request, modifier = modifier.onSizeChanged { viewSize = it })
    }
}

private sealed interface OpenedCamera {
    class Running(val provider: ProcessCameraProvider, val camera: Camera) : OpenedCamera
    class Failed(val problem: ArProblem) : OpenedCamera
}

private suspend fun openBackCamera(context: Context, owner: LifecycleOwner, preview: Preview): OpenedCamera = try {
    val provider = ProcessCameraProvider.awaitInstance(context)
    if (provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)) {
        OpenedCamera.Running(provider, provider.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview))
    } else {
        OpenedCamera.Failed(ArProblem.NO_BACK_CAMERA)
    }
} catch (_: IllegalArgumentException) {
    OpenedCamera.Failed(ArProblem.NO_BACK_CAMERA)
} catch (_: CameraInfoUnavailableException) {
    OpenedCamera.Failed(ArProblem.CAMERA_FAILED)
} catch (_: InitializationException) {
    OpenedCamera.Failed(ArProblem.CAMERA_FAILED)
} catch (_: IllegalStateException) {
    OpenedCamera.Failed(ArProblem.CAMERA_FAILED)
}

/** Errors CameraX does not recover from by itself, or that leave the user looking at black. */
private fun CameraState.StateError.isFatal(): Boolean =
    type == CameraState.ErrorType.CRITICAL ||
        code == CameraState.ERROR_CAMERA_IN_USE ||
        code == CameraState.ERROR_MAX_CAMERAS_IN_USE

/**
 * Pinching zooms from the main lens (a wider one would bend the picture away from the drawn sky)
 * up to this ratio, past which digital zoom only blurs.
 */
private const val MAX_ZOOM_RATIO = 8f

/**
 * The preview's field of view for a [view]-sized screen at display [rotation], from the lens and
 * sensor Camera2 reports for this camera and the [stream] size CameraX chose.
 */
@OptIn(markerClass = [ExperimentalCamera2Interop::class])
private fun Camera.lens(stream: android.util.Size, rotation: Int, view: IntSize): ArLens {
    val info = Camera2CameraInfo.from(cameraInfo)
    val lens = run {
        val focalLength = info.getCameraCharacteristic(LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.firstOrNull()
            ?: return@run null
        val physical = info.getCameraCharacteristic(SENSOR_INFO_PHYSICAL_SIZE) ?: return@run null
        val pixels = info.getCameraCharacteristic(SENSOR_INFO_PIXEL_ARRAY_SIZE) ?: return@run null
        val active = info.getCameraCharacteristic(SENSOR_INFO_ACTIVE_ARRAY_SIZE) ?: return@run null
        CameraLens.fromSensor(
            focalLengthMillimetres = focalLength.toDouble(),
            physicalWidthMillimetres = physical.width.toDouble(),
            physicalHeightMillimetres = physical.height.toDouble(),
            pixelArrayWidth = pixels.width,
            pixelArrayHeight = pixels.height,
            activeWidth = active.width(),
            activeHeight = active.height(),
        )
    } ?: TYPICAL_LENS
    val fieldOfView = CameraFieldOfView.preview(
        lens = lens,
        streamWidth = stream.width,
        streamHeight = stream.height,
        rotationDegrees = cameraInfo.getSensorRotationDegrees(rotation),
        viewWidth = view.width,
        viewHeight = view.height,
    )
    val zoom = cameraInfo.zoomState.value
    val minZoom = maxOf(1f, zoom?.minZoomRatio ?: 1f)
    return ArLens(
        fieldOfViewDegrees = fieldOfView.shorterSide(view.width, view.height),
        minZoomRatio = minZoom,
        maxZoomRatio = (zoom?.maxZoomRatio ?: 1f).coerceIn(minZoom, MAX_ZOOM_RATIO),
    )
}

/** A common main camera (4.4 mm on a 1/2.55" sensor), for the rare device that reports neither. */
private val TYPICAL_LENS = CameraLens(
    focalLengthMillimetres = 4.38,
    activeWidthMillimetres = 5.64,
    activeHeightMillimetres = 4.23,
)

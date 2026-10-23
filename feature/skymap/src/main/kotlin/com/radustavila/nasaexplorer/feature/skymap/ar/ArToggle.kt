package com.radustavila.nasaexplorer.feature.skymap.ar

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.SystemClock
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/**
 * What the AR switch does: off when on; otherwise checks for the rotation sensor and a camera,
 * asks for the camera permission the first time (and only here), then switches AR on and calls
 * [onTurnedOn]. Each missing piece leaves AR off with its [ArProblem].
 */
@Composable
internal fun rememberArToggle(ar: SkyArState, hasRotationSensor: Boolean, onTurnedOn: () -> Unit): () -> Unit {
    val context = LocalContext.current
    val currentOnTurnedOn by rememberUpdatedState(onTurnedOn)
    val turnOn = {
        ar.turnOn(SystemClock.elapsedRealtime())
        currentOnTurnedOn()
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) turnOn() else ar.turnOff(ArProblem.CAMERA_PERMISSION_DENIED)
    }
    LaunchedEffect(ar) {
        // Restored on after the app was away: the permission may have been taken back meanwhile.
        if (ar.isOn && !context.hasCameraPermission()) ar.turnOff()
    }
    return {
        when {
            ar.isOn -> ar.turnOff()
            !hasRotationSensor -> ar.turnOff(ArProblem.NO_ROTATION_SENSOR)
            // Whether one faces backwards only CameraX can tell, once the permission allows it to look.
            !context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY) ->
                ar.turnOff(ArProblem.NO_BACK_CAMERA)
            context.hasCameraPermission() -> turnOn()
            else -> permission.launch(Manifest.permission.CAMERA)
        }
    }
}

private fun Context.hasCameraPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

/** The app's page in system settings, where a refused permission can still be allowed. */
internal fun Context.openAppSettings() {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        // A device without that page: the message already says where to look.
    }
}

package com.radustavila.nasaexplorer.core.designsystem.notification

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.radustavila.nasaexplorer.core.designsystem.R
import kotlinx.coroutines.launch

/** What a notification switch shows, and what tapping it does. */
@Immutable
class NotificationPermissionToggle(
    val isOn: Boolean,
    val onClick: () -> Unit,
)

/**
 * Reconciles the user's in-app choice for one kind of notification with what the system allows.
 * Every notification switch in the app (new picture, aurora alerts, ISS reminders, Settings) uses it.
 *
 * The switch only reads as "on" when both agree, because notifications can be blocked in system
 * settings at any time behind the app's back. Turning it on asks for the runtime permission where
 * the platform has one (Android 13+); when the system refuses - denied now, or blocked for good -
 * a snackbar with [blockedMessage] points to the app's notification settings, the only place left
 * to change it.
 */
@Composable
fun rememberNotificationPermissionToggle(
    isEnabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    snackbarHostState: SnackbarHostState,
    blockedMessage: String = stringResource(R.string.notifications_blocked),
): NotificationPermissionToggle {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isAllowedBySystem by remember { mutableStateOf(context.areNotificationsAllowed()) }

    LifecycleResumeEffect(context) {
        isAllowedBySystem = context.areNotificationsAllowed()
        onPauseOrDispose { }
    }

    val settingsLabel = stringResource(R.string.notifications_open_settings)
    val explainBlocked: () -> Unit = {
        scope.launch {
            val result = snackbarHostState.showSnackbar(
                message = blockedMessage,
                actionLabel = settingsLabel,
                duration = SnackbarDuration.Long,
            )
            if (result == SnackbarResult.ActionPerformed) context.openAppNotificationSettings()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { isGranted ->
        isAllowedBySystem = isGranted
        if (isGranted) onEnabledChange(true) else explainBlocked()
    }

    val isOn = isEnabled && isAllowedBySystem
    return NotificationPermissionToggle(isOn = isOn) {
        when {
            isOn -> onEnabledChange(false)
            isAllowedBySystem -> onEnabledChange(true)
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)

            else -> explainBlocked()
        }
    }
}

private fun Context.areNotificationsAllowed(): Boolean =
    NotificationManagerCompat.from(this).areNotificationsEnabled()

private fun Context.openAppNotificationSettings() {
    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
    try {
        startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        // A stripped-down system image without a settings app; there is nowhere else to send the user.
    }
}

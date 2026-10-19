package com.radustavila.nasaexplorer.feature.spaceweather.components

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.radustavila.nasaexplorer.core.model.AuroraAlertSettings
import com.radustavila.nasaexplorer.core.model.AuroraVisibility
import com.radustavila.nasaexplorer.core.model.GeomagneticStorm
import com.radustavila.nasaexplorer.feature.spaceweather.R
import java.util.Locale
import kotlinx.coroutines.launch

const val AURORA_ALERT_SWITCH_TEST_TAG = "aurora-alert-switch"
const val AURORA_ALERT_LOCATION_TEST_TAG = "aurora-alert-location"
const val AURORA_LOCATION_LATITUDE_TEST_TAG = "aurora-location-latitude"
const val AURORA_LOCATION_LONGITUDE_TEST_TAG = "aurora-location-longitude"
const val AURORA_LOCATION_SAVE_TEST_TAG = "aurora-location-save"

/**
 * Opt-in aurora alerts. The switch reads "on" only when the user chose alerts and the system lets
 * the app notify; turning it on asks for the notification permission where there is one, and for a
 * place first when none is saved. The place decides the Kp that triggers an alert.
 */
@Composable
internal fun AuroraAlertCard(
    settings: AuroraAlertSettings,
    onEnabledChange: (Boolean) -> Unit,
    onEditLocation: (enableAlertsAfter: Boolean) -> Unit,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    val toggle = rememberNotificationPermissionToggle(
        isEnabled = settings.enabled,
        onEnabledChange = { enabled ->
            if (enabled && !settings.hasLocation) onEditLocation(true) else onEnabledChange(enabled)
        },
        snackbarHostState = snackbarHostState,
    )

    Card(modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Outlined.NotificationsActive, contentDescription = null)
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.space_weather_alerts_title), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(R.string.space_weather_alerts_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = toggle.isOn,
                    onCheckedChange = { toggle.onClick() },
                    modifier = Modifier.testTag(AURORA_ALERT_SWITCH_TEST_TAG),
                )
            }
            Text(
                text = locationSummary(settings),
                style = MaterialTheme.typography.bodyMedium,
            )
            TextButton(
                onClick = { onEditLocation(false) },
                modifier = Modifier.testTag(AURORA_ALERT_LOCATION_TEST_TAG),
            ) {
                Text(
                    stringResource(
                        if (settings.hasLocation) R.string.space_weather_alerts_change_place else R.string.space_weather_alerts_set_place,
                    ),
                )
            }
            Text(
                text = stringResource(R.string.space_weather_alerts_location_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun locationSummary(settings: AuroraAlertSettings): String {
    val latitude = settings.latitude
    val longitude = settings.longitude
    if (latitude == null || longitude == null) return stringResource(R.string.space_weather_alerts_no_place)
    val place = formatPlace(latitude, longitude, currentLocale())
    return when (val kp = settings.minimumKp) {
        null -> stringResource(R.string.space_weather_alerts_place_unreachable, place)
        else -> stringResource(R.string.space_weather_alerts_place_threshold, place, thresholdLabel(kp))
    }
}

@Composable
private fun thresholdLabel(kp: Int): String =
    GeomagneticStorm.forLevel(kp)?.let { stringResource(R.string.space_weather_kp_with_storm, kp, it.name) }
        ?: stringResource(R.string.space_weather_kp_value, kp)

internal fun formatPlace(latitude: Double, longitude: Double, locale: Locale): String {
    val ns = if (latitude >= 0) "N" else "S"
    val ew = if (longitude >= 0) "E" else "W"
    return String.format(locale, "%.1f° %s, %.1f° %s", kotlin.math.abs(latitude), ns, kotlin.math.abs(longitude), ew)
}

/** Latitude and longitude typed into the location dialog, once both are valid. */
internal fun parsePlace(latitude: String, longitude: String): Pair<Double, Double>? {
    val lat = latitude.toCoordinate()?.takeIf { it in -90.0..90.0 } ?: return null
    val lon = longitude.toCoordinate()?.takeIf { it in -180.0..180.0 } ?: return null
    return lat to lon
}

/** Accepts a decimal comma too, as many keyboards offer one. */
private fun String.toCoordinate(): Double? = trim().replace(',', '.').toDoubleOrNull()

/**
 * Asks for latitude and longitude in decimal degrees. The screen shows it outside its list: a
 * dialog opened from inside a lazy list item kept Compose busy forever under Robolectric.
 */
@Composable
internal fun AuroraLocationDialog(
    initialLatitude: Double?,
    initialLongitude: Double?,
    onDismiss: () -> Unit,
    onConfirm: (latitude: Double, longitude: Double) -> Unit,
) {
    var latitudeText by rememberSaveable { mutableStateOf(initialLatitude?.toString().orEmpty()) }
    var longitudeText by rememberSaveable { mutableStateOf(initialLongitude?.toString().orEmpty()) }
    val place = parsePlace(latitudeText, longitudeText)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.space_weather_location_title)) },
        text = {
            AuroraLocationFields(
                latitude = latitudeText,
                longitude = longitudeText,
                onLatitudeChange = { latitudeText = it },
                onLongitudeChange = { longitudeText = it },
            )
        },
        confirmButton = {
            TextButton(
                onClick = { place?.let { (latitude, longitude) -> onConfirm(latitude, longitude) } },
                enabled = place != null,
                modifier = Modifier.testTag(AURORA_LOCATION_SAVE_TEST_TAG),
            ) { Text(stringResource(R.string.space_weather_location_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.space_weather_location_cancel)) }
        },
    )
}

/** The dialog's fields, with the Kp the typed place needs shown as soon as it is valid. */
@Composable
internal fun AuroraLocationFields(
    latitude: String,
    longitude: String,
    onLatitudeChange: (String) -> Unit,
    onLongitudeChange: (String) -> Unit,
) {
    val place = parsePlace(latitude, longitude)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.space_weather_location_description), style = MaterialTheme.typography.bodyMedium)
        OutlinedTextField(
            value = latitude,
            onValueChange = onLatitudeChange,
            label = { Text(stringResource(R.string.space_weather_location_latitude)) },
            isError = latitude.isNotBlank() && parsePlace(latitude, "0") == null,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.testTag(AURORA_LOCATION_LATITUDE_TEST_TAG),
        )
        OutlinedTextField(
            value = longitude,
            onValueChange = onLongitudeChange,
            label = { Text(stringResource(R.string.space_weather_location_longitude)) },
            isError = longitude.isNotBlank() && parsePlace("0", longitude) == null,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.testTag(AURORA_LOCATION_LONGITUDE_TEST_TAG),
        )
        if (place != null) {
            val kp = AuroraVisibility.minimumKp(place.first, place.second)
            Text(
                text = if (kp == null) {
                    stringResource(R.string.space_weather_location_unreachable)
                } else {
                    stringResource(R.string.space_weather_location_threshold, thresholdLabel(kp))
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

private class NotificationPermissionToggle(val isOn: Boolean, val onClick: () -> Unit)

/** Same reconciliation as the feed's new-picture bell: the user's choice and the system's say. */
@Composable
private fun rememberNotificationPermissionToggle(
    isEnabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    snackbarHostState: SnackbarHostState,
): NotificationPermissionToggle {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isAllowedBySystem by remember { mutableStateOf(context.areNotificationsAllowed()) }

    LifecycleResumeEffect(context) {
        isAllowedBySystem = context.areNotificationsAllowed()
        onPauseOrDispose { }
    }

    val blockedMessage = stringResource(R.string.space_weather_notifications_blocked)
    val settingsLabel = stringResource(R.string.space_weather_notifications_open_settings)
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

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
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

private fun Context.areNotificationsAllowed(): Boolean = NotificationManagerCompat.from(this).areNotificationsEnabled()

private fun Context.openAppNotificationSettings() {
    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
    try {
        startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        // No settings app on this system image; there is nowhere else to send the user.
    }
}

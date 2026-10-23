package com.radustavila.nasaexplorer.feature.skymap.ar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.OpenWith
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.radustavila.nasaexplorer.feature.skymap.R
import com.radustavila.nasaexplorer.feature.skymap.SkyAlignment
import com.radustavila.nasaexplorer.feature.skymap.SkyCameraState
import com.radustavila.nasaexplorer.feature.skymap.translucentCardColors

const val SKY_MAP_ALIGN_BUTTON_TEST_TAG = "sky-map-align"

/**
 * Takes the time bar's place while AR is on: how the camera is doing, and Align, which lets the
 * user drag the drawn sky onto the real one when the compass is off.
 */
@Composable
internal fun ArBar(ar: SkyArState, camera: SkyCameraState) {
    Card(colors = translucentCardColors(), modifier = Modifier.fillMaxWidth()) {
        if (ar.isAligning) {
            Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 4.dp)) {
                Text(stringResource(R.string.sky_map_ar_align_hint), style = MaterialTheme.typography.bodyMedium)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = camera::resetAlignment,
                        enabled = camera.alignment != SkyAlignment.NONE,
                    ) { Text(stringResource(R.string.sky_map_ar_align_reset)) }
                    Button(onClick = { ar.isAligning = false }) { Text(stringResource(R.string.sky_map_ar_align_done)) }
                }
            }
        } else {
            Row(
                modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val status = when {
                    ar.lens == null -> R.string.sky_map_ar_starting
                    camera.alignment != SkyAlignment.NONE -> R.string.sky_map_ar_aligned
                    else -> R.string.sky_map_ar_live
                }
                Text(
                    text = stringResource(status),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    onClick = { ar.isAligning = true },
                    enabled = ar.lens != null,
                    modifier = Modifier.testTag(SKY_MAP_ALIGN_BUTTON_TEST_TAG),
                ) {
                    Icon(Icons.Outlined.OpenWith, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                    Text(stringResource(R.string.sky_map_ar_align))
                }
            }
        }
    }
}

/** Why AR is off, and what the user can do about it: allow the camera, try again or turn it back on. */
@Composable
internal fun ArProblemCard(
    problem: ArProblem,
    onTurnOn: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val (message, action) = when (problem) {
        ArProblem.NO_ROTATION_SENSOR -> R.string.sky_map_ar_no_sensor to null
        ArProblem.NO_BACK_CAMERA -> R.string.sky_map_ar_no_camera to null
        ArProblem.CAMERA_PERMISSION_DENIED ->
            R.string.sky_map_ar_permission_denied to R.string.sky_map_ar_open_settings
        ArProblem.CAMERA_FAILED -> R.string.sky_map_ar_camera_failed to R.string.sky_map_ar_try_again
        ArProblem.TURNED_OFF_WHILE_STILL -> R.string.sky_map_ar_turned_off_still to R.string.sky_map_ar_turn_on
    }
    Card(colors = translucentCardColors(), modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                Text(stringResource(message), style = MaterialTheme.typography.bodyMedium)
                action?.let {
                    TextButton(
                        onClick = {
                            onDismiss()
                            if (problem == ArProblem.CAMERA_PERMISSION_DENIED) context.openAppSettings() else onTurnOn()
                        },
                    ) { Text(stringResource(it)) }
                }
            }
            IconButton(onClick = onDismiss) {
                Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.sky_map_ar_dismiss))
            }
        }
    }
}

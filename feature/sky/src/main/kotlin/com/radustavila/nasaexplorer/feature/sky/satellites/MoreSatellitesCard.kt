package com.radustavila.nasaexplorer.feature.sky.satellites

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.radustavila.nasaexplorer.core.designsystem.notification.NotificationPermissionToggle
import com.radustavila.nasaexplorer.core.designsystem.notification.rememberNotificationPermissionToggle
import com.radustavila.nasaexplorer.core.domain.sky.SatelliteSetStatus
import com.radustavila.nasaexplorer.core.domain.sky.UpcomingPass
import com.radustavila.nasaexplorer.core.domain.usecase.PICKABLE_SATELLITE_SETS
import com.radustavila.nasaexplorer.core.domain.usecase.REMINDABLE_SATELLITE_SETS
import com.radustavila.nasaexplorer.core.map.MapCamera
import com.radustavila.nasaexplorer.core.map.SpaceMap
import com.radustavila.nasaexplorer.core.map.rememberSpaceMapState
import com.radustavila.nasaexplorer.core.model.SatelliteSet
import com.radustavila.nasaexplorer.feature.sky.R
import com.radustavila.nasaexplorer.feature.sky.component.IssTrackMapSlot
import com.radustavila.nasaexplorer.feature.sky.component.SkySection
import com.radustavila.nasaexplorer.feature.sky.component.compassShortRes
import com.radustavila.nasaexplorer.feature.sky.component.formatDay
import com.radustavila.nasaexplorer.feature.sky.component.formatDecimal
import com.radustavila.nasaexplorer.feature.sky.component.formatTime
import com.radustavila.nasaexplorer.feature.sky.component.groundTrackOverlay
import com.radustavila.nasaexplorer.feature.sky.component.observerOverlay
import com.radustavila.nasaexplorer.feature.sky.component.stationOverlay
import com.radustavila.nasaexplorer.feature.sky.component.toGeoPoint
import kotlin.math.roundToInt

const val SKY_SATELLITES_CARD_TEST_TAG = "sky-satellites-card"
const val SKY_SATELLITES_PASS_TEST_TAG = "sky-satellites-pass"
const val SKY_SATELLITES_TRACK_TEST_TAG = "sky-satellites-track"

fun skySatelliteChipTestTag(set: SatelliteSet) = "sky-satellites-chip-${set.name}"

fun skySatelliteReminderTestTag(set: SatelliteSet) = "sky-satellites-reminder-${set.name}"

/** About the size of a pass's ground track (a few thousand kilometres) in a card-wide map. */
private const val PASS_MAP_ZOOM = 3.0
private const val MAP_ASPECT_RATIO = 1.6f
private const val MAX_PASSES = 10

/**
 * Sky tonight's "More satellites" card with its own state; [snackbarHostState] is the screen's,
 * for the reminder switches' "notifications are blocked" message.
 */
@Composable
fun MoreSatellitesSection(
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
    viewModel: MoreSatellitesViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val blocked = stringResource(R.string.sky_iss_notifications_blocked)
    val toggles = REMINDABLE_SATELLITE_SETS.filter { it in uiState.enabledSets }.associateWith { set ->
        key(set) {
            rememberNotificationPermissionToggle(
                isEnabled = set in uiState.reminders,
                onEnabledChange = { enabled -> viewModel.onReminderChange(set, enabled) },
                snackbarHostState = snackbarHostState,
                blockedMessage = blocked,
            )
        }
    }
    MoreSatellitesCard(
        uiState = uiState,
        reminders = toggles,
        map = { overlays, focus, mapModifier ->
            val state = rememberSpaceMapState(MapCamera(center = checkNotNull(focus), zoom = PASS_MAP_ZOOM))
            SpaceMap(imagery = null, state = state, overlays = overlays, modifier = mapModifier)
        },
        onSetToggle = viewModel::onSetToggle,
        onPassClick = viewModel::onPassClick,
        modifier = modifier,
    )
}

/**
 * Chips to pick the sets, their visible passes merged and soonest first (a tap opens the pass's
 * ground track), why a picked set has none, and a reminder switch per set that has reminders.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MoreSatellitesCard(
    uiState: MoreSatellitesUiState,
    reminders: Map<SatelliteSet, NotificationPermissionToggle>,
    map: IssTrackMapSlot,
    onSetToggle: (SatelliteSet, Boolean) -> Unit,
    onPassClick: (UpcomingPass) -> Unit,
    modifier: Modifier = Modifier,
) {
    SkySection(
        title = stringResource(R.string.sky_satellites_title),
        modifier = modifier.testTag(SKY_SATELLITES_CARD_TEST_TAG),
    ) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PICKABLE_SATELLITE_SETS.forEach { set ->
                val selected = set in uiState.enabledSets
                FilterChip(
                    selected = selected,
                    onClick = { onSetToggle(set, !selected) },
                    label = { Text(stringResource(set.labelRes())) },
                    modifier = Modifier.testTag(skySatelliteChipTestTag(set)),
                )
            }
        }
        val forecast = uiState.forecast
        when {
            uiState.enabledSets.none { it in PICKABLE_SATELLITE_SETS } ->
                SupportingText(stringResource(R.string.sky_satellites_pick))
            forecast == null -> {
                SupportingText(stringResource(R.string.sky_satellites_computing))
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            else -> {
                PICKABLE_SATELLITE_SETS.forEach { set ->
                    val status = forecast.setStatus[set] ?: return@forEach
                    val hasPasses = forecast.passes.any { it.satellite.set == set }
                    setStatusText(set, status, hasPasses)?.let { SupportingText(it) }
                }
                forecast.passes.take(MAX_PASSES).forEachIndexed { index, upcoming ->
                    if (index > 0) HorizontalDivider()
                    PassRow(upcoming, onClick = { onPassClick(upcoming) })
                    val selected = uiState.selected
                    if (selected?.pass == upcoming) key(upcoming) { PassTrack(selected, uiState, map) }
                }
            }
        }
        if (reminders.isNotEmpty()) HorizontalDivider()
        reminders.forEach { (set, toggle) -> ReminderRow(set, toggle) }
    }
}

@Composable
private fun PassRow(upcoming: UpcomingPass, onClick: () -> Unit) {
    val pass = upcoming.pass
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp)
            .semantics(mergeDescendants = true) {}
            .testTag(SKY_SATELLITES_PASS_TEST_TAG),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(text = upcoming.title(), style = MaterialTheme.typography.titleSmall)
        Text(
            text = stringResource(
                R.string.sky_iss_pass_time,
                pass.start.formatDay(),
                pass.start.formatTime(),
                pass.end.formatTime(),
            ),
            style = MaterialTheme.typography.bodyMedium,
        )
        val details = stringResource(
            R.string.sky_iss_pass_details,
            pass.maxElevationDegrees.roundToInt(),
            stringResource(compassShortRes(pass.startAzimuthDegrees)),
            stringResource(compassShortRes(pass.endAzimuthDegrees)),
        )
        val magnitude = upcoming.satellite.typicalMagnitude?.let {
            stringResource(R.string.sky_satellites_magnitude, it.formatDecimal(1))
        }
        Text(
            text = listOfNotNull(details, magnitude).joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** The ground track under the pass, the observer, and the satellite at its highest. */
@Composable
private fun PassTrack(selected: SelectedPass, uiState: MoreSatellitesUiState, map: IssTrackMapSlot) {
    val track = selected.track
    if (track.isEmpty()) {
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        return
    }
    val colors = MaterialTheme.colorScheme
    val peak = remember(track, selected.pass) {
        val peakMillis = selected.pass.pass.peak.toEpochMilli()
        track.minBy { kotlin.math.abs(it.at.toEpochMilli() - peakMillis) }
    }
    val overlays = remember(track, uiState.observer, colors) {
        listOf(
            groundTrackOverlay(track, colors.primary),
            observerOverlay(uiState.observer, colors.secondary),
            stationOverlay(peak, colors.tertiary),
        )
    }
    val description = stringResource(R.string.sky_satellites_track_description)
    map(
        overlays,
        peak.coordinate.toGeoPoint(),
        Modifier
            .fillMaxWidth()
            .aspectRatio(MAP_ASPECT_RATIO)
            .clip(MaterialTheme.shapes.medium)
            .semantics { contentDescription = description }
            .testTag(SKY_SATELLITES_TRACK_TEST_TAG),
    )
}

@Composable
private fun ReminderRow(set: SatelliteSet, toggle: NotificationPermissionToggle) {
    // The whole row toggles, not only the switch.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = toggle.isOn, role = Role.Switch, onValueChange = { toggle.onClick() })
            .testTag(skySatelliteReminderTestTag(set)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.sky_satellites_reminder, stringResource(set.labelRes())),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        Switch(checked = toggle.isOn, onCheckedChange = null)
    }
}

@Composable
private fun SupportingText(text: String) {
    Text(text = text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun UpcomingPass.title(): String {
    val train = satellite.train
    return when {
        train != null -> stringResource(
            R.string.sky_satellites_train,
            pluralStringResource(R.plurals.sky_satellites_train_size, train.satelliteCount, train.satelliteCount),
            train.launchedAt.formatDay(),
        )
        satellite.set == SatelliteSet.BRIGHT_SATELLITES -> satellite.name
        else -> stringResource(satellite.set.labelRes())
    }
}

@Composable
private fun setStatusText(set: SatelliteSet, status: SatelliteSetStatus, hasPasses: Boolean): String? {
    val label = stringResource(set.labelRes())
    return when (status) {
        SatelliteSetStatus.READY ->
            if (hasPasses) null else stringResource(R.string.sky_satellites_status_no_passes, label)
        SatelliteSetStatus.NO_DATA -> stringResource(R.string.sky_satellites_status_no_data, label)
        SatelliteSetStatus.STALE -> stringResource(R.string.sky_satellites_status_stale, label)
        SatelliteSetStatus.NO_RECENT_LAUNCH -> stringResource(R.string.sky_satellites_status_no_launch)
    }
}

private fun SatelliteSet.labelRes(): Int = when (this) {
    SatelliteSet.ISS -> R.string.sky_satellites_set_iss
    SatelliteSet.HUBBLE -> R.string.sky_satellites_set_hubble
    SatelliteSet.TIANGONG -> R.string.sky_satellites_set_tiangong
    SatelliteSet.STARLINK_TRAINS -> R.string.sky_satellites_set_starlink
    SatelliteSet.BRIGHT_SATELLITES -> R.string.sky_satellites_set_bright
}

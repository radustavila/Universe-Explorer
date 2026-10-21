package com.radustavila.nasaexplorer.feature.sky.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.radustavila.nasaexplorer.core.designsystem.units.rememberUnitFormatter
import com.radustavila.nasaexplorer.core.domain.sky.IssPassForecast
import com.radustavila.nasaexplorer.core.domain.sky.IssTrack
import com.radustavila.nasaexplorer.core.map.overlay.GeoPoint
import com.radustavila.nasaexplorer.core.map.overlay.MapOverlay
import com.radustavila.nasaexplorer.core.model.GeoCoordinate
import com.radustavila.nasaexplorer.core.model.SatellitePass
import com.radustavila.nasaexplorer.feature.sky.R
import kotlin.math.roundToInt

const val SKY_ISS_CARD_TEST_TAG = "sky-iss-card"
const val SKY_ISS_PASS_TEST_TAG = "sky-iss-pass"
const val SKY_ISS_REMINDER_SWITCH_TEST_TAG = "sky-iss-reminder-switch"
const val SKY_TRACK_CARD_TEST_TAG = "sky-track-card"

@Composable
internal fun IssPassesCard(
    forecast: IssPassForecast,
    remindersOn: Boolean,
    onRemindersClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showAll by rememberSaveable { mutableStateOf(false) }
    SkySection(
        title = stringResource(R.string.sky_iss_title),
        modifier = modifier.testTag(SKY_ISS_CARD_TEST_TAG),
        trailing = {
            FilterChip(
                selected = showAll,
                onClick = { showAll = !showAll },
                label = { Text(stringResource(R.string.sky_iss_show_all)) },
            )
        },
    ) {
        val elements = forecast.elements
        when {
            elements == null -> Text(stringResource(R.string.sky_iss_no_elements))
            forecast.isStale -> Text(stringResource(R.string.sky_iss_stale, elements.epoch.formatDay()))
            else -> {
                val shown = if (showAll) forecast.passes else forecast.passes.filter(SatellitePass::isVisible)
                if (shown.isEmpty()) {
                    Text(
                        stringResource(
                            if (forecast.passes.isEmpty()) R.string.sky_iss_no_passes else R.string.sky_iss_no_visible,
                        ),
                    )
                }
                shown.take(MAX_PASSES).forEachIndexed { index, pass ->
                    if (index > 0) HorizontalDivider()
                    PassRow(pass)
                }
                Text(
                    text = stringResource(R.string.sky_iss_elements_from, elements.epoch.formatDayAndTime()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        HorizontalDivider()
        // The whole row toggles, not only the switch.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .toggleable(value = remindersOn, role = Role.Switch, onValueChange = { onRemindersClick() })
                .testTag(SKY_ISS_REMINDER_SWITCH_TEST_TAG),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.sky_iss_reminders),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            Switch(checked = remindersOn, onCheckedChange = null)
        }
    }
}

@Composable
private fun PassRow(pass: SatellitePass) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .semantics(mergeDescendants = true) {}
            .testTag(SKY_ISS_PASS_TEST_TAG),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = stringResource(
                    R.string.sky_iss_pass_time,
                    pass.start.formatDay(),
                    pass.start.formatTime(),
                    pass.end.formatTime(),
                ),
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = stringResource(
                    R.string.sky_iss_pass_details,
                    pass.maxElevationDegrees.roundToInt(),
                    stringResource(compassShortRes(pass.startAzimuthDegrees)),
                    stringResource(compassShortRes(pass.endAzimuthDegrees)),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (pass.isVisible) {
            SuggestionChip(onClick = {}, label = { Text(stringResource(R.string.sky_iss_visible)) }, enabled = false)
        }
    }
}

/**
 * Draws the ISS overlays on a map: `SpaceMap` in the app, a stand-in in tests (MapLibre needs a
 * device). [focus] is the station's position, for the map to centre on.
 */
typealias IssTrackMapSlot = @Composable (overlays: List<MapOverlay>, focus: GeoPoint?, modifier: Modifier) -> Unit

@Composable
internal fun IssTrackCard(
    track: IssTrack?,
    observer: GeoCoordinate?,
    map: IssTrackMapSlot,
    modifier: Modifier = Modifier,
) {
    SkySection(title = stringResource(R.string.sky_track_title), modifier = modifier.testTag(SKY_TRACK_CARD_TEST_TAG)) {
        val position = track?.position
        val colors = MaterialTheme.colorScheme
        // Remembered apart: the dot moves every few seconds, the track only every few minutes, so
        // only the dot's overlay is rebuilt (and re-sent to the map) on most updates.
        val groundTrack = track?.groundTrack.orEmpty()
        val trackOverlay = remember(groundTrack, colors.primary) { groundTrackOverlay(groundTrack, colors.primary) }
        val observerOverlay = remember(observer, colors.secondary) { observerOverlay(observer, colors.secondary) }
        val stationOverlay = remember(position, colors.tertiary) { stationOverlay(position, colors.tertiary) }
        val overlays = remember(trackOverlay, observerOverlay, stationOverlay) {
            listOf(trackOverlay, observerOverlay, stationOverlay)
        }
        val description = stringResource(R.string.sky_track_map_description)
        map(
            overlays,
            position?.coordinate?.toGeoPoint(),
            Modifier
                .fillMaxWidth()
                .aspectRatio(MAP_ASPECT_RATIO)
                .clip(MaterialTheme.shapes.medium)
                .semantics { contentDescription = description },
        )
        if (position == null) {
            Text(stringResource(R.string.sky_track_unavailable), style = MaterialTheme.typography.bodyMedium)
            return@SkySection
        }
        val units = rememberUnitFormatter()
        Text(
            text = if (position.isLive) {
                stringResource(R.string.sky_track_live, units.distance(position.altitudeKm), units.speedPerHour(position.speedKmPerHour))
            } else {
                stringResource(R.string.sky_track_estimated, units.distance(position.altitudeKm))
            },
            style = MaterialTheme.typography.bodyMedium,
        )
        val sunlight = when (position.isSunlit) {
            true -> stringResource(R.string.sky_track_sunlit)
            false -> stringResource(R.string.sky_track_eclipsed)
            null -> null
        }
        Text(
            text = listOfNotNull(position.coordinate.label(), sunlight).joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private const val MAX_PASSES = 8
private const val MAP_ASPECT_RATIO = 1.6f

package com.radustavila.nasaexplorer.feature.skymap

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.radustavila.nasaexplorer.core.designsystem.units.rememberUnitFormatter
import com.radustavila.nasaexplorer.core.model.DeepSkyObject
import com.radustavila.nasaexplorer.core.model.ObservingLogEntry
import com.radustavila.nasaexplorer.core.model.SolarSystemBody
import com.radustavila.nasaexplorer.core.skymap.HorizontalSky
import com.radustavila.nasaexplorer.feature.skymap.render.SkyNames
import com.radustavila.nasaexplorer.feature.skymap.deepsky.bestMonthAndInstrument
import com.radustavila.nasaexplorer.feature.skymap.deepsky.brightnessAndSize
import com.radustavila.nasaexplorer.feature.skymap.deepsky.designationLine
import com.radustavila.nasaexplorer.feature.skymap.deepsky.formatDay
import com.radustavila.nasaexplorer.feature.skymap.deepsky.nameRes
import com.radustavila.nasaexplorer.feature.skymap.deepsky.title
import com.radustavila.nasaexplorer.feature.skymap.render.comet
import com.radustavila.nasaexplorer.feature.skymap.render.deepSkyObject
import com.radustavila.nasaexplorer.feature.skymap.render.direction
import com.radustavila.nasaexplorer.feature.skymap.render.satellite
import kotlin.math.roundToInt
import kotlin.math.roundToLong

const val SKY_MAP_SELECTION_TEST_TAG = "sky-map-selection"
const val SKY_MAP_COMET_DETAILS_TEST_TAG = "sky-map-comet-details"
const val SKY_MAP_MARK_SEEN_TEST_TAG = "sky-map-mark-seen"

private const val PERCENT = 100.0

/** Name, kind, place in the sky and brightness of the selection; a comet's opens its details. */
@Composable
internal fun SelectionCard(
    uiState: SkyMapUiState,
    selected: SkyTarget,
    names: SkyNames,
    onClose: () -> Unit,
    onCometDetails: (designation: String) -> Unit,
    onLogClick: (objectId: String) -> Unit = {},
) {
    val sky = uiState.sky ?: return
    val deepSky = (selected as? SkyTarget.DeepSky)?.let(sky::deepSkyObject)
    val details = listOfNotNull(
        position(uiState, sky, selected),
        if (deepSky != null) deepSky.brightnessAndSize(rememberUnitFormatter()) else brightness(uiState, sky, selected),
    )
    Card(
        colors = translucentCardColors(),
        modifier = Modifier.fillMaxWidth().testTag(SKY_MAP_SELECTION_TEST_TAG),
    ) {
        Row(Modifier.padding(start = 16.dp, top = 8.dp, bottom = 12.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f).padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title(uiState, sky, selected, names),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = subtitle(uiState, sky, selected),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                details.firstOrNull()?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                details.getOrNull(1)?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                if (deepSky != null) DeepSkyDetails(deepSky, uiState.observingLog[deepSky.id], onLogClick)
                if (selected is SkyTarget.Comet) {
                    TextButton(
                        onClick = { onCometDetails(selected.designation) },
                        modifier = Modifier.testTag(SKY_MAP_COMET_DETAILS_TEST_TAG),
                    ) {
                        Text(stringResource(R.string.sky_map_comet_details))
                    }
                }
            }
            IconButton(onClick = onClose) {
                Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.sky_map_close_details))
            }
        }
    }
}

@Composable
private fun title(uiState: SkyMapUiState, sky: HorizontalSky, selected: SkyTarget, names: SkyNames): String =
    when (selected) {
        is SkyTarget.StarTarget -> sky.chart.catalog.stars.getOrNull(selected.index)?.name
            ?: stringResource(R.string.sky_map_unnamed_star)
        is SkyTarget.Body -> names.bodies[selected.body].orEmpty()
        is SkyTarget.Constellation -> sky.chart.catalog.constellations.getOrNull(selected.index)?.name.orEmpty()
        is SkyTarget.Satellite -> names.satellite(selected.noradId, uiState.bodies)
        is SkyTarget.Comet -> uiState.bodies?.comet(selected.designation)?.name ?: selected.designation
        SkyTarget.GalacticCentre -> names.galacticCentre
        is SkyTarget.DeepSky -> sky.deepSkyObject(selected)?.title() ?: selected.id
    }

/** Best month, instrument and the observing log's mark (F23), with "Mark as seen" or "Edit". */
@Composable
private fun DeepSkyDetails(item: DeepSkyObject, entry: ObservingLogEntry?, onLogClick: (objectId: String) -> Unit) {
    item.designationLine()?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
    Text(item.bestMonthAndInstrument(), style = MaterialTheme.typography.bodySmall)
    if (entry != null) {
        Text(
            text = stringResource(R.string.deep_sky_seen_on, entry.observedOn.formatDay()),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = { onLogClick(item.id) }, modifier = Modifier.testTag(SKY_MAP_MARK_SEEN_TEST_TAG)) {
            Text(stringResource(if (entry == null) R.string.deep_sky_mark_seen else R.string.deep_sky_edit_mark))
        }
        item.nasaPageUrl?.let { url ->
            val uriHandler = LocalUriHandler.current
            TextButton(onClick = { uriHandler.openUri(url) }) { Text(stringResource(R.string.deep_sky_read_more)) }
        }
    }
}

/** "Star in Orion", or just the kind when the constellation is not known. */
@Composable
private fun subtitle(uiState: SkyMapUiState, sky: HorizontalSky, selected: SkyTarget): String {
    val kind = stringResource(
        (selected as? SkyTarget.DeepSky)?.let(sky::deepSkyObject)?.type?.nameRes() ?: selected.kindRes(),
    )
    val constellation = uiState.selectedConstellation
        ?.let { id -> sky.chart.catalog.constellations.firstOrNull { it.id == id }?.name }
    return constellation?.let { stringResource(R.string.sky_map_kind_in, kind, it) } ?: kind
}

/** "32° above the horizon, towards the south-east", or below it. */
@Composable
private fun position(uiState: SkyMapUiState, sky: HorizontalSky, selected: SkyTarget): String? {
    val horizontal = selected.direction(sky, uiState.bodies)?.toHorizontal() ?: return null
    val towards = stringResource(compassPointRes(horizontal.azimuthDegrees))
    return if (horizontal.altitudeDegrees >= 0.0) {
        stringResource(R.string.sky_map_position, horizontal.altitudeDegrees.roundToInt().toString(), towards)
    } else {
        stringResource(R.string.sky_map_below_horizon, towards)
    }
}

/** "Magnitude -1.4", with how much of the Moon is lit; a comet's is a prediction. */
@Composable
private fun brightness(uiState: SkyMapUiState, sky: HorizontalSky, selected: SkyTarget): String? =
    if (selected is SkyTarget.Comet) cometBrightness(uiState, selected) else bodyBrightness(uiState, sky, selected)

@Composable
private fun cometBrightness(uiState: SkyMapUiState, selected: SkyTarget.Comet): String? =
    uiState.bodies?.comet(selected.designation)?.magnitude?.let { magnitude ->
        stringResource(R.string.sky_map_predicted_magnitude, rememberUnitFormatter().number(magnitude, 1))
    }

@Composable
private fun bodyBrightness(uiState: SkyMapUiState, sky: HorizontalSky, selected: SkyTarget): String? {
    val body = (selected as? SkyTarget.Body)
        ?.let { target -> uiState.bodies?.solarSystem?.firstOrNull { it.body == target.body } }
    val magnitude = when (selected) {
        is SkyTarget.StarTarget -> sky.chart.catalog.stars.getOrNull(selected.index)?.magnitude?.toDouble()
        else -> body?.magnitude
    } ?: return null
    val text = stringResource(R.string.sky_map_magnitude, rememberUnitFormatter().number(magnitude, 1))
    val lit = body?.takeIf { it.body == SolarSystemBody.MOON }
        ?.let { stringResource(R.string.sky_map_moon_lit, (it.illuminatedFraction * PERCENT).roundToLong().toString()) }
    return listOfNotNull(text, lit).joinToString(" · ")
}

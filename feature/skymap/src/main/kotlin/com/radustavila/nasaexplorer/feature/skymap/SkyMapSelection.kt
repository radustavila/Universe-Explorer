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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.radustavila.nasaexplorer.core.designsystem.units.rememberUnitFormatter
import com.radustavila.nasaexplorer.core.model.SolarSystemBody
import com.radustavila.nasaexplorer.core.skymap.HorizontalSky
import com.radustavila.nasaexplorer.feature.skymap.render.SkyNames
import com.radustavila.nasaexplorer.feature.skymap.render.direction
import com.radustavila.nasaexplorer.feature.skymap.render.satellite
import kotlin.math.roundToInt
import kotlin.math.roundToLong

const val SKY_MAP_SELECTION_TEST_TAG = "sky-map-selection"

private const val PERCENT = 100.0

/** Name, kind, place in the sky and brightness of the selection. */
@Composable
internal fun SelectionCard(uiState: SkyMapUiState, selected: SkyTarget, names: SkyNames, onClose: () -> Unit) {
    val sky = uiState.sky ?: return
    val details = listOfNotNull(position(uiState, sky, selected), brightness(uiState, sky, selected))
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
    }

/** "Star in Orion", or just the kind when the constellation is not known. */
@Composable
private fun subtitle(uiState: SkyMapUiState, sky: HorizontalSky, selected: SkyTarget): String {
    val kind = stringResource(selected.kindRes())
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

/** "Magnitude -1.4", with how much of the Moon is lit. */
@Composable
private fun brightness(uiState: SkyMapUiState, sky: HorizontalSky, selected: SkyTarget): String? {
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

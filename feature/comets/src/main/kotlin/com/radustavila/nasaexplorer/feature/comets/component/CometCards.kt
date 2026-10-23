package com.radustavila.nasaexplorer.feature.comets.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.radustavila.nasaexplorer.core.designsystem.units.rememberUnitFormatter
import com.radustavila.nasaexplorer.core.model.CometTonight
import com.radustavila.nasaexplorer.feature.comets.R

fun cometCardTestTag(designation: String) = "comet-card-$designation"

fun faintCometTestTag(designation: String) = "faint-comet-$designation"

const val COMETS_PLACE_PROMPT_TEST_TAG = "comets-place-prompt"

/** A comet worth looking for: brightness, where and when to look tonight, and where it is. */
@Composable
internal fun CometCard(comet: CometTonight, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val units = rememberUnitFormatter()
    Card(onClick = onClick, modifier = modifier.fillMaxWidth().testTag(cometCardTestTag(comet.comet.designation))) {
        Column(
            modifier = Modifier.padding(16.dp).semantics(mergeDescendants = true) {},
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(comet.comet.name, style = MaterialTheme.typography.titleMedium)
            Text(magnitudeLine(comet, units), style = MaterialTheme.typography.bodyLarge)
            comet.sky?.let { sky ->
                Text(bestLine(sky, units), style = MaterialTheme.typography.bodyMedium)
                Text(
                    riseSetLine(sky, units),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = listOf(
                    stringResource(R.string.comets_constellation, comet.constellation),
                    stringResource(R.string.comets_distance_earth, units.au(comet.earthDistanceAu)),
                ).joinToString(stringResource(R.string.comets_separator)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** A candidate too faint tonight: its predicted brightness now and at its best. */
@Composable
internal fun FaintCometRow(comet: CometTonight, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val units = rememberUnitFormatter()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp)
            .semantics(mergeDescendants = true) {}
            .testTag(faintCometTestTag(comet.comet.designation)),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(comet.comet.name, style = MaterialTheme.typography.titleSmall)
        val peakMagnitude = comet.peakMagnitude
        val peakTime = comet.peakTime
        Text(
            text = listOfNotNull(
                magnitudeLine(comet, units),
                if (peakMagnitude != null && peakTime != null) {
                    stringResource(R.string.comets_peak, units.day(peakTime), units.magnitude(peakMagnitude))
                } else {
                    null
                },
            ).joinToString(stringResource(R.string.comets_separator)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Without a saved place, the directions are missing: say why and offer to set one. */
@Composable
internal fun PlacePrompt(onSetLocation: () -> Unit, modifier: Modifier = Modifier) {
    Card(modifier = modifier.fillMaxWidth().testTag(COMETS_PLACE_PROMPT_TEST_TAG)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.comets_place_prompt), style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(onClick = onSetLocation) { Text(stringResource(R.string.comets_place_action)) }
        }
    }
}

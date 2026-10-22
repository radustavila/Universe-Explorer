package com.radustavila.nasaexplorer.feature.dsn

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.radustavila.nasaexplorer.core.designsystem.component.SectionTitle
import com.radustavila.nasaexplorer.core.model.DeepSpaceProbe
import com.radustavila.nasaexplorer.core.model.DeepSpaceUnits
import java.time.Instant

/* "Farthest from home": Voyager 1 and 2 and New Horizons, from JPL Horizons. */

@Composable
internal fun FarthestHeader() {
    Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        SectionTitle(stringResource(R.string.dsn_farthest_title))
        Text(
            text = stringResource(R.string.dsn_farthest_description),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** One far spacecraft; its distance is moved on to [at] (the snapshot's time) or shown as Horizons gave it. */
@Composable
internal fun ProbeCard(probe: DeepSpaceProbe, isTalking: Boolean, at: Instant?) {
    val format = rememberDsnFormatter()
    val instant = at ?: probe.at
    val km = probe.distanceKmAt(instant)
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(probe.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                if (isTalking) {
                    Text(
                        text = stringResource(R.string.dsn_farthest_talking),
                        style = MaterialTheme.typography.labelMedium,
                        color = LIVE_GREEN,
                    )
                }
            }
            Text(
                text = "${format.astronomicalUnits(probe.distanceAuAt(instant))} · ${format.distance(km)}",
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = stringResource(
                    R.string.dsn_farthest_light,
                    format.lightTime(DeepSpaceUnits.lightTime(km)),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

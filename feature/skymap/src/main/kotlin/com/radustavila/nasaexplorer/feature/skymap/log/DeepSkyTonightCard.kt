package com.radustavila.nasaexplorer.feature.skymap.log

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.radustavila.nasaexplorer.core.designsystem.component.SectionTitle
import com.radustavila.nasaexplorer.core.designsystem.units.rememberUnitFormatter
import com.radustavila.nasaexplorer.core.model.DeepSkyCatalogue
import com.radustavila.nasaexplorer.core.model.DeepSkyObject
import com.radustavila.nasaexplorer.core.model.DeepSkyVisibility
import com.radustavila.nasaexplorer.feature.skymap.R
import com.radustavila.nasaexplorer.feature.skymap.deepsky.nameRes
import com.radustavila.nasaexplorer.feature.skymap.deepsky.title
import kotlin.math.roundToInt

const val DEEP_SKY_TONIGHT_CARD_TEST_TAG = "deep-sky-tonight-card"

/**
 * Sky tonight's "Tonight's best" card (F23): Messier objects not in the observing log yet that
 * climb 30° during tonight's dark hours, brightest first, each opening the sky map on it. Hidden
 * without a place, on nights that never get dark, and when no Messier object is up tonight.
 */
@Composable
fun DeepSkyTonightCard(
    onOpenLog: () -> Unit,
    onObjectClick: (objectId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ObservingLogViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    DeepSkyTonightCardContent(uiState, onOpenLog, onObjectClick, modifier)
}

@Composable
internal fun DeepSkyTonightCardContent(
    uiState: ObservingLogUiState,
    onOpenLog: () -> Unit,
    onObjectClick: (objectId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tonight = (uiState.tonight as? TonightSky.Known)?.tonight ?: return
    if (uiState.messierUpTonight == 0) return
    val best = uiState.tonightsBest()
    Card(modifier = modifier.fillMaxWidth().testTag(DEEP_SKY_TONIGHT_CARD_TEST_TAG)) {
        Column(Modifier.padding(vertical = 8.dp)) {
            CardHeader(onOpenLog)
            Text(
                text = stringResource(
                    R.string.deep_sky_tonight_progress,
                    uiState.seenCount(DeepSkyCatalogue.MESSIER),
                    DeepSkyCatalogue.MESSIER.size,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            if (best.isEmpty()) {
                Text(
                    text = stringResource(R.string.deep_sky_tonight_all_seen),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            best.forEach { item ->
                TonightItem(item, tonight.visible.getValue(item.id), onClick = { onObjectClick(item.id) })
            }
        }
    }
}

@Composable
private fun CardHeader(onOpenLog: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 16.dp, end = 8.dp)) {
        Icon(
            imageVector = Icons.Outlined.Checklist,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(8.dp))
        SectionTitle(stringResource(R.string.deep_sky_tonight_title), modifier = Modifier.weight(1f))
        IconButton(onClick = onOpenLog) {
            Icon(
                Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = stringResource(R.string.deep_sky_tonight_open),
            )
        }
    }
}

/** "M13 · Hercules Globular Cluster", then "21:40 · 62° up · Binoculars". */
@Composable
private fun TonightItem(item: DeepSkyObject, visibility: DeepSkyVisibility, onClick: () -> Unit) {
    val units = rememberUnitFormatter()
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Text(item.title(), style = MaterialTheme.typography.bodyLarge)
        Text(
            text = stringResource(
                R.string.deep_sky_tonight_item,
                units.time(visibility.bestTime),
                visibility.altitudeDegrees.roundToInt().toString(),
                stringResource(item.instrument.nameRes()),
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

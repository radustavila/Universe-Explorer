package com.radustavila.nasaexplorer.feature.comets.sky

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
import androidx.compose.material.icons.outlined.AutoAwesome
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.radustavila.nasaexplorer.core.designsystem.component.SectionTitle
import com.radustavila.nasaexplorer.core.designsystem.units.rememberUnitFormatter
import com.radustavila.nasaexplorer.core.model.CometTonight
import com.radustavila.nasaexplorer.feature.comets.CometsUiState
import com.radustavila.nasaexplorer.feature.comets.CometsViewModel
import com.radustavila.nasaexplorer.feature.comets.R
import com.radustavila.nasaexplorer.feature.comets.component.compassPointRes
import com.radustavila.nasaexplorer.feature.comets.component.labelRes
import com.radustavila.nasaexplorer.feature.comets.component.magnitude

const val COMETS_TONIGHT_CARD_TEST_TAG = "comets-tonight-card"

/**
 * Sky tonight's comets card: the comets bright enough to look for, each with its best time, or a
 * line saying there is none. [onOpenComets] opens the list; [onCometClick] one comet. Works offline
 * from the last daily download, and without a place (no directions then).
 */
@Composable
fun CometsTonightCard(
    onOpenComets: () -> Unit,
    onCometClick: (designation: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CometsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    CometsTonightCardContent(uiState, onOpenComets, onCometClick, modifier)
}

@Composable
internal fun CometsTonightCardContent(
    uiState: CometsUiState,
    onOpenComets: () -> Unit,
    onCometClick: (designation: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val comets = uiState.comets ?: return
    Card(onClick = onOpenComets, modifier = modifier.fillMaxWidth().testTag(COMETS_TONIGHT_CARD_TEST_TAG)) {
        Column(Modifier.padding(vertical = 8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 16.dp, end = 8.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                SectionTitle(stringResource(R.string.comets_card_title), modifier = Modifier.weight(1f))
                IconButton(onClick = onOpenComets) {
                    Icon(
                        Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                        contentDescription = stringResource(R.string.comets_card_open),
                    )
                }
            }
            if (comets.visible.isEmpty()) {
                Text(
                    text = stringResource(R.string.comets_card_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                )
            }
            comets.visible.take(MAX_LINES).forEach { comet ->
                CometLine(comet, onClick = { onCometClick(comet.comet.designation) })
            }
        }
    }
}

@Composable
private fun CometLine(comet: CometTonight, onClick: () -> Unit) {
    val units = rememberUnitFormatter()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Text(
            comet.comet.name,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        val bestTime = comet.sky?.bestTime
        val best = comet.sky?.best
        Text(
            text = listOfNotNull(
                comet.magnitude?.let {
                    stringResource(
                        R.string.comets_card_line,
                        units.magnitude(it),
                        stringResource(comet.visibility.labelRes()),
                    )
                },
                if (bestTime != null && best != null) {
                    stringResource(
                        R.string.comets_best_short,
                        units.time(bestTime),
                        stringResource(compassPointRes(best.azimuthDegrees)),
                    )
                } else {
                    null
                },
            ).joinToString(stringResource(R.string.comets_separator)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private const val MAX_LINES = 3

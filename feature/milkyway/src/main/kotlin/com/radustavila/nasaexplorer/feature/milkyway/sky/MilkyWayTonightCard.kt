package com.radustavila.nasaexplorer.feature.milkyway.sky

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.BlurOn
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
import com.radustavila.nasaexplorer.feature.milkyway.MilkyWayUiState
import com.radustavila.nasaexplorer.feature.milkyway.MilkyWayViewModel
import com.radustavila.nasaexplorer.feature.milkyway.R
import com.radustavila.nasaexplorer.feature.milkyway.component.compassPointRes
import com.radustavila.nasaexplorer.feature.milkyway.component.intervalText
import kotlin.math.roundToInt

const val MILKY_WAY_TONIGHT_CARD_TEST_TAG = "milky-way-tonight-card"

/**
 * Sky tonight's Milky Way card (F20): tonight's window ("23:40–02:10, core 18° high in the
 * south-west") or the next good night's. Hidden without a place, and when no night of the coming
 * month has a window: the months the core is up only by day, the summers that never get dark, and
 * latitudes where it never climbs 10°. [onOpen] opens the planner.
 */
@Composable
fun MilkyWayTonightCard(
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MilkyWayViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    MilkyWayTonightCardContent(uiState, onOpen, modifier)
}

@Composable
internal fun MilkyWayTonightCardContent(uiState: MilkyWayUiState, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val outlook = uiState.outlook
    val night = outlook?.nextWindowNight
    val window = night?.window ?: return
    val units = rememberUnitFormatter()
    val times = intervalText(window.start, window.end, units)
    val line = if (night == outlook.nights.firstOrNull()) {
        stringResource(
            R.string.milky_way_card_tonight,
            times,
            window.coreAtBest.altitudeDegrees.roundToInt(),
            stringResource(compassPointRes(window.coreAtBest.azimuthDegrees)),
        )
    } else {
        stringResource(R.string.milky_way_card_not_tonight, units.day(night.start), times)
    }
    Card(onClick = onOpen, modifier = modifier.fillMaxWidth().testTag(MILKY_WAY_TONIGHT_CARD_TEST_TAG)) {
        Column(Modifier.padding(vertical = 8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 16.dp, end = 8.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.BlurOn,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                SectionTitle(stringResource(R.string.milky_way_card_title), modifier = Modifier.weight(1f))
                IconButton(onClick = onOpen) {
                    Icon(
                        Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                        contentDescription = stringResource(R.string.milky_way_card_open),
                    )
                }
            }
            Text(
                text = line,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
            )
        }
    }
}

package com.radustavila.nasaexplorer.feature.sky.today

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.NightsStay
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.radustavila.nasaexplorer.core.model.PlanetTonight
import com.radustavila.nasaexplorer.core.model.SkyTonight
import com.radustavila.nasaexplorer.feature.sky.R
import com.radustavila.nasaexplorer.feature.sky.component.MoonPhaseIcon
import com.radustavila.nasaexplorer.feature.sky.component.formatTime
import com.radustavila.nasaexplorer.feature.sky.component.labelRes
import kotlin.math.roundToInt

const val SKY_TONIGHT_SUMMARY_CARD_TEST_TAG = "sky-tonight-summary-card"

/**
 * "Sky tonight" for the Today screen: the Moon, when it gets dark and which planets are up, for
 * the saved place. Without one it asks for it; everything is computed on the device.
 */
@Composable
fun SkyTonightSummaryCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SkyTonightSummaryViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    SkyTonightSummaryCardContent(uiState = uiState, onClick = onClick, modifier = modifier)
}

@Composable
internal fun SkyTonightSummaryCardContent(
    uiState: SkyTonightSummaryUiState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!uiState.isLoaded) return
    val sky = uiState.sky

    TodaySkyCard(
        title = stringResource(R.string.sky_title),
        icon = {
            if (sky == null) {
                Icon(
                    imageVector = Icons.Outlined.NightsStay,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
            } else {
                MoonPhaseIcon(
                    phaseAngleDegrees = sky.moon.phaseAngleDegrees,
                    mirrored = uiState.southernHemisphere,
                    modifier = Modifier.size(20.dp),
                )
            }
        },
        onClick = onClick,
        modifier = modifier.testTag(SKY_TONIGHT_SUMMARY_CARD_TEST_TAG),
    ) {
        if (sky == null) {
            Text(stringResource(R.string.sky_today_summary_no_place), style = MaterialTheme.typography.bodyMedium)
        } else {
            SummaryLines(sky)
        }
    }
}

@Composable
private fun SummaryLines(sky: SkyTonight) {
    Text(
        text = stringResource(
            R.string.sky_today_summary_moon,
            stringResource(sky.moon.phase.labelRes()),
            (sky.moon.illuminatedFraction * PERCENT).roundToInt(),
        ),
        style = MaterialTheme.typography.titleSmall,
    )
    val dark = sky.night.astronomicalDusk ?: sky.night.nauticalDusk
    if (dark != null) {
        Text(
            text = stringResource(R.string.sky_today_summary_dark, dark.formatTime()),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
    val visible = sky.planets.filter(PlanetTonight::isVisibleTonight)
    Text(
        text = if (visible.isEmpty()) {
            stringResource(R.string.sky_today_summary_no_planets)
        } else {
            stringResource(
                R.string.sky_today_summary_planets,
                visible.map { stringResource(it.planet.labelRes()) }
                    .joinToString(stringResource(R.string.sky_today_list_separator)),
            )
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private const val PERCENT = 100

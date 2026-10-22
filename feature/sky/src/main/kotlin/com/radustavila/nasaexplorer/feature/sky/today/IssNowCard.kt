package com.radustavila.nasaexplorer.feature.sky.today

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SatelliteAlt
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
import com.radustavila.nasaexplorer.core.designsystem.units.rememberUnitFormatter
import com.radustavila.nasaexplorer.core.model.SatellitePosition
import com.radustavila.nasaexplorer.feature.sky.R
import com.radustavila.nasaexplorer.feature.sky.component.formatDay
import com.radustavila.nasaexplorer.feature.sky.component.formatTime
import com.radustavila.nasaexplorer.feature.sky.component.label
import kotlin.math.roundToInt

const val ISS_NOW_CARD_TEST_TAG = "iss-now-card"

/**
 * "ISS now" for the Today screen: where the station is and its next visible pass over the saved
 * place. Offline it keeps working from the cached orbit, and says so when there is none yet.
 */
@Composable
fun IssNowCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: IssNowViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    IssNowCardContent(uiState = uiState, onClick = onClick, modifier = modifier)
}

@Composable
internal fun IssNowCardContent(
    uiState: IssNowUiState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!uiState.isLoaded) return

    TodaySkyCard(
        title = stringResource(R.string.sky_today_iss_title),
        icon = {
            Icon(
                imageVector = Icons.Outlined.SatelliteAlt,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
        },
        onClick = onClick,
        modifier = modifier.testTag(ISS_NOW_CARD_TEST_TAG),
    ) {
        val position = uiState.position
        if (position == null) {
            Text(stringResource(R.string.sky_track_unavailable), style = MaterialTheme.typography.bodyMedium)
        } else {
            PositionLines(position)
        }
        val passLine = when (val pass = uiState.nextPass) {
            IssNextPass.NoPlace -> stringResource(R.string.sky_today_iss_no_place)
            IssNextPass.NoOrbit -> null
            IssNextPass.NoneVisible -> stringResource(R.string.sky_today_iss_no_visible)
            is IssNextPass.Next -> stringResource(
                R.string.sky_today_iss_next_pass,
                pass.pass.start.formatDay(),
                pass.pass.start.formatTime(),
                pass.pass.maxElevationDegrees.roundToInt(),
            )
        }
        if (passLine != null) Text(passLine, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun PositionLines(position: SatellitePosition) {
    val units = rememberUnitFormatter()
    Text(
        text = stringResource(R.string.sky_today_iss_over, position.coordinate.label()),
        style = MaterialTheme.typography.titleSmall,
    )
    val sunlight = when (position.isSunlit) {
        true -> stringResource(R.string.sky_track_sunlit)
        false -> stringResource(R.string.sky_track_eclipsed)
        null -> null
    }
    Text(
        text = listOfNotNull(
            stringResource(R.string.sky_today_iss_altitude, units.distance(position.altitudeKm)),
            units.speedPerHour(position.speedKmPerHour),
            sunlight,
        ).joinToString(" · "),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

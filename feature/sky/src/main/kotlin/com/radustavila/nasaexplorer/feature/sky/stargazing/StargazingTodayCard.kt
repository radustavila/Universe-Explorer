package com.radustavila.nasaexplorer.feature.sky.stargazing

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.radustavila.nasaexplorer.core.designsystem.stargazing.StargazingText
import com.radustavila.nasaexplorer.core.designsystem.units.rememberUnitFormatter
import com.radustavila.nasaexplorer.feature.sky.R
import com.radustavila.nasaexplorer.feature.sky.today.TodaySkyCard

const val STARGAZING_TODAY_CARD_TEST_TAG = "stargazing-today-card"

/** The stargazing line on Today: tonight's rating and best window, or a nudge to save a place. */
@Composable
fun StargazingTodayCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: StargazingViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    StargazingTodayCardContent(uiState = uiState, onClick = onClick, modifier = modifier)
}

@Composable
fun StargazingTodayCardContent(uiState: StargazingUiState, onClick: () -> Unit, modifier: Modifier = Modifier) {
    if (!uiState.isLoaded) return
    val resources = LocalResources.current
    val formatter = rememberUnitFormatter()
    val tonight = uiState.forecast?.nights?.firstOrNull()
    TodaySkyCard(
        title = stringResource(R.string.sky_stargazing_today_title),
        icon = {
            Icon(
                imageVector = Icons.Outlined.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
        },
        onClick = onClick,
        modifier = modifier.testTag(STARGAZING_TODAY_CARD_TEST_TAG),
    ) {
        when {
            !uiState.hasLocation -> BodyText(stringResource(R.string.sky_stargazing_today_no_place))
            tonight == null -> BodyText(stringResource(R.string.sky_stargazing_computing))
            else -> {
                val score = tonight.score
                val rating = tonight.rating
                Text(
                    text = if (score != null && rating != null) {
                        stringResource(R.string.sky_stargazing_today_line, ratingLabel(rating), score)
                    } else {
                        stringResource(R.string.sky_stargazing_tonight)
                    },
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(StargazingText.summary(resources, formatter, tonight), style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = stringResource(R.string.sky_stargazing_credit),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

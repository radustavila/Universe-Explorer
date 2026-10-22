package com.radustavila.nasaexplorer.feature.dsn.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.SettingsInputAntenna
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.radustavila.nasaexplorer.core.designsystem.component.SectionTitle
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveDsnRightNowUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveDsnSnapshotUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.PollDeepSpaceNetworkUseCase
import com.radustavila.nasaexplorer.core.model.DeepSpaceUnits
import com.radustavila.nasaexplorer.core.model.DsnRightNow
import com.radustavila.nasaexplorer.feature.dsn.PollWhileStarted
import com.radustavila.nasaexplorer.feature.dsn.R
import com.radustavila.nasaexplorer.feature.dsn.rememberDsnFormatter
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Duration
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

const val DSN_RIGHT_NOW_CARD_TEST_TAG = "dsn-right-now-card"

/**
 * "Right now: Voyager 2 is talking to Canberra", for Today. Polls the live feed, more slowly than
 * the network's screen, only while the card is on a started screen.
 */
@Composable
fun DsnRightNowCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DsnRightNowViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    PollWhileStarted(viewModel::pollWhileVisible)
    DsnRightNowCardContent(uiState = uiState, onClick = onClick, modifier = modifier)
}

@Composable
internal fun DsnRightNowCardContent(
    uiState: DsnRightNowUiState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!uiState.isLoaded) return

    Card(onClick = onClick, modifier = modifier.fillMaxWidth().testTag(DSN_RIGHT_NOW_CARD_TEST_TAG)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 16.dp, bottom = 16.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.SettingsInputAntenna,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    SectionTitle(stringResource(R.string.dsn_title))
                }
                val rightNow = uiState.rightNow
                when {
                    rightNow != null -> RightNowLines(rightNow)
                    uiState.hasSnapshot -> Body(stringResource(R.string.dsn_today_quiet))
                    else -> Body(stringResource(R.string.dsn_today_empty))
                }
            }
            Icon(
                Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = stringResource(R.string.dsn_today_open),
            )
        }
    }
}

@Composable
private fun RightNowLines(rightNow: DsnRightNow) {
    Text(
        text = stringResource(R.string.dsn_today_right_now, rightNow.spacecraft.displayName, rightNow.siteName),
        style = MaterialTheme.typography.titleSmall,
    )
    val km = rightNow.distanceKm ?: return
    val format = rememberDsnFormatter()
    Text(
        text = stringResource(
            R.string.dsn_today_distance,
            format.distance(km),
            format.lightTime(DeepSpaceUnits.lightTime(km)),
        ),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun Body(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium)
}

@Immutable
data class DsnRightNowUiState(
    val isLoaded: Boolean = false,
    /** A snapshot is stored, so a null [rightNow] means every dish is quiet. */
    val hasSnapshot: Boolean = false,
    val rightNow: DsnRightNow? = null,
)

@HiltViewModel
class DsnRightNowViewModel @Inject constructor(
    observeRightNow: ObserveDsnRightNowUseCase,
    observeSnapshot: ObserveDsnSnapshotUseCase,
    private val poll: PollDeepSpaceNetworkUseCase,
) : ViewModel() {

    val uiState: StateFlow<DsnRightNowUiState> = combine(observeRightNow(), observeSnapshot()) { rightNow, snapshot ->
        DsnRightNowUiState(isLoaded = true, hasSnapshot = snapshot != null, rightNow = rightNow)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STATE_TIMEOUT_MILLIS),
        initialValue = DsnRightNowUiState(),
    )

    suspend fun pollWhileVisible(): Nothing = poll(interval = POLL_INTERVAL)

    private companion object {
        const val STATE_TIMEOUT_MILLIS = 5_000L

        /** A line on Today, not a live dashboard: once a minute is plenty. */
        val POLL_INTERVAL: Duration = Duration.ofMinutes(1)
    }
}

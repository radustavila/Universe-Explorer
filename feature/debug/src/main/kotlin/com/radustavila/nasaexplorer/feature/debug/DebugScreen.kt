package com.radustavila.nasaexplorer.feature.debug

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.radustavila.nasaexplorer.core.designsystem.component.LoadingState
import com.radustavila.nasaexplorer.core.designsystem.units.formatLocalDateTime
import com.radustavila.nasaexplorer.core.designsystem.util.formatMedium
import com.radustavila.nasaexplorer.core.domain.model.DebugInfo
import com.radustavila.nasaexplorer.core.domain.model.SourceSyncStatus
import com.radustavila.nasaexplorer.core.model.SyncState

const val DEBUG_CONTENT_TEST_TAG = "debug-content"
const val DEBUG_FAINT_COMETS_TEST_TAG = "debug-faint-comets"

@Composable
fun DebugScreen(
    modifier: Modifier = Modifier,
    viewModel: DebugViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    DebugContent(
        uiState = uiState,
        onSyncNow = viewModel::onSyncNow,
        onEnsurePeriodicSync = viewModel::onEnsurePeriodicSync,
        onShowFaintCometsChange = viewModel::onShowFaintCometsChange,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DebugContent(
    uiState: DebugUiState,
    onSyncNow: () -> Unit,
    onEnsurePeriodicSync: () -> Unit,
    onShowFaintCometsChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        // See FeedScreen: insets belong to the top bar and to the app scaffold's navigation bar.
        contentWindowInsets = WindowInsets(0),
        topBar = { TopAppBar(title = { Text(stringResource(R.string.debug_title)) }) },
    ) { padding ->
        val info = uiState.info
        if (info == null) {
            LoadingState(modifier = Modifier.padding(padding))
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
                .testTag(DEBUG_CONTENT_TEST_TAG),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            DebugSection(title = stringResource(R.string.debug_section_connectivity)) {
                DebugRow(
                    label = stringResource(R.string.debug_network),
                    value = stringResource(
                        if (info.isOnline) R.string.debug_online else R.string.debug_offline,
                    ),
                )
            }

            DebugSection(title = stringResource(R.string.debug_section_cache)) {
                DebugRow(
                    label = stringResource(R.string.debug_cached_count),
                    value = info.cachedApodCount.toString(),
                )
                DebugRow(
                    label = stringResource(R.string.debug_favorites_count),
                    value = info.favoriteCount.toString(),
                )
                DebugRow(
                    label = stringResource(R.string.debug_oldest_cached),
                    value = info.oldestCachedDate?.formatMedium() ?: stringResource(R.string.debug_none),
                )
                DebugRow(
                    label = stringResource(R.string.debug_oldest_requested),
                    value = info.oldestRequestedDate?.formatMedium() ?: stringResource(R.string.debug_none),
                )
            }

            DebugSection(title = stringResource(R.string.debug_section_sync)) {
                DebugRow(
                    label = stringResource(R.string.debug_last_sync),
                    value = info.lastSuccessfulSync?.formatLocalDateTime()
                        ?: stringResource(R.string.debug_never),
                )
                DebugRow(
                    label = stringResource(R.string.debug_worker_state),
                    value = info.syncState.describe(),
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(onClick = onSyncNow, modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.debug_sync_now),
                            textAlign = TextAlign.Center,
                        )
                    }
                    OutlinedButton(onClick = onEnsurePeriodicSync, modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.debug_schedule_periodic),
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }

            DebugSection(title = stringResource(R.string.debug_section_testing)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.debug_faint_comets), style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = stringResource(R.string.debug_faint_comets_description),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = uiState.showFaintComets,
                        onCheckedChange = onShowFaintCometsChange,
                        modifier = Modifier.testTag(DEBUG_FAINT_COMETS_TEST_TAG),
                    )
                }
            }

            DebugSection(title = stringResource(R.string.debug_section_sources)) {
                if (info.sources.isEmpty()) {
                    Text(
                        text = stringResource(R.string.debug_sources_none),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                info.sources.forEachIndexed { index, status ->
                    if (index > 0) HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    SourceRows(status)
                }
            }
        }
    }
}

@Composable
private fun SyncState.describe(): String = when (this) {
    SyncState.Idle -> stringResource(R.string.debug_state_idle)
    SyncState.Enqueued -> stringResource(R.string.debug_state_enqueued)
    SyncState.Syncing -> stringResource(R.string.debug_state_syncing)
    SyncState.Succeeded -> stringResource(R.string.debug_state_succeeded)
    is SyncState.Failed -> stringResource(
        R.string.debug_state_failed,
        error?.toString() ?: stringResource(R.string.debug_none),
    )
}

@Composable
private fun SourceRows(status: SourceSyncStatus) {
    val none = stringResource(R.string.debug_none)
    Text(
        text = status.source.key,
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier.padding(vertical = 4.dp),
    )
    DebugRow(
        label = stringResource(R.string.debug_source_last_success),
        value = status.lastSuccessAt?.formatLocalDateTime() ?: stringResource(R.string.debug_never),
    )
    DebugRow(
        label = stringResource(R.string.debug_source_last_error),
        value = status.lastFailureAt?.let { failedAt ->
            stringResource(
                R.string.debug_source_error_at,
                status.lastErrorDescription ?: none,
                failedAt.formatLocalDateTime(),
            )
        } ?: none,
    )
    DebugRow(
        label = stringResource(R.string.debug_source_next_refresh),
        value = status.nextAllowedRefreshAt?.formatLocalDateTime()
            ?: stringResource(R.string.debug_source_any_time),
    )
}

@Composable
private fun DebugSection(title: String, content: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleSmall)
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            content()
        }
    }
}

@Composable
private fun DebugRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(text = value, style = MaterialTheme.typography.bodyMedium)
    }
}

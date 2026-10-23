package com.radustavila.nasaexplorer.feature.comets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridItemSpanScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.radustavila.nasaexplorer.core.designsystem.adaptive.PanelColumnMinWidth
import com.radustavila.nasaexplorer.core.designsystem.component.EmptyState
import com.radustavila.nasaexplorer.core.designsystem.component.FreshnessLabel
import com.radustavila.nasaexplorer.core.designsystem.component.LoadingState
import com.radustavila.nasaexplorer.core.designsystem.component.OfflineBanner
import com.radustavila.nasaexplorer.core.designsystem.component.OfflineState
import com.radustavila.nasaexplorer.core.designsystem.component.SectionTitle
import com.radustavila.nasaexplorer.core.designsystem.units.rememberUnitFormatter
import com.radustavila.nasaexplorer.core.model.CometVisibility
import com.radustavila.nasaexplorer.feature.comets.component.CometCard
import com.radustavila.nasaexplorer.feature.comets.component.FaintCometRow
import com.radustavila.nasaexplorer.feature.comets.component.PlacePrompt
import com.radustavila.nasaexplorer.feature.comets.component.magnitude

const val COMETS_LIST_TEST_TAG = "comets-list"
const val COMETS_EMPTY_TEST_TAG = "comets-empty"

@Composable
fun CometsScreen(
    onBackClick: () -> Unit,
    onCometClick: (designation: String) -> Unit,
    onSetLocation: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CometsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val failedMessage = stringResource(R.string.comets_refresh_failed)
    LaunchedEffect(uiState.refreshFailed) {
        if (uiState.refreshFailed) {
            snackbarHostState.showSnackbar(failedMessage)
            viewModel.onRefreshFailedShown()
        }
    }
    CometsContent(
        uiState = uiState,
        onBackClick = onBackClick,
        onCometClick = onCometClick,
        onSetLocation = onSetLocation,
        onRefresh = viewModel::onRefresh,
        snackbarHostState = snackbarHostState,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CometsContent(
    uiState: CometsUiState,
    onBackClick: () -> Unit,
    onCometClick: (designation: String) -> Unit,
    onSetLocation: () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.comets_title)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.comets_back),
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(Modifier.padding(padding)) {
            if (uiState.freshness.isOffline && uiState.freshness.lastUpdatedAt != null) OfflineBanner()
            PullToRefreshBox(
                isRefreshing = uiState.isRefreshing,
                onRefresh = onRefresh,
                modifier = Modifier.fillMaxSize(),
            ) {
                when {
                    !uiState.isLoaded -> LoadingState()
                    uiState.isEmpty && uiState.freshness.lastUpdatedAt == null ->
                        NeverDownloaded(uiState.freshness.isOffline)
                    else -> CometList(uiState, onCometClick, onSetLocation)
                }
            }
        }
    }
}

@Composable
private fun NeverDownloaded(isOffline: Boolean) {
    if (isOffline) {
        OfflineState(
            title = stringResource(R.string.comets_offline_title),
            description = stringResource(R.string.comets_offline_description),
        )
    } else {
        EmptyState(
            title = stringResource(R.string.comets_none_title),
            description = stringResource(R.string.comets_none_description),
        )
    }
}

@Composable
private fun CometList(
    uiState: CometsUiState,
    onCometClick: (designation: String) -> Unit,
    onSetLocation: () -> Unit,
) {
    val comets = checkNotNull(uiState.comets)
    val units = rememberUnitFormatter()
    // One column on a phone held upright; side by side on wider windows.
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = PanelColumnMinWidth),
        modifier = Modifier.fillMaxSize().testTag(COMETS_LIST_TEST_TAG),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val fullLine: LazyGridItemSpanScope.() -> GridItemSpan = { GridItemSpan(maxLineSpan) }
        item(key = "freshness", span = fullLine) { FreshnessLabel(uiState.freshness) }
        if (!uiState.hasPlace) {
            item(key = "place", span = fullLine) { PlacePrompt(onSetLocation) }
        }
        item(key = "visible-title", span = fullLine) { SectionTitle(stringResource(R.string.comets_visible_title)) }
        if (comets.visible.isEmpty()) {
            item(key = "empty", span = fullLine) {
                EmptyState(
                    title = stringResource(R.string.comets_empty_title),
                    description = stringResource(
                        R.string.comets_empty_description,
                        units.magnitude(CometVisibility.TELESCOPE_LIMIT),
                    ),
                    modifier = Modifier.testTag(COMETS_EMPTY_TEST_TAG),
                )
            }
        }
        items(comets.visible, key = { it.comet.designation }) { comet ->
            CometCard(comet, onClick = { onCometClick(comet.comet.designation) })
        }
        if (comets.faint.isNotEmpty()) {
            item(key = "faint-title", span = fullLine) { FaintTitle() }
            items(comets.faint, key = { "faint-${it.comet.designation}" }) { comet ->
                FaintCometRow(comet, onClick = { onCometClick(comet.comet.designation) })
            }
        }
        item(key = "footer", span = fullLine) { Footer() }
    }
}

@Composable
private fun FaintTitle() {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SectionTitle(stringResource(R.string.comets_faint_title))
        Text(
            stringResource(R.string.comets_faint_description),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Footer() {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 8.dp)) {
        Text(
            stringResource(R.string.comets_disclaimer),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            stringResource(R.string.comets_credit),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

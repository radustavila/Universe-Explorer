package com.radustavila.nasaexplorer.feature.news.podcast

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.radustavila.nasaexplorer.core.designsystem.adaptive.ReadableMaxWidth
import com.radustavila.nasaexplorer.core.designsystem.component.EmptyState
import com.radustavila.nasaexplorer.core.designsystem.component.FreshnessLabel
import com.radustavila.nasaexplorer.core.designsystem.component.LoadingState
import com.radustavila.nasaexplorer.core.designsystem.component.OfflineBanner
import com.radustavila.nasaexplorer.core.designsystem.component.OfflineState
import com.radustavila.nasaexplorer.core.designsystem.error.asText
import com.radustavila.nasaexplorer.core.designsystem.podcast.nameRes
import com.radustavila.nasaexplorer.core.model.PodcastEpisode
import com.radustavila.nasaexplorer.core.model.PodcastShow
import com.radustavila.nasaexplorer.feature.news.R

const val PODCAST_LIST_TEST_TAG = "podcast-list"

fun podcastFilterTestTag(show: PodcastShow) = "podcast-filter-${show.name}"

/** NASA's podcasts, newest episode first, filtered by show; episodes play in the app. */
@Composable
internal fun PodcastsTab(
    onEpisodeClick: (id: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PodcastsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    PodcastsContent(
        uiState = uiState,
        onEpisodeClick = { onEpisodeClick(it.id) },
        onPlayClick = viewModel::onPlayClick,
        onShowSelected = viewModel::onShowSelected,
        onRefresh = viewModel::onRefresh,
        onErrorShown = viewModel::onErrorShown,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PodcastsContent(
    uiState: PodcastsUiState,
    onEpisodeClick: (PodcastEpisode) -> Unit,
    onPlayClick: (PodcastEpisode) -> Unit,
    onShowSelected: (PodcastShow?) -> Unit,
    onRefresh: () -> Unit,
    onErrorShown: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    // With episodes on screen a failed refresh is a passing note; without, the empty state says it.
    val errorMessage = uiState.error?.takeIf { uiState.hasEpisodes }?.asText()
    LaunchedEffect(errorMessage) {
        if (errorMessage != null) {
            snackbarHostState.showSnackbar(errorMessage)
            onErrorShown()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (uiState.freshness.isOffline && uiState.hasEpisodes) OfflineBanner()
            PullToRefreshBox(
                isRefreshing = uiState.isRefreshing && uiState.hasEpisodes,
                onRefresh = onRefresh,
                modifier = Modifier.fillMaxSize(),
            ) {
                PodcastsBody(uiState, onEpisodeClick, onPlayClick, onShowSelected, onRefresh)
            }
        }
        SnackbarHost(snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun PodcastsBody(
    uiState: PodcastsUiState,
    onEpisodeClick: (PodcastEpisode) -> Unit,
    onPlayClick: (PodcastEpisode) -> Unit,
    onShowSelected: (PodcastShow?) -> Unit,
    onRefresh: () -> Unit,
) {
    when {
        uiState.hasEpisodes -> EpisodeList(uiState, onEpisodeClick, onPlayClick, onShowSelected)
        !uiState.isLoaded || uiState.isRefreshing -> LoadingState()
        uiState.freshness.isOffline -> ScrollableMessage { messageModifier ->
            OfflineState(
                title = stringResource(R.string.podcasts_offline_title),
                description = stringResource(R.string.podcasts_offline_description),
                modifier = messageModifier,
            )
        }

        else -> ScrollableMessage { messageModifier ->
            val failed = uiState.error != null
            EmptyState(
                title = stringResource(if (failed) R.string.podcasts_failed_title else R.string.podcasts_empty_title),
                description = stringResource(
                    if (failed) R.string.podcasts_failed_description else R.string.podcasts_empty_description,
                ),
                onRetry = onRefresh,
                modifier = messageModifier,
            )
        }
    }
}

@Composable
private fun EpisodeList(
    uiState: PodcastsUiState,
    onEpisodeClick: (PodcastEpisode) -> Unit,
    onPlayClick: (PodcastEpisode) -> Unit,
    onShowSelected: (PodcastShow?) -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = ReadableMaxWidth)
                .fillMaxSize()
                .testTag(PODCAST_LIST_TEST_TAG),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            item(key = "filters") {
                ShowFilters(selected = uiState.selectedShow, onShowSelected = onShowSelected)
            }
            item(key = "freshness") {
                FreshnessLabel(uiState.freshness, Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
            }
            items(uiState.episodes, key = { it.id }) { episode ->
                Column {
                    PodcastEpisodeRow(
                        episode = episode,
                        now = uiState.now,
                        isPlaying = uiState.isPlaying(episode),
                        position = uiState.positionIn(episode),
                        onClick = { onEpisodeClick(episode) },
                        onPlayClick = { onPlayClick(episode) },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                }
            }
            item(key = "attribution") {
                Text(
                    text = stringResource(R.string.podcasts_attribution),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
    }
}

@Composable
private fun ShowFilters(selected: PodcastShow?, onShowSelected: (PodcastShow?) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PodcastShow.entries.forEach { show ->
            FilterChip(
                selected = show == selected,
                onClick = { onShowSelected(show) },
                label = { Text(stringResource(show.nameRes)) },
                modifier = Modifier.testTag(podcastFilterTestTag(show)),
            )
        }
    }
}

/** Centred while it fits, scrolling once it does not, which also lets pull-to-refresh work over it. */
@Composable
private fun ScrollableMessage(content: @Composable (Modifier) -> Unit) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val viewportHeight = maxHeight
        Box(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            content(Modifier.heightIn(min = viewportHeight))
        }
    }
}

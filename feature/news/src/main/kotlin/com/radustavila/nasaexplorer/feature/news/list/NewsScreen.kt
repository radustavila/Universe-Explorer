package com.radustavila.nasaexplorer.feature.news.list

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
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
import com.radustavila.nasaexplorer.core.model.NewsItem
import com.radustavila.nasaexplorer.core.model.NewsKind
import com.radustavila.nasaexplorer.feature.news.LiveNowBanner
import com.radustavila.nasaexplorer.feature.news.NewsRow
import com.radustavila.nasaexplorer.feature.news.R
import com.radustavila.nasaexplorer.feature.news.chipRes
import com.radustavila.nasaexplorer.feature.news.icon
import com.radustavila.nasaexplorer.feature.news.rememberNewsItemOpener

const val NEWS_LIST_TEST_TAG = "news-list"

fun newsFilterTestTag(kind: NewsKind) = "news-filter-${kind.name}"

@Composable
fun NewsScreen(
    onBackClick: () -> Unit,
    onImageClick: (id: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: NewsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    NewsContent(
        uiState = uiState,
        onBackClick = onBackClick,
        onItemClick = rememberNewsItemOpener(onImageClick),
        onKindSelected = viewModel::onKindSelected,
        onRefresh = viewModel::onRefresh,
        onErrorShown = viewModel::onErrorShown,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NewsContent(
    uiState: NewsUiState,
    onBackClick: () -> Unit,
    onItemClick: (NewsItem) -> Unit,
    onKindSelected: (NewsKind?) -> Unit,
    onRefresh: () -> Unit,
    onErrorShown: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    // With news on screen a failed refresh is a passing note; without, the empty state says it.
    val errorMessage = uiState.error?.takeIf { uiState.hasNews }?.asText()
    LaunchedEffect(errorMessage) {
        if (errorMessage != null) {
            snackbarHostState.showSnackbar(errorMessage)
            onErrorShown()
        }
    }

    Scaffold(
        modifier = modifier,
        // Inside the Universe tab: the app scaffold's navigation bar owns the bottom inset.
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.news_title)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.news_back),
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (uiState.freshness.isOffline && uiState.hasNews) OfflineBanner()
            PullToRefreshBox(
                isRefreshing = uiState.isRefreshing && uiState.hasNews,
                onRefresh = onRefresh,
                modifier = Modifier.fillMaxSize(),
            ) {
                NewsBody(uiState, onItemClick, onKindSelected, onRefresh)
            }
        }
    }
}

/** The list, or why there is none yet. */
@Composable
private fun NewsBody(
    uiState: NewsUiState,
    onItemClick: (NewsItem) -> Unit,
    onKindSelected: (NewsKind?) -> Unit,
    onRefresh: () -> Unit,
) {
    when {
        uiState.hasNews -> NewsList(uiState, onItemClick, onKindSelected)
        !uiState.isLoaded || uiState.isRefreshing -> LoadingState()
        uiState.freshness.isOffline -> ScrollableMessage { messageModifier ->
            OfflineState(
                title = stringResource(R.string.news_offline_title),
                description = stringResource(R.string.news_offline_description),
                modifier = messageModifier,
            )
        }

        uiState.error != null -> ScrollableMessage { messageModifier ->
            EmptyState(
                title = stringResource(R.string.news_failed_title),
                description = stringResource(R.string.news_failed_description),
                onRetry = onRefresh,
                modifier = messageModifier,
            )
        }

        else -> ScrollableMessage { messageModifier ->
            EmptyState(
                title = stringResource(R.string.news_empty_title),
                description = stringResource(R.string.news_empty_description),
                onRetry = onRefresh,
                modifier = messageModifier,
            )
        }
    }
}

@Composable
private fun NewsList(
    uiState: NewsUiState,
    onItemClick: (NewsItem) -> Unit,
    onKindSelected: (NewsKind?) -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = ReadableMaxWidth)
                .fillMaxSize()
                .testTag(NEWS_LIST_TEST_TAG),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            item(key = "filters") {
                KindFilters(selected = uiState.selectedKind, onKindSelected = onKindSelected)
            }
            item(key = "freshness") {
                FreshnessLabel(uiState.freshness, Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
            }
            // The stream moves from its place in the list to the top.
            val live = uiState.liveItem?.takeIf { uiState.selectedKind in setOf(null, NewsKind.VIDEO) }
            val rows = if (live == null) uiState.items else uiState.items - live
            if (live != null) {
                item(key = "live") {
                    LiveNowBanner(
                        item = live,
                        onClick = { onItemClick(live) },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }
            if (rows.isEmpty() && live == null) {
                item(key = "filter-empty") {
                    Text(
                        text = stringResource(R.string.news_filter_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
            items(rows, key = { it.id }) { item ->
                Column {
                    NewsRow(
                        item = item,
                        now = uiState.now,
                        onClick = { onItemClick(item) },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                }
            }
            item(key = "attribution") {
                Text(
                    text = stringResource(R.string.news_attribution),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
    }
}

@Composable
private fun KindFilters(selected: NewsKind?, onKindSelected: (NewsKind?) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        NewsKind.entries.forEach { kind ->
            FilterChip(
                selected = kind == selected,
                onClick = { onKindSelected(kind) },
                label = { Text(stringResource(kind.chipRes)) },
                leadingIcon = {
                    Icon(kind.icon, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize))
                },
                modifier = Modifier.testTag(newsFilterTestTag(kind)),
            )
        }
    }
}

/**
 * A message that is centred while it fits and scrolls once it does not (landscape), which also lets
 * pull-to-refresh work over it.
 */
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

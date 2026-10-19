package com.radustavila.nasaexplorer.feature.universe.gallery

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.radustavila.nasaexplorer.core.designsystem.component.EmptyState
import com.radustavila.nasaexplorer.core.designsystem.component.LoadingState
import com.radustavila.nasaexplorer.core.designsystem.component.MessageState
import com.radustavila.nasaexplorer.core.designsystem.component.OfflineBanner
import com.radustavila.nasaexplorer.core.designsystem.component.OfflineState
import com.radustavila.nasaexplorer.core.designsystem.error.asText
import com.radustavila.nasaexplorer.core.model.GalleryRail
import com.radustavila.nasaexplorer.core.model.NasaMediaType
import com.radustavila.nasaexplorer.feature.universe.R
import java.text.NumberFormat
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter

const val GALLERY_RAILS_TEST_TAG = "gallery-rails"
const val GALLERY_SEARCH_FIELD_TEST_TAG = "gallery-search-field"
const val GALLERY_RESULTS_TEST_TAG = "gallery-results"

fun galleryRailTestTag(rail: GalleryRail): String = "gallery-rail-${rail.key}"

@Composable
fun GalleryScreen(
    onMediaClick: (nasaId: String) -> Unit,
    header: (@Composable () -> Unit)?,
    modifier: Modifier = Modifier,
    viewModel: GalleryViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    GalleryContent(
        uiState = uiState,
        header = header,
        onMediaClick = onMediaClick,
        onQueryChange = viewModel::onQueryChange,
        onClearQuery = viewModel::onClearQuery,
        onToggleMediaType = viewModel::onToggleMediaType,
        onYearRangeChange = viewModel::onYearRangeChange,
        onRefresh = viewModel::onRefresh,
        onRefreshErrorShown = viewModel::onRefreshErrorShown,
        onRetrySearch = viewModel::onRetrySearch,
        onLoadMore = viewModel::onLoadMore,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GalleryContent(
    uiState: GalleryUiState,
    header: (@Composable () -> Unit)?,
    onMediaClick: (nasaId: String) -> Unit,
    onQueryChange: (String) -> Unit,
    onClearQuery: () -> Unit,
    onToggleMediaType: (NasaMediaType) -> Unit,
    onYearRangeChange: (start: Int?, end: Int?) -> Unit,
    onRefresh: () -> Unit,
    onRefreshErrorShown: () -> Unit,
    onRetrySearch: () -> Unit,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    // Over cached rails a failed refresh is only worth a note; without rails it is the screen.
    val refreshMessage = uiState.refreshError?.takeIf { uiState.rails.isNotEmpty() }?.asText()

    LaunchedEffect(refreshMessage) {
        if (refreshMessage != null) {
            snackbarHostState.showSnackbar(refreshMessage)
            onRefreshErrorShown()
        }
    }

    Scaffold(
        modifier = modifier,
        // See FeedScreen: insets belong to the top bar and to the app scaffold's navigation bar.
        contentWindowInsets = WindowInsets(0),
        topBar = { TopAppBar(title = { Text(stringResource(R.string.gallery_title)) }) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            SearchField(
                query = uiState.query,
                onQueryChange = onQueryChange,
                onClear = onClearQuery,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
            )

            if (uiState.isSearching) {
                SearchFiltersRow(
                    filters = uiState.filters,
                    onToggleMediaType = onToggleMediaType,
                    onYearRangeChange = onYearRangeChange,
                    modifier = Modifier.padding(top = 4.dp),
                )
                SearchResults(
                    search = uiState.search,
                    onMediaClick = onMediaClick,
                    onRetry = onRetrySearch,
                    onLoadMore = onLoadMore,
                )
            } else {
                PullToRefreshBox(
                    isRefreshing = uiState.isRefreshing && uiState.rails.isNotEmpty(),
                    onRefresh = onRefresh,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Rails(
                        uiState = uiState,
                        header = header,
                        onMediaClick = onMediaClick,
                        onRetry = onRefresh,
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current

    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.testTag(GALLERY_SEARCH_FIELD_TEST_TAG),
        placeholder = { Text(stringResource(R.string.gallery_search_hint)) },
        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = onClear) {
                    Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.gallery_search_clear))
                }
            }
        },
        singleLine = true,
        shape = MaterialTheme.shapes.extraLarge,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        // Results already follow the typing; the key only puts the keyboard away.
        keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
    )
}

@Composable
private fun Rails(
    uiState: GalleryUiState,
    header: (@Composable () -> Unit)?,
    onMediaClick: (nasaId: String) -> Unit,
    onRetry: () -> Unit,
) {
    when (val status = uiState.railsStatus) {
        RailsStatus.Loading -> LoadingState()

        RailsStatus.Offline -> OfflineState(
            title = stringResource(R.string.gallery_rails_offline_title),
            description = stringResource(R.string.gallery_rails_offline_description),
            onRetry = onRetry,
        )

        is RailsStatus.Failed -> EmptyState(
            title = stringResource(R.string.gallery_rails_error_title),
            description = status.error.asText(),
            onRetry = onRetry.takeIf { status.error.isRetryable },
        )

        RailsStatus.Content -> LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag(GALLERY_RAILS_TEST_TAG),
            contentPadding = PaddingValues(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            if (uiState.isOffline) item(key = "offline") { OfflineBanner() }
            if (header != null) {
                item(key = "header") {
                    Box(modifier = Modifier.padding(horizontal = 16.dp)) { header() }
                }
            }
            items(items = uiState.rails, key = { it.rail.key }) { rail ->
                RailSection(rail = rail, onMediaClick = onMediaClick)
            }
        }
    }
}

@Composable
private fun RailSection(
    rail: RailUi,
    onMediaClick: (nasaId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.testTag(galleryRailTestTag(rail.rail))) {
        Text(
            text = stringResource(rail.rail.titleRes),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(items = rail.items, key = { it.nasaId }) { media ->
                MediaCard(
                    media = media,
                    onClick = { onMediaClick(media.nasaId) },
                    modifier = Modifier.width(RAIL_CARD_WIDTH),
                )
            }
        }
    }
}

@Composable
private fun SearchResults(
    search: SearchUiState,
    onMediaClick: (nasaId: String) -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
) {
    when (search) {
        SearchUiState.Idle, SearchUiState.Loading -> LoadingState()

        SearchUiState.NeedsConnection -> OfflineState(
            title = stringResource(R.string.gallery_search_offline_title),
            description = stringResource(R.string.gallery_search_offline_description),
            onRetry = onRetry,
        )

        SearchUiState.Empty -> MessageState(
            icon = Icons.Outlined.SearchOff,
            title = stringResource(R.string.gallery_search_empty_title),
            description = stringResource(R.string.gallery_search_empty_description),
        )

        is SearchUiState.Failed -> EmptyState(
            title = stringResource(R.string.gallery_search_error_title),
            description = search.error.asText(),
            onRetry = onRetry.takeIf { search.error.isRetryable },
        )

        is SearchUiState.Results -> ResultsGrid(
            results = search,
            onMediaClick = onMediaClick,
            onLoadMore = onLoadMore,
        )
    }
}

@Composable
private fun ResultsGrid(
    results: SearchUiState.Results,
    onMediaClick: (nasaId: String) -> Unit,
    onLoadMore: () -> Unit,
) {
    val gridState = rememberLazyGridState()
    val currentOnLoadMore by rememberUpdatedState(onLoadMore)

    LaunchedEffect(gridState) {
        snapshotFlow {
            val info = gridState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            info.totalItemsCount > 0 && last >= info.totalItemsCount - LOAD_MORE_THRESHOLD
        }
            .distinctUntilChanged()
            .filter { it }
            .collect { currentOnLoadMore() }
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = GRID_MIN_CELL),
        state = gridState,
        modifier = Modifier
            .fillMaxSize()
            .testTag(GALLERY_RESULTS_TEST_TAG),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "hits", span = { GridItemSpan(maxLineSpan) }) {
            Text(
                text = pluralStringResource(
                    R.plurals.gallery_search_hits,
                    results.totalHits,
                    NumberFormat.getIntegerInstance().format(results.totalHits),
                ),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        items(items = results.items, key = { it.nasaId }) { media ->
            MediaCard(media = media, onClick = { onMediaClick(media.nasaId) })
        }
        if (results.isAppending || results.appendError != null) {
            item(key = "footer", span = { GridItemSpan(maxLineSpan) }) {
                Box(modifier = Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
                    val error = results.appendError
                    if (error == null) {
                        CircularProgressIndicator()
                    } else {
                        TextButton(onClick = onLoadMore) { Text(error.asText()) }
                    }
                }
            }
        }
    }
}

@get:StringRes
internal val GalleryRail.titleRes: Int
    get() = when (this) {
        GalleryRail.WEBB_GALAXIES -> R.string.gallery_rail_webb_galaxies
        GalleryRail.HUBBLE_GALAXIES -> R.string.gallery_rail_hubble_galaxies
        GalleryRail.NEBULAE -> R.string.gallery_rail_nebulae
        GalleryRail.BLACK_HOLES -> R.string.gallery_rail_black_holes
        GalleryRail.MILKY_WAY -> R.string.gallery_rail_milky_way
        GalleryRail.WEBB -> R.string.gallery_rail_webb
    }

private val RAIL_CARD_WIDTH = 168.dp
private val GRID_MIN_CELL = 160.dp
private const val LOAD_MORE_THRESHOLD = 6

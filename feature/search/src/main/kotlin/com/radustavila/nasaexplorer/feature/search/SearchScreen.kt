package com.radustavila.nasaexplorer.feature.search

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Newspaper
import androidx.compose.material.icons.outlined.ImageSearch
import androidx.compose.material.icons.outlined.Photo
import androidx.compose.material.icons.outlined.Podcasts
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.RocketLaunch
import androidx.compose.material.icons.outlined.TravelExplore
import androidx.compose.material.icons.outlined.Waves
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.radustavila.nasaexplorer.core.designsystem.component.NasaAsyncImage
import com.radustavila.nasaexplorer.core.designsystem.util.formatMedium
import com.radustavila.nasaexplorer.core.domain.search.SearchResultGroup
import com.radustavila.nasaexplorer.core.domain.search.SearchText
import com.radustavila.nasaexplorer.core.domain.search.UniversalSearchUseCase
import com.radustavila.nasaexplorer.core.model.FavoriteSource
import com.radustavila.nasaexplorer.core.model.SearchHit
import com.radustavila.nasaexplorer.core.model.SearchSource
import com.radustavila.nasaexplorer.core.model.SearchTarget

const val SEARCH_FIELD_TEST_TAG = "search-field"
const val SEARCH_RESULTS_TEST_TAG = "search-results"
const val SEARCH_ONLINE_BUTTON_TEST_TAG = "search-online"

/** How many hits a group shows before its "see all". */
internal const val GROUP_PREVIEW_SIZE = 3

@Composable
fun SearchScreen(
    onBackClick: () -> Unit,
    onResultClick: (SearchTarget) -> Unit,
    onSearchOnline: (query: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    SearchContent(
        uiState = uiState,
        onBackClick = onBackClick,
        onQueryChange = viewModel::onQueryChange,
        onClearQuery = viewModel::onClearQuery,
        onSeeAll = viewModel::onSeeAll,
        onShowAllGroups = viewModel::onShowAllGroups,
        onResultClick = onResultClick,
        onSearchOnline = onSearchOnline,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SearchContent(
    uiState: SearchUiState,
    onBackClick: () -> Unit,
    onQueryChange: (String) -> Unit,
    onClearQuery: () -> Unit,
    onSeeAll: (SearchSource) -> Unit,
    onShowAllGroups: () -> Unit,
    onResultClick: (SearchTarget) -> Unit,
    onSearchOnline: (query: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Back from one group's full list returns to every group first.
    BackHandler(enabled = uiState.expandedSource != null, onBack = onShowAllGroups)

    Scaffold(
        modifier = modifier,
        topBar = {
            Column {
                SearchBar(
                    query = uiState.query,
                    onQueryChange = onQueryChange,
                    onClearQuery = onClearQuery,
                    onBackClick = onBackClick,
                )
                Box(Modifier.fillMaxWidth()) {
                    if (uiState.isSearching) LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .imePadding(),
        ) {
            if (!uiState.hasQuery) {
                SearchIntro()
            } else {
                SearchResultsList(
                    uiState = uiState,
                    onSeeAll = onSeeAll,
                    onShowAllGroups = onShowAllGroups,
                    onResultClick = onResultClick,
                    onSearchOnline = onSearchOnline,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClearQuery: () -> Unit,
    onBackClick: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    // Opening search is a request to type; a query handed in (e.g. by a shortcut) is already typed.
    LaunchedEffect(focusRequester) { if (query.isEmpty()) focusRequester.requestFocus() }

    TopAppBar(
        navigationIcon = {
            IconButton(onClick = onBackClick) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.search_close),
                )
            }
        },
        title = {
            TextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = { Text(stringResource(R.string.search_placeholder)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                // Results already follow the typing; the action only makes room to read them.
                keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .testTag(SEARCH_FIELD_TEST_TAG),
            )
        },
        actions = {
            if (query.isNotEmpty()) {
                IconButton(onClick = onClearQuery) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = stringResource(R.string.search_clear),
                    )
                }
            }
        },
    )
}

/** Before anything is typed: what search covers, so nobody expects the whole of NASA. */
@Composable
private fun SearchIntro(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = Icons.Outlined.TravelExplore,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = stringResource(R.string.search_intro_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = stringResource(R.string.search_intro_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SearchResultsList(
    uiState: SearchUiState,
    onSeeAll: (SearchSource) -> Unit,
    onShowAllGroups: () -> Unit,
    onResultClick: (SearchTarget) -> Unit,
    onSearchOnline: (query: String) -> Unit,
) {
    val expanded = uiState.expandedSource
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag(SEARCH_RESULTS_TEST_TAG),
        contentPadding = PaddingValues(bottom = 16.dp),
    ) {
        item(key = "scope") {
            Text(
                text = stringResource(R.string.search_scope),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }

        if (uiState.hasNoResults) {
            item(key = "empty") {
                Text(
                    text = stringResource(R.string.search_no_results, uiState.query.trim()),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }

        uiState.visibleGroups.forEach { group ->
            item(key = "header-${group.source}") {
                GroupHeader(
                    group = group,
                    isExpanded = expanded == group.source,
                    onSeeAll = { onSeeAll(group.source) },
                    onShowAllGroups = onShowAllGroups,
                )
            }
            val hits = if (expanded == group.source) group.hits else group.hits.take(GROUP_PREVIEW_SIZE)
            items(hits, key = { hit -> "${hit.source}-${hit.id}" }) { hit ->
                SearchHitRow(hit = hit, terms = uiState.terms, onClick = { onResultClick(hit.target) })
            }
        }

        val failed = uiState.results?.failedSources.orEmpty()
        if (failed.isNotEmpty() && !uiState.isSearching) {
            item(key = "failed") {
                val names = failed.map { stringResource(it.labelRes) }.joinToString()
                Text(
                    text = stringResource(R.string.search_sources_failed, names),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }

        if (expanded == null) {
            item(key = "online") {
                SearchOnlineAction(
                    isOffline = uiState.isOffline,
                    onClick = { onSearchOnline(uiState.query.trim()) },
                )
            }
        }
    }
}

@Composable
private fun GroupHeader(
    group: SearchResultGroup,
    isExpanded: Boolean,
    onSeeAll: () -> Unit,
    onShowAllGroups: () -> Unit,
) {
    Column {
        HorizontalDivider()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 4.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(group.source.labelRes),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .weight(1f)
                    .semantics { heading() },
            )
            when {
                isExpanded -> TextButton(onClick = onShowAllGroups) {
                    Text(stringResource(R.string.search_show_all_groups))
                }

                group.hits.size > GROUP_PREVIEW_SIZE -> TextButton(onClick = onSeeAll) {
                    // A full group is capped by the search, so there may be more than it shows.
                    val count = group.hits.size
                    Text(
                        if (count >= UniversalSearchUseCase.DEFAULT_LIMIT_PER_SOURCE) {
                            stringResource(R.string.search_see_all_capped, count)
                        } else {
                            pluralStringResource(R.plurals.search_see_all, count, count)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchHitRow(hit: SearchHit, terms: List<String>, onClick: () -> Unit) {
    val highlight = SpanStyle(color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
    val details = listOfNotNull(
        (hit.target as? SearchTarget.Favorite)?.item?.source?.let { stringResource(it.labelRes) },
        // The date first: a long subtitle (a launch pad) would push it out of the line.
        hit.date?.formatMedium(),
        hit.subtitle,
    ).joinToString(" · ")

    ListItem(
        headlineContent = {
            Text(
                text = hit.title.highlighted(terms, highlight),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = if (details.isNotEmpty() || hit.snippet != null) {
            {
                Column {
                    if (details.isNotEmpty()) {
                        Text(text = details, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    hit.snippet?.let { snippet ->
                        Text(
                            text = snippet.highlighted(terms, highlight),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        } else {
            null
        },
        leadingContent = { HitThumbnail(hit) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
private fun HitThumbnail(hit: SearchHit) {
    val shape = RoundedCornerShape(8.dp)
    val modifier = Modifier
        .size(56.dp)
        .clip(shape)
    if (hit.imageUrl != null) {
        NasaAsyncImage(
            url = hit.imageUrl,
            contentDescription = null,
            modifier = modifier,
            fallbackIcon = hit.source.icon,
        )
    } else {
        Box(
            modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = hit.source.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SearchOnlineAction(isOffline: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .windowInsetsPadding(WindowInsets.navigationBars),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedButton(
            onClick = onClick,
            enabled = !isOffline,
            modifier = Modifier.testTag(SEARCH_ONLINE_BUTTON_TEST_TAG),
        ) {
            Icon(Icons.Outlined.ImageSearch, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(
                text = stringResource(R.string.search_online_library),
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        if (isOffline) {
            Text(
                text = stringResource(R.string.search_online_needs_connection),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** [this] with every occurrence of a query term drawn in [style]. */
internal fun String.highlighted(terms: List<String>, style: SpanStyle): AnnotatedString = buildAnnotatedString {
    append(this@highlighted)
    SearchText.matchRanges(this@highlighted, terms).forEach { range ->
        addStyle(style, range.first, range.last + 1)
    }
}

internal val SearchSource.labelRes: Int
    get() = when (this) {
        SearchSource.APOD -> R.string.search_source_apod
        SearchSource.FAVORITES -> R.string.search_source_favorites
        SearchSource.NASA_LIBRARY -> R.string.search_source_nasa_library
        SearchSource.EXOPLANETS -> R.string.search_source_exoplanets
        SearchSource.SMALL_BODIES -> R.string.search_source_small_bodies
        SearchSource.EARTH_EVENTS -> R.string.search_source_earth_events
        SearchSource.LAUNCHES -> R.string.search_source_launches
        SearchSource.SKY_EVENTS -> R.string.search_source_sky_events
        SearchSource.NEWS -> R.string.search_source_news
        SearchSource.COMETS -> R.string.search_source_comets
        SearchSource.EARTHQUAKES -> R.string.search_source_earthquakes
        SearchSource.PODCASTS -> R.string.search_source_podcasts
    }

private val FavoriteSource.labelRes: Int
    get() = when (this) {
        FavoriteSource.APOD -> R.string.search_source_apod
        FavoriteSource.EPIC -> R.string.search_favorite_epic
        FavoriteSource.NASA_LIBRARY -> R.string.search_source_nasa_library
        FavoriteSource.MARS -> R.string.search_favorite_mars
        FavoriteSource.EXOPLANET -> R.string.search_source_exoplanets
        FavoriteSource.NASA_IMAGE_OF_THE_DAY -> R.string.search_favorite_image_of_the_day
        FavoriteSource.NASA_PODCAST_EPISODE -> R.string.search_favorite_podcast_episode
    }

private val SearchSource.icon: ImageVector
    get() = when (this) {
        SearchSource.APOD -> Icons.Outlined.Photo
        SearchSource.FAVORITES -> Icons.Outlined.Favorite
        SearchSource.NASA_LIBRARY -> Icons.Outlined.Image
        SearchSource.EXOPLANETS -> Icons.Outlined.Public
        SearchSource.SMALL_BODIES -> Icons.Outlined.Explore
        SearchSource.EARTH_EVENTS -> Icons.Outlined.Public
        SearchSource.LAUNCHES -> Icons.Outlined.RocketLaunch
        SearchSource.SKY_EVENTS -> Icons.Outlined.Event
        SearchSource.NEWS -> Icons.Outlined.Newspaper
        SearchSource.COMETS -> Icons.Outlined.AutoAwesome
        SearchSource.EARTHQUAKES -> Icons.Outlined.Waves
        SearchSource.PODCASTS -> Icons.Outlined.Podcasts
    }

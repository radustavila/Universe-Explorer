package com.radustavila.nasaexplorer.feature.exoplanets

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.radustavila.nasaexplorer.core.designsystem.component.EmptyState
import com.radustavila.nasaexplorer.core.designsystem.component.LoadingState
import com.radustavila.nasaexplorer.core.designsystem.component.MessageState
import com.radustavila.nasaexplorer.core.designsystem.component.OfflineBanner
import com.radustavila.nasaexplorer.core.designsystem.component.OfflineState
import com.radustavila.nasaexplorer.core.model.Exoplanet
import com.radustavila.nasaexplorer.core.model.ExoplanetList
import com.radustavila.nasaexplorer.core.model.ExoplanetUnits
import kotlin.math.ln

const val EXOPLANET_LIST_TEST_TAG = "exoplanet-list"
const val EXOPLANET_REFRESH_FAILED_TEST_TAG = "exoplanet-refresh-failed"

@Composable
fun ExoplanetsScreen(
    onBackClick: () -> Unit,
    onExoplanetClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ExoplanetsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    ExoplanetsContent(
        uiState = uiState,
        onBackClick = onBackClick,
        onExoplanetClick = onExoplanetClick,
        onListSelected = viewModel::onListSelected,
        onRefresh = viewModel::onRefresh,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExoplanetsContent(
    uiState: ExoplanetsUiState,
    onBackClick: () -> Unit,
    onExoplanetClick: (String) -> Unit,
    onListSelected: (ExoplanetList) -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        // See FeedScreen: insets belong to the top bar and to the app scaffold's navigation bar.
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.exoplanets_title)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.exoplanets_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (uiState.isOffline) OfflineBanner()
            PrimaryTabRow(selectedTabIndex = uiState.selectedList.ordinal) {
                ExoplanetList.entries.forEach { list ->
                    Tab(
                        selected = list == uiState.selectedList,
                        onClick = { onListSelected(list) },
                        text = { Text(stringResource(list.labelRes)) },
                    )
                }
            }
            PullToRefreshBox(
                // The first download has its own progress message; the indicator is for refreshes.
                isRefreshing = uiState.isRefreshing && uiState.planets.isNotEmpty(),
                onRefresh = onRefresh,
                modifier = Modifier.fillMaxSize(),
            ) {
                ExoplanetsBody(uiState = uiState, onExoplanetClick = onExoplanetClick, onRetry = onRefresh)
            }
        }
    }
}

@Composable
private fun ExoplanetsBody(
    uiState: ExoplanetsUiState,
    onExoplanetClick: (String) -> Unit,
    onRetry: () -> Unit,
) {
    when {
        uiState.planets.isNotEmpty() -> PlanetList(uiState = uiState, onExoplanetClick = onExoplanetClick)

        uiState.isLoading -> LoadingState()

        uiState.neverSynced && uiState.isOffline -> OfflineState(
            title = stringResource(R.string.exoplanets_offline_empty_title),
            description = stringResource(R.string.exoplanets_offline_empty_description),
            onRetry = onRetry,
        )

        uiState.neverSynced && uiState.lastRefreshFailed -> MessageState(
            icon = Icons.Outlined.ErrorOutline,
            title = stringResource(R.string.exoplanets_error_title),
            description = stringResource(R.string.exoplanets_error_description),
            onRetry = onRetry,
        )

        // Online and waiting for the first download.
        uiState.neverSynced -> FirstDownload()

        // Synced, but this list is empty (e.g. no discoveries yet this year).
        else -> EmptyState(title = stringResource(R.string.exoplanets_empty_title))
    }
}

@Composable
private fun FirstDownload() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
        Text(
            text = stringResource(R.string.exoplanets_loading_title),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun PlanetList(
    uiState: ExoplanetsUiState,
    onExoplanetClick: (String) -> Unit,
) {
    // One scroll position per list: switching tabs starts the other list at its top.
    val listState = when (uiState.selectedList) {
        ExoplanetList.NEWEST -> rememberLazyListState()
        ExoplanetList.NEAREST -> rememberLazyListState()
        ExoplanetList.EARTH_SIZED -> rememberLazyListState()
    }
    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .testTag(EXOPLANET_LIST_TEST_TAG),
        contentPadding = PaddingValues(bottom = 16.dp),
    ) {
        item(key = "header") {
            ListHeader(uiState)
        }
        items(items = uiState.planets, key = Exoplanet::name) { planet ->
            ExoplanetRow(planet = planet, onClick = { onExoplanetClick(planet.name) })
            HorizontalDivider()
        }
    }
}

@Composable
private fun ListHeader(uiState: ExoplanetsUiState) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        if (uiState.lastRefreshFailed && !uiState.isOffline) {
            Text(
                text = stringResource(R.string.exoplanets_refresh_failed),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier
                    .padding(bottom = 8.dp)
                    .testTag(EXOPLANET_REFRESH_FAILED_TEST_TAG),
            )
        }
        val description = when (uiState.selectedList) {
            ExoplanetList.NEWEST ->
                stringResource(R.string.exoplanets_list_newest_description, uiState.newestSinceYear)
            ExoplanetList.NEAREST -> stringResource(R.string.exoplanets_list_nearest_description)
            ExoplanetList.EARTH_SIZED -> stringResource(R.string.exoplanets_list_earth_sized_description)
        }
        Text(text = description, style = MaterialTheme.typography.bodyMedium)
        uiState.confirmedPlanets?.let { count ->
            Text(
                text = stringResource(R.string.exoplanets_confirmed_count, formatCount(count, currentLocale())),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ExoplanetRow(planet: Exoplanet, onClick: () -> Unit) {
    val locale = currentLocale()
    val unknown = stringResource(R.string.exoplanets_unknown)
    ListItem(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        leadingContent = { PlanetDot(radiusEarth = planet.radiusEarth) },
        headlineContent = { Text(planet.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Text(
                text = stringResource(
                    R.string.exoplanets_row_summary,
                    planet.hostName,
                    stringResource(planet.discoveryMethod.labelRes),
                    planet.discoveryPublished?.let { formatYearMonth(it, locale) } ?: planet.discoveryYear.toString(),
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        trailingContent = {
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = planet.radiusEarth?.let {
                        stringResource(R.string.exoplanets_times_earth, formatQuantity(it, locale))
                    } ?: unknown,
                    style = MaterialTheme.typography.labelLarge,
                )
                Text(
                    text = planet.distanceLightYears?.let {
                        stringResource(R.string.exoplanets_light_years, formatLightYears(it, locale))
                    } ?: unknown,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    )
}

/**
 * A dot whose size grows with the planet's radius on a log scale, so Earths and Jupiters both
 * stay readable in a 40 dp slot.
 */
@Composable
private fun PlanetDot(radiusEarth: Double?) {
    val color = if (radiusEarth == null) {
        MaterialTheme.colorScheme.outlineVariant
    } else {
        MaterialTheme.colorScheme.tertiary
    }
    Box(modifier = Modifier.size(DOT_SLOT.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(DOT_SLOT.dp)) {
            val fraction = radiusEarth?.let { dotFraction(it) } ?: UNKNOWN_FRACTION
            drawCircle(color = color, radius = size.minDimension / 2f * fraction)
        }
    }
}

/** 0.25 for Earth, 1.0 at twice Jupiter and above. */
internal fun dotFraction(radiusEarth: Double): Float {
    val max = ln(ExoplanetUnits.EARTH_RADII_PER_JUPITER_RADIUS * 2)
    val scaled = ln(radiusEarth.coerceAtLeast(MIN_RADIUS)) / max
    return (MIN_FRACTION + (1f - MIN_FRACTION) * scaled.toFloat()).coerceIn(SMALLEST_FRACTION, 1f)
}

private const val DOT_SLOT = 40
private const val MIN_FRACTION = 0.25f
private const val SMALLEST_FRACTION = 0.12f
private const val UNKNOWN_FRACTION = 0.25f
private const val MIN_RADIUS = 0.3

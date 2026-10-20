package com.radustavila.nasaexplorer.feature.earthevents

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.ImageNotSupported
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.SatelliteAlt
import androidx.compose.material.icons.outlined.SyncProblem
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.radustavila.nasaexplorer.core.designsystem.component.OfflineBanner
import com.radustavila.nasaexplorer.core.designsystem.util.formatMedium
import com.radustavila.nasaexplorer.core.map.SpaceMap
import com.radustavila.nasaexplorer.core.map.TileLoadStatus
import com.radustavila.nasaexplorer.core.map.gibs.GibsImagery
import com.radustavila.nasaexplorer.core.map.overlay.MapOverlay
import com.radustavila.nasaexplorer.core.map.rememberSpaceMapState
import com.radustavila.nasaexplorer.core.model.EarthEventCategory

const val EARTH_EVENTS_FILTER_TEST_TAG = "earth-events-filter"
const val EARTH_EVENTS_IMAGERY_BAR_TEST_TAG = "earth-events-imagery-bar"
const val EARTH_EVENTS_TILE_BANNER_TEST_TAG = "earth-events-tile-banner"
const val EARTH_EVENTS_EMPTY_TEST_TAG = "earth-events-empty"

/** Zoom the map flies to when it shows an event's imagery: a region, a few hundred km across. */
private const val EVENT_IMAGERY_ZOOM = 5.0

/** The live map's slot, so tests can render the screen without MapLibre's native renderer. */
typealias EarthEventsMap = @Composable (
    imagery: GibsImagery,
    overlays: List<MapOverlay>,
    onEventClick: (eventId: String) -> Unit,
    onMapClick: () -> Unit,
    modifier: Modifier,
) -> Unit

@Composable
internal fun EarthEventsRoute(
    onBackClick: () -> Unit,
    viewModel: EarthEventsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val mapState = rememberSpaceMapState()
    val context = LocalContext.current

    EarthEventsScreen(
        uiState = uiState,
        tileStatus = mapState.tileStatus,
        onBackClick = onBackClick,
        onRefresh = viewModel::onRefresh,
        onCategoryToggled = viewModel::onCategoryToggled,
        onEventSelected = viewModel::onEventSelected,
        onEventDismissed = viewModel::onEventDismissed,
        onShowImagery = {
            uiState.selectedEvent?.let { event ->
                mapState.animateTo(event.position, zoom = maxOf(mapState.camera.zoom, EVENT_IMAGERY_ZOOM))
            }
            viewModel.onShowImagery()
        },
        onHideImagery = viewModel::onHideImagery,
        onOpenSource = context::openUrl,
        onMessageShown = viewModel::onMessageShown,
        map = { imagery, overlays, onEventClick, onMapClick, modifier ->
            SpaceMap(
                imagery = imagery,
                state = mapState,
                overlays = overlays,
                onShapeClick = { click -> onEventClick(eventIdOf(click.shapeId)) },
                onMapClick = { onMapClick() },
                modifier = modifier,
            )
        },
    )
}

/**
 * Stateless events map: banners, the category filter, the map with event overlays, and the sheet
 * of the selected event.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EarthEventsScreen(
    uiState: EarthEventsUiState,
    tileStatus: TileLoadStatus,
    onBackClick: () -> Unit,
    onRefresh: () -> Unit,
    onCategoryToggled: (EarthEventCategory) -> Unit,
    onEventSelected: (String) -> Unit,
    onEventDismissed: () -> Unit,
    onShowImagery: () -> Unit,
    onHideImagery: () -> Unit,
    onOpenSource: (String) -> Unit,
    onMessageShown: () -> Unit,
    modifier: Modifier = Modifier,
    map: EarthEventsMap,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val message = uiState.message
    val messageText = message?.let { stringResource(it.textRes) }
    LaunchedEffect(message) {
        if (messageText != null) {
            snackbarHostState.showSnackbar(messageText)
            onMessageShown()
        }
    }

    Scaffold(
        modifier = modifier,
        // See FeedScreen: insets belong to the top bar and to the app scaffold's navigation bar.
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.earth_events_title)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.earth_events_back))
                    }
                },
                actions = {
                    if (uiState.isRefreshing) {
                        CircularProgressIndicator(modifier = Modifier.padding(12.dp).size(24.dp), strokeWidth = 2.dp)
                    } else {
                        IconButton(onClick = onRefresh) {
                            Icon(Icons.Outlined.Refresh, contentDescription = stringResource(R.string.earth_events_refresh))
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            MapBanner(isOffline = uiState.isOffline, tileStatus = tileStatus)
            uiState.eventImagery?.let { imagery -> ImageryBar(imagery, onHideImagery) }
            if (uiState.categoryCounts.isNotEmpty()) {
                CategoryFilter(
                    counts = uiState.categoryCounts,
                    hidden = uiState.hiddenCategories,
                    onCategoryToggled = onCategoryToggled,
                )
            }

            BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
                // The selection lives in its own overlay, so picking an event leaves the (large) base
                // overlays untouched and the map only redraws the highlight.
                val baseOverlays = remember(uiState.events, uiState.hiddenCategories) {
                    baseEventOverlays(uiState.events, uiState.hiddenCategories)
                }
                val selection = remember(uiState.events, uiState.hiddenCategories, uiState.selectedEventId) {
                    selectionOverlay(uiState.events, uiState.hiddenCategories, uiState.selectedEventId)
                }
                val overlays = remember(baseOverlays, selection) { baseOverlays + selection }
                map(uiState.imagery, overlays, onEventSelected, onEventDismissed, Modifier.fillMaxSize())

                if (uiState.isLoaded && uiState.events.isEmpty()) {
                    EmptyNotice(
                        isOffline = uiState.isOffline,
                        isRefreshing = uiState.isRefreshing,
                        modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    )
                }

                uiState.selectedEvent?.let { event ->
                    val isLandscape = maxWidth > maxHeight
                    EventSheet(
                        event = event,
                        isShowingImagery = uiState.eventImagery?.eventId == event.id,
                        isLoadingImagery = uiState.isLoadingImagery,
                        onShowImagery = onShowImagery,
                        onHideImagery = onHideImagery,
                        onOpenSource = onOpenSource,
                        onClose = onEventDismissed,
                        modifier = Modifier
                            .align(if (isLandscape) Alignment.BottomStart else Alignment.BottomCenter)
                            .padding(12.dp)
                            .then(if (isLandscape) Modifier.width(SHEET_LANDSCAPE_WIDTH) else Modifier.fillMaxWidth()),
                    )
                }
            }
        }
    }
}

@Composable
private fun MapBanner(isOffline: Boolean, tileStatus: TileLoadStatus) {
    when {
        isOffline -> OfflineBanner()
        tileStatus == TileLoadStatus.NetworkError ->
            InfoBar(Icons.Outlined.SyncProblem, stringResource(R.string.earth_events_tiles_failed), EARTH_EVENTS_TILE_BANNER_TEST_TAG)
        tileStatus == TileLoadStatus.ImageryUnavailable ->
            InfoBar(
                Icons.Outlined.ImageNotSupported,
                stringResource(R.string.earth_events_imagery_unavailable),
                EARTH_EVENTS_TILE_BANNER_TEST_TAG,
            )
        else -> Unit
    }
}

@Composable
private fun ImageryBar(imagery: EventImagery, onHideImagery: () -> Unit) {
    InfoBar(
        icon = Icons.Outlined.SatelliteAlt,
        text = stringResource(R.string.earth_events_imagery_caption, imagery.layerName, imagery.date.formatMedium()),
        testTag = EARTH_EVENTS_IMAGERY_BAR_TEST_TAG,
        action = {
            TextButton(onClick = onHideImagery) { Text(stringResource(R.string.earth_events_latest_imagery)) }
        },
    )
}

@Composable
private fun InfoBar(
    icon: ImageVector,
    text: String,
    testTag: String,
    action: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.tertiaryContainer)
            .padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onTertiaryContainer)
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(vertical = 4.dp),
        )
        action?.invoke()
    }
}

@Composable
private fun CategoryFilter(
    counts: List<Pair<EarthEventCategory, Int>>,
    hidden: Set<EarthEventCategory>,
    onCategoryToggled: (EarthEventCategory) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp)
            .testTag(EARTH_EVENTS_FILTER_TEST_TAG),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        counts.forEach { (category, count) ->
            FilterChip(
                selected = category !in hidden,
                onClick = { onCategoryToggled(category) },
                label = { Text(stringResource(R.string.earth_events_filter_label, stringResource(category.labelRes), count)) },
                leadingIcon = { CategoryDot(category) },
            )
        }
    }
}

@Composable
internal fun CategoryDot(category: EarthEventCategory, modifier: Modifier = Modifier) {
    Box(modifier.size(10.dp).background(category.color, CircleShape))
}

@Composable
private fun EmptyNotice(isOffline: Boolean, isRefreshing: Boolean, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.testTag(EARTH_EVENTS_EMPTY_TEST_TAG),
        shape = MaterialTheme.shapes.large,
        tonalElevation = 3.dp,
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Icons.Outlined.Public, contentDescription = null)
            Text(
                text = stringResource(
                    when {
                        isRefreshing -> R.string.earth_events_loading
                        isOffline -> R.string.earth_events_empty_offline
                        else -> R.string.earth_events_empty
                    },
                ),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
        }
    }
}

private val EarthEventsMessage.textRes: Int
    get() = when (this) {
        EarthEventsMessage.RefreshFailed -> R.string.earth_events_refresh_failed
        EarthEventsMessage.ImageryFallback -> R.string.earth_events_imagery_fallback
    }

private val SHEET_LANDSCAPE_WIDTH = 380.dp

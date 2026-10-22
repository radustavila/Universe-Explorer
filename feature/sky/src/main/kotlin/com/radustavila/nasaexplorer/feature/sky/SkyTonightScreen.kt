package com.radustavila.nasaexplorer.feature.sky

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.radustavila.nasaexplorer.core.designsystem.adaptive.PanelColumnMinWidth
import com.radustavila.nasaexplorer.core.designsystem.component.FreshnessLabel
import com.radustavila.nasaexplorer.core.designsystem.component.LoadingState
import com.radustavila.nasaexplorer.core.designsystem.component.OfflineBanner
import com.radustavila.nasaexplorer.core.designsystem.error.asText
import com.radustavila.nasaexplorer.core.designsystem.notification.rememberNotificationPermissionToggle
import com.radustavila.nasaexplorer.core.map.MapCamera
import com.radustavila.nasaexplorer.core.map.SpaceMap
import com.radustavila.nasaexplorer.core.map.TileLoadStatus
import com.radustavila.nasaexplorer.core.map.overlay.GeoPoint
import com.radustavila.nasaexplorer.core.map.rememberSpaceMapState
import com.radustavila.nasaexplorer.core.model.City
import com.radustavila.nasaexplorer.core.model.GeoCoordinate
import com.radustavila.nasaexplorer.feature.sky.component.IssPassesCard
import com.radustavila.nasaexplorer.feature.sky.component.IssTrackCard
import com.radustavila.nasaexplorer.feature.sky.component.IssTrackMapSlot
import com.radustavila.nasaexplorer.feature.sky.component.LocationDialog
import com.radustavila.nasaexplorer.feature.sky.component.LocationHeader
import com.radustavila.nasaexplorer.feature.sky.component.LocationPrompt
import com.radustavila.nasaexplorer.feature.sky.component.MoonCard
import com.radustavila.nasaexplorer.feature.sky.component.NightCard
import com.radustavila.nasaexplorer.feature.sky.component.PeopleInSpaceCard
import com.radustavila.nasaexplorer.feature.sky.component.PlanetsCard
import com.radustavila.nasaexplorer.feature.sky.component.rememberCoarseLocationRequest
import java.time.Instant

const val SKY_LIST_TEST_TAG = "sky-list"

/** Most of the world in a card-sized map, so the whole track around the station is in view. */
private const val MAP_ZOOM = 0.0

@Composable
fun SkyTonightScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    nextInTheSky: (@Composable () -> Unit)? = null,
    viewModel: SkyTonightViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showLocationDialog by rememberSaveable { mutableStateOf(false) }

    val onUseMyLocation = rememberCoarseLocationRequest(
        onGranted = viewModel::onLocateDevice,
        onDenied = viewModel::onLocationPermissionDenied,
    )

    val reminders = rememberNotificationPermissionToggle(
        isEnabled = uiState.remindersEnabled,
        onEnabledChange = viewModel::onRemindersChange,
        snackbarHostState = snackbarHostState,
        blockedMessage = stringResource(R.string.sky_iss_notifications_blocked),
    )

    SkyMessageEffect(
        message = uiState.message,
        snackbarHostState = snackbarHostState,
        onChooseCity = { showLocationDialog = true },
        onShown = viewModel::onMessageShown,
    )

    val mapState = rememberSpaceMapState(MapCamera(center = GeoPoint(latitude = 0.0, longitude = 0.0), zoom = MAP_ZOOM))
    SkyTonightContent(
        uiState = uiState,
        remindersOn = reminders.isOn,
        trackMap = { overlays, focus, mapModifier ->
            // Centre on the station once, when its first position arrives; after that the user pans.
            var centered by rememberSaveable { mutableStateOf(false) }
            LaunchedEffect(focus != null) {
                if (focus != null && !centered) {
                    mapState.moveTo(MapCamera(center = focus, zoom = MAP_ZOOM))
                    centered = true
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                SpaceMap(imagery = null, state = mapState, overlays = overlays, modifier = mapModifier)
                if (mapState.tileStatus == TileLoadStatus.NetworkError) {
                    Text(
                        text = stringResource(R.string.sky_track_map_offline),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        onBackClick = onBackClick,
        onUseMyLocation = onUseMyLocation,
        onChooseLocation = { showLocationDialog = true },
        onRemindersClick = reminders.onClick,
        onRefresh = viewModel::onRefresh,
        snackbarHostState = snackbarHostState,
        modifier = modifier,
        nextInTheSky = nextInTheSky,
    )

    if (showLocationDialog) {
        LocationDialog(
            citySearch = uiState.citySearch,
            onQueryChange = viewModel::onCityQueryChange,
            onCitySelected = { city: City ->
                viewModel.onCitySelected(city)
                showLocationDialog = false
            },
            onCoordinatesEntered = { coordinate: GeoCoordinate ->
                viewModel.onCoordinatesEntered(coordinate)
                showLocationDialog = false
            },
            onDismiss = {
                viewModel.onCityQueryChange("")
                showLocationDialog = false
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SkyTonightContent(
    uiState: SkyTonightUiState,
    remindersOn: Boolean,
    trackMap: IssTrackMapSlot,
    onBackClick: () -> Unit,
    onUseMyLocation: () -> Unit,
    onChooseLocation: () -> Unit,
    onRemindersClick: () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    nextInTheSky: (@Composable () -> Unit)? = null,
) {
    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.sky_title)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.sky_back))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            if (uiState.isOffline) OfflineBanner()
            when {
                !uiState.isLocationLoaded -> LoadingState()
                else -> PullToRefreshBox(
                    isRefreshing = uiState.isRefreshing,
                    onRefresh = onRefresh,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    SkyList(
                        uiState = uiState,
                        remindersOn = remindersOn,
                        trackMap = trackMap,
                        onUseMyLocation = onUseMyLocation,
                        onChooseLocation = onChooseLocation,
                        onRemindersClick = onRemindersClick,
                        nextInTheSky = nextInTheSky,
                    )
                }
            }
        }
    }
}

@Composable
private fun SkyList(
    uiState: SkyTonightUiState,
    remindersOn: Boolean,
    trackMap: IssTrackMapSlot,
    onUseMyLocation: () -> Unit,
    onChooseLocation: () -> Unit,
    onRemindersClick: () -> Unit,
    nextInTheSky: (@Composable () -> Unit)?,
) {
    val location = uiState.location
    // One column on a phone held upright; side by side on wider windows (a phone in landscape
    // included), each card as tall as it needs. The location spans the full width above them.
    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Adaptive(minSize = PanelColumnMinWidth),
        modifier = Modifier
            .fillMaxSize()
            .testTag(SKY_LIST_TEST_TAG),
        contentPadding = PaddingValues(16.dp),
        verticalItemSpacing = 12.dp,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Always an item, even while it draws nothing: added later above the first card, the list
        // would keep that card in place and scroll the line out of view.
        item(key = "freshness", span = StaggeredGridItemSpan.FullLine) { FreshnessLabel(uiState.freshness) }
        if (location == null) {
            item(key = "prompt", span = StaggeredGridItemSpan.FullLine) {
                LocationPrompt(
                    isLocating = uiState.isLocating,
                    onUseMyLocation = onUseMyLocation,
                    onChooseCity = onChooseLocation,
                )
            }
        } else {
            item(key = "location", span = StaggeredGridItemSpan.FullLine) {
                LocationHeader(
                    location = location,
                    isLocating = uiState.isLocating,
                    onUseMyLocation = onUseMyLocation,
                    onChangeLocation = onChooseLocation,
                )
            }
            val sky = uiState.sky
            if (sky == null) {
                item(key = "computing", span = StaggeredGridItemSpan.FullLine) { Box(Modifier.padding(24.dp)) { LoadingState() } }
            } else {
                item(key = "night") { NightCard(sky.night) }
                item(key = "moon") { MoonCard(sky.moon, southernHemisphere = location.coordinate.latitude < 0) }
                item(key = "planets") { PlanetsCard(sky.planets) }
            }
            uiState.passes?.let { forecast ->
                item(key = "iss") {
                    IssPassesCard(forecast = forecast, remindersOn = remindersOn, onRemindersClick = onRemindersClick)
                }
            }
        }
        // Works without a place too: the calendar then shows the global facts.
        nextInTheSky?.let { card -> item(key = "next-in-the-sky") { card() } }
        item(key = "track") { IssTrackCard(track = uiState.track, observer = location?.coordinate, map = trackMap) }
        item(key = "people") {
            PeopleInSpaceCard(people = uiState.people, now = uiState.sky?.computedAt ?: Instant.now())
        }
    }
}

@Composable
private fun SkyMessageEffect(
    message: SkyMessage?,
    snackbarHostState: SnackbarHostState,
    onChooseCity: () -> Unit,
    onShown: () -> Unit,
) {
    val context = LocalContext.current
    val denied = stringResource(R.string.sky_location_denied)
    val unavailable = stringResource(R.string.sky_location_unavailable)
    val chooseCity = stringResource(R.string.sky_choose_city)
    val settings = stringResource(R.string.sky_location_settings)
    val refreshError = (message as? SkyMessage.RefreshFailed)?.error?.asText()

    LaunchedEffect(message) {
        when (message) {
            null -> return@LaunchedEffect
            SkyMessage.LocationPermissionDenied -> {
                val result = snackbarHostState.showSnackbar(denied, actionLabel = chooseCity, duration = SnackbarDuration.Long)
                if (result == SnackbarResult.ActionPerformed) onChooseCity()
            }

            SkyMessage.LocationUnavailable -> {
                val result = snackbarHostState.showSnackbar(unavailable, actionLabel = settings, duration = SnackbarDuration.Long)
                if (result == SnackbarResult.ActionPerformed) context.openSettings(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            }

            is SkyMessage.RefreshFailed -> snackbarHostState.showSnackbar(refreshError.orEmpty())
        }
        onShown()
    }
}

private fun Context.openSettings(action: String, configure: Intent.() -> Unit = {}) {
    val intent = Intent(action).apply(configure)
    try {
        startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        // A stripped-down system image without that settings page.
    }
}

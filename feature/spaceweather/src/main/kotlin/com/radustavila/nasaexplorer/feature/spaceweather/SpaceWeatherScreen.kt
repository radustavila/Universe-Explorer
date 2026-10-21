package com.radustavila.nasaexplorer.feature.spaceweather

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.radustavila.nasaexplorer.core.designsystem.component.LoadingState
import com.radustavila.nasaexplorer.core.designsystem.component.OfflineBanner
import com.radustavila.nasaexplorer.core.designsystem.component.OfflineState
import com.radustavila.nasaexplorer.core.designsystem.component.EmptyState
import com.radustavila.nasaexplorer.core.designsystem.motion.rememberReducedMotion
import com.radustavila.nasaexplorer.core.designsystem.util.formatDateTime
import com.radustavila.nasaexplorer.core.map.MapCamera
import com.radustavila.nasaexplorer.core.map.SpaceMap
import com.radustavila.nasaexplorer.core.map.SpaceMapState
import com.radustavila.nasaexplorer.core.map.overlay.GeoPoint
import com.radustavila.nasaexplorer.core.map.overlay.MapMarker
import com.radustavila.nasaexplorer.core.map.overlay.MapOverlay
import com.radustavila.nasaexplorer.core.map.rememberSpaceMapState
import com.radustavila.nasaexplorer.feature.spaceweather.components.AuroraAlertCard
import com.radustavila.nasaexplorer.feature.spaceweather.components.AuroraLocationDialog
import com.radustavila.nasaexplorer.feature.spaceweather.components.AuroraOval
import com.radustavila.nasaexplorer.feature.spaceweather.components.CmeCard
import com.radustavila.nasaexplorer.feature.spaceweather.components.KpForecastBars
import com.radustavila.nasaexplorer.feature.spaceweather.components.KpGauge
import com.radustavila.nasaexplorer.feature.spaceweather.components.SolarFlareCard

const val SPACE_WEATHER_LIST_TEST_TAG = "space-weather-list"
const val AURORA_MAP_TEST_TAG = "aurora-map"

@Composable
internal fun SpaceWeatherScreen(
    onBackClick: () -> Unit,
    viewModel: SpaceWeatherViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val initialCamera = remember { uiState.hemisphere.camera(uiState.alerts.longitude) }
    val mapState = rememberSpaceMapState(initialCamera)
    val reducedMotion = rememberReducedMotion()

    SpaceWeatherContent(
        uiState = uiState,
        onBackClick = onBackClick,
        onRefresh = viewModel::onRefresh,
        onHemisphereSelected = { hemisphere ->
            viewModel.onHemisphereSelected(hemisphere)
            val center = hemisphere.camera(uiState.alerts.longitude).center
            if (reducedMotion) {
                mapState.moveTo(MapCamera(center, mapState.camera.zoom))
            } else {
                mapState.animateTo(center, mapState.camera.zoom)
            }
        },
        onAlertsEnabledChange = viewModel::onAlertsEnabledChange,
        onAlertLocationChange = viewModel::onAlertLocationChange,
        map = { overlays, modifier -> AuroraSpaceMap(mapState, overlays, modifier) },
    )
}

@Composable
private fun MapPlaceholder(@Suppress("UNUSED_PARAMETER") overlays: List<MapOverlay>, modifier: Modifier) {
    Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant))
}

@Composable
private fun AuroraSpaceMap(state: SpaceMapState, overlays: List<MapOverlay>, modifier: Modifier) {
    // Night lights would hide the oval's faint edge; the plain Blue Marble base reads best under it.
    SpaceMap(imagery = null, state = state, overlays = overlays, modifier = modifier)
}

/**
 * Stateless space weather screen. The map comes in through [map] so tests can render everything
 * else without MapLibre's native renderer.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpaceWeatherContent(
    uiState: SpaceWeatherUiState,
    onBackClick: () -> Unit,
    onRefresh: () -> Unit,
    onHemisphereSelected: (Hemisphere) -> Unit,
    onAlertsEnabledChange: (Boolean) -> Unit,
    onAlertLocationChange: (latitude: Double, longitude: Double, enableAlerts: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    map: @Composable (overlays: List<MapOverlay>, modifier: Modifier) -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    var editingLocation by rememberSaveable { mutableStateOf(false) }
    var enableAlertsAfterLocation by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        // See FeedScreen: insets belong to the top bar and to the app scaffold's navigation bar.
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.space_weather_title)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.space_weather_back))
                    }
                },
            )
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing && uiState.hasData,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            when {
                uiState.hasData -> SpaceWeatherList(
                    uiState = uiState,
                    snackbarHostState = snackbarHostState,
                    onHemisphereSelected = onHemisphereSelected,
                    onAlertsEnabledChange = onAlertsEnabledChange,
                    onEditLocation = { enableAfter ->
                        enableAlertsAfterLocation = enableAfter
                        editingLocation = true
                    },
                    // MapLibre's texture view and a dialog window deadlock the main thread (ANR on
                    // emulator-5556, API 37), so the map steps aside while the dialog is up.
                    map = if (editingLocation) ::MapPlaceholder else map,
                )

                uiState.isOffline -> OfflineState(
                    title = stringResource(R.string.space_weather_offline_title),
                    description = stringResource(R.string.space_weather_offline_description),
                    onRetry = onRefresh,
                )

                uiState.lastRefreshFailed && !uiState.isRefreshing -> EmptyState(
                    title = stringResource(R.string.space_weather_error_title),
                    description = stringResource(R.string.space_weather_error_description),
                    onRetry = onRefresh,
                )

                else -> LoadingState()
            }
        }
    }

    if (editingLocation) {
        AuroraLocationDialog(
            initialLatitude = uiState.alerts.latitude,
            initialLongitude = uiState.alerts.longitude,
            onDismiss = { editingLocation = false },
            onConfirm = { latitude, longitude ->
                editingLocation = false
                onAlertLocationChange(latitude, longitude, enableAlertsAfterLocation)
            },
        )
    }
}

@Composable
private fun SpaceWeatherList(
    uiState: SpaceWeatherUiState,
    snackbarHostState: SnackbarHostState,
    onHemisphereSelected: (Hemisphere) -> Unit,
    onAlertsEnabledChange: (Boolean) -> Unit,
    onEditLocation: (enableAlertsAfter: Boolean) -> Unit,
    map: @Composable (overlays: List<MapOverlay>, modifier: Modifier) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag(SPACE_WEATHER_LIST_TEST_TAG),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (uiState.isOffline) item(key = "offline") { OfflineBanner() }
        item(key = "gauge") {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(horizontal = 16.dp).widthIn(max = CONTENT_MAX_WIDTH),
            ) {
                KpGauge(kp = uiState.currentKp, modifier = Modifier.widthIn(max = GAUGE_MAX_WIDTH).fillMaxWidth(GAUGE_WIDTH_FRACTION))
                UpdatedLine(uiState)
            }
        }
        if (uiState.forecast.isNotEmpty()) {
            item(key = "forecast") {
                Section(title = stringResource(R.string.space_weather_forecast_title)) {
                    KpForecastBars(forecast = uiState.forecast, threshold = uiState.alerts.minimumKp)
                    if (uiState.alerts.minimumKp != null) {
                        Caption(stringResource(R.string.space_weather_forecast_threshold_caption))
                    }
                }
            }
        }
        item(key = "alerts") {
            Section(title = null) {
                AuroraAlertCard(
                    settings = uiState.alerts,
                    onEnabledChange = onAlertsEnabledChange,
                    onEditLocation = onEditLocation,
                    snackbarHostState = snackbarHostState,
                )
            }
        }
        item(key = "sun") {
            Section(title = stringResource(R.string.space_weather_sun_title)) {
                SolarFlareCard(uiState.flare)
                CmeCard(uiState.cme)
            }
        }
        // Last on purpose: the map takes vertical drags for panning, so nothing below it needs
        // scrolling to and the list above it scrolls freely.
        item(key = "aurora") {
            Section(title = stringResource(R.string.space_weather_aurora_title)) {
                HemisphereSelector(selected = uiState.hemisphere, onSelected = onHemisphereSelected)
                map(
                    auroraOverlays(uiState),
                    Modifier
                        .fillMaxWidth()
                        .height(MAP_HEIGHT)
                        .clip(RoundedCornerShape(12.dp))
                        .testTag(AURORA_MAP_TEST_TAG),
                )
                Caption(
                    uiState.aurora?.let { stringResource(R.string.space_weather_aurora_caption, it.forecastFor.formatDateTime()) }
                        ?: stringResource(R.string.space_weather_aurora_missing),
                )
            }
        }
    }
}

/** The probability bands, plus the user's place when alerts know it. */
@Composable
private fun auroraOverlays(uiState: SpaceWeatherUiState): List<MapOverlay> {
    val latitude = uiState.alerts.latitude
    val longitude = uiState.alerts.longitude
    return remember(uiState.aurora, latitude, longitude) {
        val bands = uiState.aurora?.overlays ?: AuroraOval.BANDS.map { MapOverlay("aurora-${it.minProbability}", emptyList()) }
        val place = if (latitude != null && longitude != null) {
            listOf(MapMarker(id = "you", position = GeoPoint(latitude, longitude), color = Color(0xFF57D0E8)))
        } else {
            emptyList()
        }
        bands + MapOverlay(id = "place", shapes = place)
    }
}

@Composable
private fun UpdatedLine(uiState: SpaceWeatherUiState) {
    val time = uiState.currentKpTime ?: uiState.lastUpdatedAt
    val text = when {
        uiState.lastRefreshFailed && time != null -> stringResource(R.string.space_weather_updated_failed, time.formatDateTime())
        time != null -> stringResource(R.string.space_weather_updated, time.formatDateTime())
        else -> return
    }
    Caption(text)
}

@Composable
private fun HemisphereSelector(selected: Hemisphere, onSelected: (Hemisphere) -> Unit) {
    val options = Hemisphere.entries
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, hemisphere ->
            SegmentedButton(
                selected = hemisphere == selected,
                onClick = { onSelected(hemisphere) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                label = {
                    Text(
                        stringResource(
                            if (hemisphere == Hemisphere.North) R.string.space_weather_north else R.string.space_weather_south,
                        ),
                    )
                },
            )
        }
    }
}

@Composable
private fun Section(title: String?, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier.widthIn(max = CONTENT_MAX_WIDTH).fillMaxWidth().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (title != null) Text(title, style = MaterialTheme.typography.titleLarge)
        content()
    }
}

@Composable
private fun Caption(text: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private val MAP_HEIGHT = 300.dp
private val CONTENT_MAX_WIDTH = 640.dp
private val GAUGE_MAX_WIDTH = 320.dp
private const val GAUGE_WIDTH_FRACTION = 0.8f

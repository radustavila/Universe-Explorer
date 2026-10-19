package com.radustavila.nasaexplorer.feature.earthmap

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.ImageNotSupported
import androidx.compose.material.icons.outlined.SyncProblem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
import com.radustavila.nasaexplorer.core.map.rememberSpaceMapState
import java.time.LocalDate
import kotlin.math.roundToInt

const val EARTH_MAP_TILE_BANNER_TEST_TAG = "earth-map-tile-banner"
const val EARTH_MAP_DATE_SLIDER_TEST_TAG = "earth-map-date-slider"
const val EARTH_MAP_DATE_LABEL_TEST_TAG = "earth-map-date-label"

@Composable
internal fun EarthMapRoute(
    onBackClick: () -> Unit,
    viewModel: EarthMapViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val mapState = rememberSpaceMapState()

    EarthMapScreen(
        uiState = uiState,
        tileStatus = mapState.tileStatus,
        onBackClick = onBackClick,
        onModeSelected = viewModel::onModeSelected,
        onDateSelected = viewModel::onDateSelected,
        onPreviousDay = viewModel::onPreviousDay,
        onNextDay = viewModel::onNextDay,
        map = { imagery, modifier -> SpaceMap(imagery = imagery, state = mapState, modifier = modifier) },
    )
}

/**
 * Stateless satellite map screen. The map itself comes in through [map] so tests can render the
 * controls without MapLibre's native renderer.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EarthMapScreen(
    uiState: EarthMapUiState,
    tileStatus: TileLoadStatus,
    onBackClick: () -> Unit,
    onModeSelected: (EarthMapMode) -> Unit,
    onDateSelected: (LocalDate) -> Unit,
    onPreviousDay: () -> Unit,
    onNextDay: () -> Unit,
    modifier: Modifier = Modifier,
    map: @Composable (imagery: GibsImagery, modifier: Modifier) -> Unit,
) {
    Scaffold(
        modifier = modifier,
        // See FeedScreen: insets belong to the top bar and to the app scaffold's navigation bar.
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.earth_map_title)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.earth_map_back))
                    }
                },
            )
        },
    ) { padding ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
            val isLandscape = maxWidth > maxHeight
            val controls = @Composable { controlsModifier: Modifier ->
                EarthMapControls(
                    uiState = uiState,
                    compact = isLandscape,
                    onModeSelected = onModeSelected,
                    onDateSelected = onDateSelected,
                    onPreviousDay = onPreviousDay,
                    onNextDay = onNextDay,
                    modifier = controlsModifier,
                )
            }
            val mapWithBanner = @Composable { mapModifier: Modifier ->
                Column(mapModifier) {
                    MapBanner(isOffline = uiState.isOffline, tileStatus = tileStatus)
                    map(uiState.imagery, Modifier.fillMaxWidth().weight(1f))
                }
            }

            if (isLandscape) {
                Row(Modifier.fillMaxSize()) {
                    mapWithBanner(Modifier.weight(1f).fillMaxHeight())
                    controls(
                        Modifier
                            .width(LANDSCAPE_PANEL_WIDTH)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState()),
                    )
                }
            } else {
                Column(Modifier.fillMaxSize()) {
                    mapWithBanner(Modifier.weight(1f).fillMaxWidth())
                    controls(Modifier.fillMaxWidth())
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
            TileBanner(Icons.Outlined.SyncProblem, stringResource(R.string.earth_map_tiles_failed))
        tileStatus == TileLoadStatus.ImageryUnavailable ->
            TileBanner(Icons.Outlined.ImageNotSupported, stringResource(R.string.earth_map_imagery_unavailable))
        else -> Unit
    }
}

@Composable
private fun TileBanner(icon: ImageVector, text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.tertiaryContainer)
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag(EARTH_MAP_TILE_BANNER_TEST_TAG),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onTertiaryContainer,
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
        )
    }
}

@Composable
private fun EarthMapControls(
    uiState: EarthMapUiState,
    compact: Boolean,
    onModeSelected: (EarthMapMode) -> Unit,
    onDateSelected: (LocalDate) -> Unit,
    onPreviousDay: () -> Unit,
    onNextDay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier, tonalElevation = 2.dp) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ModeSelector(selected = uiState.mode, compact = compact, onModeSelected = onModeSelected)
            // Same height in every mode, so switching layers does not resize the map.
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(min = DATE_AREA_MIN_HEIGHT),
                verticalArrangement = Arrangement.Center,
            ) {
                when (uiState.mode) {
                    EarthMapMode.TrueColor -> DateSelector(uiState, onDateSelected, onPreviousDay, onNextDay)
                    EarthMapMode.NightLights -> Caption(stringResource(R.string.earth_map_night_lights_caption))
                    EarthMapMode.BlueMarble -> Caption(stringResource(R.string.earth_map_blue_marble_caption))
                }
            }
        }
    }
}

@Composable
private fun ModeSelector(selected: EarthMapMode, compact: Boolean, onModeSelected: (EarthMapMode) -> Unit) {
    val modes = EarthMapMode.entries
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        modes.forEachIndexed { index, mode ->
            SegmentedButton(
                selected = mode == selected,
                onClick = { onModeSelected(mode) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = modes.size),
                // The side panel is too narrow for the check mark and the full labels.
                icon = { if (!compact) SegmentedButtonDefaults.Icon(active = mode == selected) },
                label = {
                    Text(
                        text = stringResource(mode.labelRes),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = if (compact) MaterialTheme.typography.labelMedium else LocalTextStyle.current,
                    )
                },
            )
        }
    }
}

@Composable
private fun DateSelector(
    uiState: EarthMapUiState,
    onDateSelected: (LocalDate) -> Unit,
    onPreviousDay: () -> Unit,
    onNextDay: () -> Unit,
) {
    // While dragging, only the label follows the thumb; the map reloads once the finger lifts.
    var draggedDays by remember { mutableStateOf<Float?>(null) }
    val shownDate = draggedDays?.let { uiState.minDate.plusDays(it.roundToInt().toLong()) } ?: uiState.date
    val selectedDays = (uiState.date.toEpochDay() - uiState.minDate.toEpochDay()).toFloat()

    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onPreviousDay, enabled = uiState.date > uiState.minDate) {
            Icon(
                Icons.AutoMirrored.Outlined.KeyboardArrowLeft,
                contentDescription = stringResource(R.string.earth_map_previous_day),
            )
        }
        Text(
            text = shownDate.formatMedium(),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f).testTag(EARTH_MAP_DATE_LABEL_TEST_TAG),
        )
        IconButton(onClick = onNextDay, enabled = uiState.date < uiState.maxDate) {
            Icon(
                Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = stringResource(R.string.earth_map_next_day),
            )
        }
    }
    val sliderDescription = stringResource(R.string.earth_map_date_slider)
    Slider(
        value = draggedDays ?: selectedDays,
        onValueChange = { draggedDays = it },
        onValueChangeFinished = {
            draggedDays?.let { onDateSelected(uiState.minDate.plusDays(it.roundToInt().toLong())) }
            draggedDays = null
        },
        valueRange = 0f..uiState.dayCount.toFloat(),
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = sliderDescription }
            .testTag(EARTH_MAP_DATE_SLIDER_TEST_TAG),
    )
}

@Composable
private fun Caption(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}

private val EarthMapMode.labelRes: Int
    get() = when (this) {
        EarthMapMode.TrueColor -> R.string.earth_map_mode_true_color
        EarthMapMode.NightLights -> R.string.earth_map_mode_night_lights
        EarthMapMode.BlueMarble -> R.string.earth_map_mode_blue_marble
    }

private val LANDSCAPE_PANEL_WIDTH = 360.dp
private val DATE_AREA_MIN_HEIGHT = 96.dp

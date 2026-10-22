package com.radustavila.nasaexplorer.feature.skymap

import android.hardware.GeomagneticField
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.radustavila.nasaexplorer.core.designsystem.motion.rememberReducedMotion
import com.radustavila.nasaexplorer.core.model.SolarSystemBody
import com.radustavila.nasaexplorer.feature.skymap.render.SkyPalette
import com.radustavila.nasaexplorer.feature.skymap.render.direction
import com.radustavila.nasaexplorer.feature.skymap.sensor.hasRotationSensor
import com.radustavila.nasaexplorer.feature.skymap.sensor.rememberDeviceOrientation
import java.time.Duration
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The sky map. [locationSection] lets the user choose a place when none is saved (Sky tonight's
 * location picker, passed in by the app).
 */
@Composable
fun SkyMapScreen(
    onBackClick: () -> Unit,
    locationSection: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SkyMapViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val hasSensor = remember(context) { context.hasRotationSensor() }
    val camera = rememberSkyCameraState(if (hasSensor) CameraMode.COMPASS else CameraMode.TOUCH)
    val declination = remember(uiState.location?.coordinate) {
        uiState.location?.coordinate?.let {
            GeomagneticField(it.latitude.toFloat(), it.longitude.toFloat(), 0f, System.currentTimeMillis()).declination
        } ?: 0f
    }
    val orientation = rememberDeviceOrientation(
        enabled = hasSensor && camera.mode == CameraMode.COMPASS,
        declinationDegrees = declination,
        onView = { camera.sensorView = it },
    )
    SkyMapContent(
        uiState = uiState,
        camera = camera,
        hasCompass = hasSensor,
        isCompassUnsure = orientation.isAccuracyLow,
        onBackClick = onBackClick,
        onSelect = viewModel::onSelect,
        onTimeOffsetChange = viewModel::onTimeOffsetChange,
        onBackToNow = viewModel::onBackToNow,
        onNightModeChange = viewModel::onNightModeChange,
        locationSection = locationSection,
        modifier = modifier,
    )
}

@Composable
internal fun SkyMapContent(
    uiState: SkyMapUiState,
    camera: SkyCameraState,
    hasCompass: Boolean,
    isCompassUnsure: Boolean,
    onBackClick: () -> Unit,
    onSelect: (SkyTarget?) -> Unit,
    onTimeOffsetChange: (Duration) -> Unit,
    onBackToNow: () -> Unit,
    onNightModeChange: (Boolean) -> Unit,
    locationSection: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = if (uiState.isNightMode) SkyPalette.NIGHT else SkyPalette.REGULAR
    SkyMapTheme(isNightMode = uiState.isNightMode) {
        val sun = uiState.bodies?.solarSystem?.firstOrNull { it.body == SolarSystemBody.SUN }
        Box(modifier.fillMaxSize().background(palette.background(sun?.horizontal?.altitudeDegrees))) {
            val sky = uiState.sky
            when {
                !uiState.isLocationLoaded -> Unit
                uiState.location == null -> LocationNeeded(locationSection, Modifier.align(Alignment.Center))
                sky == null -> {
                    val loading = stringResource(R.string.sky_map_loading)
                    CircularProgressIndicator(
                        Modifier.align(Alignment.Center).semantics { contentDescription = loading },
                    )
                }
                else -> SkyMapLayers(
                    uiState = uiState,
                    camera = camera,
                    palette = palette,
                    onSelect = onSelect,
                    onTimeOffsetChange = onTimeOffsetChange,
                    onBackToNow = onBackToNow,
                    hasCompass = hasCompass,
                    isCompassUnsure = isCompassUnsure,
                )
            }
            SkyMapTopBar(
                uiState = uiState,
                camera = camera,
                hasCompass = hasCompass,
                onBackClick = onBackClick,
                onSelect = onSelect,
                onNightModeChange = onNightModeChange,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        }
    }
}

@Composable
private fun SkyMapLayers(
    uiState: SkyMapUiState,
    camera: SkyCameraState,
    palette: SkyPalette,
    onSelect: (SkyTarget?) -> Unit,
    onTimeOffsetChange: (Duration) -> Unit,
    onBackToNow: () -> Unit,
    hasCompass: Boolean,
    isCompassUnsure: Boolean,
) {
    val sky = checkNotNull(uiState.sky)
    val names = rememberSkyNames()
    val reducedMotion = rememberReducedMotion()
    val looking = rememberLookingText(camera)
    // Searching or tapping in touch mode turns the map to the selection.
    var lookedAt by rememberSaveable(stateSaver = SkyTargetSaver) { mutableStateOf<SkyTarget?>(null) }
    LaunchedEffect(uiState.selected) {
        val selected = uiState.selected
        if (selected != null && selected != lookedAt) {
            lookedAt = selected
            selected.direction(sky, uiState.bodies)?.let { camera.lookAt(it, animate = !reducedMotion) }
        }
    }
    Box(Modifier.fillMaxSize()) {
        SkyMapCanvas(
            sky = sky,
            bodies = uiState.bodies,
            camera = camera,
            selected = uiState.selected,
            palette = palette,
            names = names,
            description = stringResource(R.string.sky_map_description, looking),
            onTap = { target ->
                // A tap shows what is there without swinging the map round to it.
                lookedAt = target
                onSelect(target)
            },
            modifier = Modifier.fillMaxSize(),
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .widthIn(max = 560.dp)
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            DirectionHint(
                looking = looking,
                camera = camera,
                hasCompass = hasCompass,
                isCompassUnsure = isCompassUnsure,
            )
            uiState.selected?.let { selected ->
                SelectionCard(
                    uiState = uiState,
                    selected = selected,
                    names = names,
                    onClose = { onSelect(null) },
                )
            }
            TimeBar(
                uiState = uiState,
                onTimeOffsetChange = onTimeOffsetChange,
                onBackToNow = onBackToNow,
            )
        }
    }
}

/** "Looking south-east, 32° up", updated only when the rounded numbers change. */
@Composable
private fun rememberLookingText(camera: SkyCameraState): String {
    val angles by remember(camera) {
        derivedStateOf {
            val view = camera.view
            compassPointRes(view.azimuthDegrees) to view.altitudeDegrees.roundToInt()
        }
    }
    val (direction, altitude) = angles
    return if (altitude >= 0) {
        stringResource(R.string.sky_map_looking, stringResource(direction), altitude.toString())
    } else {
        stringResource(R.string.sky_map_looking_down, stringResource(direction), abs(altitude).toString())
    }
}

@Composable
private fun LocationNeeded(locationSection: @Composable () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.widthIn(max = 480.dp).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.sky_map_location_needed),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        locationSection()
    }
}

package com.radustavila.nasaexplorer.feature.skymap

import android.hardware.GeomagneticField
import android.os.SystemClock
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.radustavila.nasaexplorer.core.designsystem.motion.rememberReducedMotion
import com.radustavila.nasaexplorer.core.model.DeepSkyObject
import com.radustavila.nasaexplorer.core.model.ObservingConditions
import com.radustavila.nasaexplorer.core.model.ObservingLogEntry
import com.radustavila.nasaexplorer.core.model.SolarSystemBody
import com.radustavila.nasaexplorer.feature.skymap.ar.ArBar
import com.radustavila.nasaexplorer.feature.skymap.ar.ArCameraPreview
import com.radustavila.nasaexplorer.feature.skymap.ar.ArProblem
import com.radustavila.nasaexplorer.feature.skymap.ar.ArProblemCard
import com.radustavila.nasaexplorer.feature.skymap.ar.SkyArState
import com.radustavila.nasaexplorer.feature.skymap.ar.rememberArToggle
import com.radustavila.nasaexplorer.feature.skymap.ar.rememberSkyArState
import com.radustavila.nasaexplorer.feature.skymap.deepsky.MarkSeenDialog
import com.radustavila.nasaexplorer.feature.skymap.deepsky.title
import com.radustavila.nasaexplorer.feature.skymap.render.SkyPalette
import com.radustavila.nasaexplorer.feature.skymap.render.deepSkyObject
import com.radustavila.nasaexplorer.feature.skymap.render.direction
import com.radustavila.nasaexplorer.feature.skymap.sensor.hasRotationSensor
import com.radustavila.nasaexplorer.feature.skymap.sensor.rememberDeviceOrientation
import java.time.Duration
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

/**
 * The sky map. [locationSection] lets the user choose a place when none is saved (Sky tonight's
 * location picker, passed in by the app); [onCometClick] opens a comet's details (F18).
 */
@Composable
fun SkyMapScreen(
    onBackClick: () -> Unit,
    onCometClick: (designation: String) -> Unit,
    locationSection: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    onObservingLogClick: () -> Unit = {},
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
    val ar = rememberSkyArState()
    val orientation = rememberDeviceOrientation(
        enabled = hasSensor && camera.mode == CameraMode.COMPASS,
        declinationDegrees = declination,
        onView = {
            camera.sensorView = it
            if (ar.isOn) ar.onView(it.forward, SystemClock.elapsedRealtime())
        },
    )
    val toggleAr = rememberArToggle(ar, hasRotationSensor = hasSensor) {
        // The camera shows the sky now, so the map does too, aimed by the phone.
        camera.mode = CameraMode.COMPASS
        viewModel.onBackToNow()
    }
    if (ar.isOn) {
        LaunchedEffect(ar) {
            while (true) {
                delay(STILL_CHECK_MILLIS)
                if (ar.isStill(SystemClock.elapsedRealtime())) ar.turnOff(ArProblem.TURNED_OFF_WHILE_STILL)
            }
        }
    }
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
        onCometClick = onCometClick,
        locationSection = locationSection,
        modifier = modifier,
        ar = ar,
        onArClick = toggleAr,
        arCamera = { ArCameraPreview(ar, it) },
        deepSky = DeepSkyActions(
            onLayerChange = viewModel::onDeepSkyLayerChange,
            onMarkSeen = viewModel::onMarkSeen,
            onRemoveSeen = viewModel::onRemoveSeen,
            onObservingLogClick = onObservingLogClick,
        ),
    )
}

/** What the deep-sky layer (F23) can do from the map: switch it, log an object, open the log. */
internal class DeepSkyActions(
    val onLayerChange: (Boolean) -> Unit = {},
    val onMarkSeen: (objectId: String, observedOn: LocalDate, conditions: ObservingConditions?, note: String?) -> Unit =
        { _, _, _, _ -> },
    val onRemoveSeen: (objectId: String) -> Unit = {},
    val onObservingLogClick: () -> Unit = {},
)

/** Ties the map to the camera while AR is on: its lens, and AR's end when the map is moved by touch. */
@Composable
private fun LinkArToCamera(ar: SkyArState, camera: SkyCameraState) {
    LaunchedEffect(ar, camera) {
        // The map takes the lens's field of view once the camera runs, and lets it go with AR.
        snapshotFlow { ar.fieldOfViewDegrees.takeIf { ar.isOn } }.collect { camera.arFieldOfViewDegrees = it }
    }
    LaunchedEffect(ar, camera) {
        snapshotFlow { camera.mode }.collect { if (it != CameraMode.COMPASS && ar.isOn) ar.turnOff() }
    }
}

private const val STILL_CHECK_MILLIS = 10_000L
private val AR_SCRIM = Color.Black.copy(alpha = 0.3f)

/**
 * The map and its controls. With [ar] on, [arCamera] (CameraX on a device, a stand-in in tests)
 * fills the background under a scrim, and the map is drawn over it as the lens sees the sky.
 */
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
    onCometClick: (designation: String) -> Unit,
    locationSection: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    ar: SkyArState = remember { SkyArState() },
    onArClick: () -> Unit = {},
    arCamera: @Composable (Modifier) -> Unit = {},
    deepSky: DeepSkyActions = remember { DeepSkyActions() },
) {
    val isNightMode = uiState.isNightMode && !ar.isOn
    val palette = if (ar.isOn) SkyPalette.AR else if (isNightMode) SkyPalette.NIGHT else SkyPalette.REGULAR
    LinkArToCamera(ar, camera)
    SkyMapTheme(isNightMode = isNightMode) {
        val sun = uiState.bodies?.solarSystem?.firstOrNull { it.body == SolarSystemBody.SUN }
        val background = if (ar.isOn) Color.Black else palette.background(sun?.horizontal?.altitudeDegrees)
        Box(modifier.fillMaxSize().background(background)) {
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
                    ar = ar,
                    arCamera = arCamera,
                    palette = palette,
                    onSelect = onSelect,
                    onTimeOffsetChange = onTimeOffsetChange,
                    onBackToNow = onBackToNow,
                    onCometClick = onCometClick,
                    hasCompass = hasCompass,
                    isCompassUnsure = isCompassUnsure,
                    deepSky = deepSky,
                )
            }
            Column(Modifier.align(Alignment.TopCenter), horizontalAlignment = Alignment.CenterHorizontally) {
                SkyMapTopBar(
                    uiState = uiState,
                    camera = camera,
                    hasCompass = hasCompass,
                    isArOn = ar.isOn,
                    onArClick = onArClick,
                    onBackClick = onBackClick,
                    onSelect = onSelect,
                    onNightModeChange = onNightModeChange,
                    onDeepSkyLayerChange = deepSky.onLayerChange,
                    onObservingLogClick = deepSky.onObservingLogClick,
                )
                ar.problem?.let { problem ->
                    ArProblemCard(
                        problem = problem,
                        onTurnOn = onArClick,
                        onDismiss = ar::dismissProblem,
                        modifier = Modifier.widthIn(max = 560.dp).padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SkyMapLayers(
    uiState: SkyMapUiState,
    camera: SkyCameraState,
    ar: SkyArState,
    arCamera: @Composable (Modifier) -> Unit,
    palette: SkyPalette,
    onSelect: (SkyTarget?) -> Unit,
    onTimeOffsetChange: (Duration) -> Unit,
    onBackToNow: () -> Unit,
    onCometClick: (designation: String) -> Unit,
    hasCompass: Boolean,
    isCompassUnsure: Boolean,
    deepSky: DeepSkyActions,
) {
    val sky = checkNotNull(uiState.sky)
    var loggingId by rememberSaveable { mutableStateOf<String?>(null) }
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
        if (ar.isOn) {
            // The scrim keeps lines and labels readable over a bright picture.
            arCamera(Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(AR_SCRIM))
        }
        SkyMapCanvas(
            sky = sky,
            bodies = uiState.bodies,
            camera = camera,
            ar = ar,
            selected = uiState.selected,
            palette = palette,
            names = names,
            description = stringResource(R.string.sky_map_description, looking),
            showDeepSky = uiState.isDeepSkyOn,
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
            DirectionHint(looking, camera, hasCompass, isCompassUnsure)
            uiState.selected?.let { selected ->
                SelectionCard(uiState, selected, names, { onSelect(null) }, onCometClick) { loggingId = it }
            }
            // AR shows the sky as it is now: no time travel over the camera.
            if (ar.isOn) ArBar(ar, camera) else TimeBar(uiState, onTimeOffsetChange, onBackToNow)
        }
    }
    loggingId?.let { id -> sky.deepSkyObject(SkyTarget.DeepSky(id)) }?.let { item ->
        LogDialog(item, uiState.observingLog[item.id], deepSky, onClose = { loggingId = null })
    }
}

/** "Mark as seen" for [item] (F23), or the edit of its [entry]. */
@Composable
private fun LogDialog(item: DeepSkyObject, entry: ObservingLogEntry?, deepSky: DeepSkyActions, onClose: () -> Unit) {
    MarkSeenDialog(
        title = item.title(),
        entry = entry,
        today = LocalDate.now(),
        onSave = { day, conditions, note ->
            deepSky.onMarkSeen(item.id, day, conditions, note)
            onClose()
        },
        onRemove = {
            deepSky.onRemoveSeen(item.id)
            onClose()
        },
        onDismiss = onClose,
    )
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

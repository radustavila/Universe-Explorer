package com.radustavila.nasaexplorer.feature.skymap

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.radustavila.nasaexplorer.core.designsystem.units.rememberUnitFormatter
import com.radustavila.nasaexplorer.core.model.SolarSystemBody
import com.radustavila.nasaexplorer.feature.skymap.render.SkyNames
import com.radustavila.nasaexplorer.feature.skymap.render.direction
import java.time.Duration
import kotlin.math.roundToInt
import kotlin.math.roundToLong

const val SKY_MAP_SEARCH_FIELD_TEST_TAG = "sky-map-search-field"
const val SKY_MAP_TIME_SLIDER_TEST_TAG = "sky-map-time-slider"
const val SKY_MAP_SELECTION_TEST_TAG = "sky-map-selection"

private val SURFACE_ALPHA = 0.88f
private const val MAX_OFFSET_MINUTES = 12 * 60f
private const val SLIDER_STEP_MINUTES = 5
private val ONE_HOUR: Duration = Duration.ofHours(1)

@Composable
internal fun SkyMapTopBar(
    uiState: SkyMapUiState,
    camera: SkyCameraState,
    hasCompass: Boolean,
    onBackClick: () -> Unit,
    onSelect: (SkyTarget?) -> Unit,
    onNightModeChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var isSearching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    val names = rememberSkyNames()
    Column(modifier.statusBarsPadding().widthIn(max = 720.dp).fillMaxWidth().padding(horizontal = 4.dp)) {
        Surface(color = MaterialTheme.colorScheme.surface.copy(alpha = SURFACE_ALPHA), shape = MaterialTheme.shapes.extraLarge) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBackClick) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.sky_map_back))
                }
                if (isSearching) {
                    TextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text(stringResource(R.string.sky_map_search_hint)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                        ),
                        modifier = Modifier.weight(1f).testTag(SKY_MAP_SEARCH_FIELD_TEST_TAG),
                    )
                    IconButton(onClick = {
                        isSearching = false
                        query = ""
                    }) {
                        Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.sky_map_close_search))
                    }
                } else {
                    Text(
                        text = stringResource(R.string.sky_map_title),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f).padding(start = 4.dp),
                    )
                    IconButton(onClick = { isSearching = true }, enabled = uiState.sky != null) {
                        Icon(Icons.Outlined.Search, contentDescription = stringResource(R.string.sky_map_search))
                    }
                }
                if (hasCompass) {
                    val isCompass = camera.mode == CameraMode.COMPASS
                    IconToggleButton(
                        checked = isCompass,
                        onCheckedChange = { camera.mode = if (it) CameraMode.COMPASS else CameraMode.TOUCH },
                    ) {
                        Icon(
                            imageVector = if (isCompass) Icons.Outlined.Explore else Icons.Outlined.TouchApp,
                            contentDescription = stringResource(if (isCompass) R.string.sky_map_touch_mode else R.string.sky_map_compass_mode),
                        )
                    }
                }
                IconToggleButton(checked = uiState.isNightMode, onCheckedChange = onNightModeChange) {
                    Icon(
                        imageVector = Icons.Outlined.DarkMode,
                        contentDescription = stringResource(
                            if (uiState.isNightMode) R.string.sky_map_night_mode_off else R.string.sky_map_night_mode_on,
                        ),
                    )
                }
            }
        }
        val sky = uiState.sky
        if (isSearching && sky != null && query.isNotBlank()) {
            val results = remember(query, sky.chart, uiState.bodies?.satellites, names) {
                searchSky(query, sky.chart.catalog, uiState.bodies, names)
            }
            SearchResults(results, onResultClick = { target ->
                isSearching = false
                query = ""
                onSelect(target)
            })
        }
    }
}

@Composable
private fun SearchResults(results: List<SkySearchResult>, onResultClick: (SkyTarget) -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = SURFACE_ALPHA),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.padding(top = 4.dp).fillMaxWidth(),
    ) {
        if (results.isEmpty()) {
            Text(stringResource(R.string.sky_map_no_results), Modifier.padding(16.dp))
        } else {
            LazyColumn(Modifier.heightIn(max = 360.dp)) {
                items(results, key = { it.target.toString() }) { result ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onResultClick(result.target) }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                    ) {
                        Text(result.name, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            text = stringResource(result.target.kindRes()),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/** Where the map looks, and what to do when the compass is not up to it. */
@Composable
internal fun DirectionHint(looking: String, camera: SkyCameraState, hasCompass: Boolean, isCompassUnsure: Boolean) {
    val isCompass = hasCompass && camera.mode == CameraMode.COMPASS
    val isLookingDown by remember(camera) { derivedStateOf { camera.view.altitudeDegrees < POINT_UP_BELOW } }
    val hint = when {
        isCompass && isCompassUnsure -> stringResource(R.string.sky_map_calibrate)
        isCompass && isLookingDown -> stringResource(R.string.sky_map_point_at_sky)
        else -> null
    }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(color = MaterialTheme.colorScheme.surface.copy(alpha = SURFACE_ALPHA), shape = MaterialTheme.shapes.small) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(looking, style = MaterialTheme.typography.labelLarge)
                hint?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
            }
        }
    }
}

private const val POINT_UP_BELOW = -45.0

/** Name, kind, place in the sky and brightness of the selection. */
@Composable
internal fun SelectionCard(uiState: SkyMapUiState, selected: SkyTarget, names: SkyNames, onClose: () -> Unit) {
    val sky = uiState.sky ?: return
    val formatter = rememberUnitFormatter()
    val catalog = sky.chart.catalog
    val title = when (selected) {
        is SkyTarget.StarTarget -> catalog.stars.getOrNull(selected.index)?.name ?: stringResource(R.string.sky_map_unnamed_star)
        is SkyTarget.Body -> names.bodies[selected.body].orEmpty()
        is SkyTarget.Constellation -> catalog.constellations.getOrNull(selected.index)?.name.orEmpty()
        is SkyTarget.Satellite -> names.iss
    }
    val constellation = uiState.selectedConstellation?.let { id -> catalog.constellations.firstOrNull { it.id == id }?.name }
    val kind = stringResource(selected.kindRes())
    val subtitle = constellation?.let { stringResource(R.string.sky_map_kind_in, kind, it) } ?: kind
    val horizontal = selected.direction(sky, uiState.bodies)?.toHorizontal()
    val position = horizontal?.let {
        val towards = stringResource(compassPointRes(it.azimuthDegrees))
        if (it.altitudeDegrees >= 0.0) {
            stringResource(R.string.sky_map_position, it.altitudeDegrees.roundToInt().toString(), towards)
        } else {
            stringResource(R.string.sky_map_below_horizon, towards)
        }
    }
    val brightness = when (selected) {
        is SkyTarget.StarTarget -> catalog.stars.getOrNull(selected.index)?.magnitude?.toDouble()
        is SkyTarget.Body -> uiState.bodies?.solarSystem?.firstOrNull { it.body == selected.body }?.magnitude
        else -> null
    }?.let { stringResource(R.string.sky_map_magnitude, formatter.number(it, 1)) }
    val lit = (selected as? SkyTarget.Body)?.takeIf { it.body == SolarSystemBody.MOON }?.let {
        uiState.bodies?.solarSystem?.firstOrNull { body -> body.body == SolarSystemBody.MOON }?.illuminatedFraction
    }?.let { stringResource(R.string.sky_map_moon_lit, (it * PERCENT).roundToLong().toString()) }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = SURFACE_ALPHA)),
        modifier = Modifier.fillMaxWidth().testTag(SKY_MAP_SELECTION_TEST_TAG),
    ) {
        Row(Modifier.padding(start = 16.dp, top = 8.dp, bottom = 12.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f).padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                position?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                listOfNotNull(brightness, lit).takeIf { it.isNotEmpty() }?.let {
                    Text(it.joinToString(" · "), style = MaterialTheme.typography.bodySmall)
                }
            }
            IconButton(onClick = onClose) {
                Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.sky_map_close_details))
            }
        }
    }
}

private const val PERCENT = 100.0

/** The time the map shows: a slider 12 hours either way, hour steps, and back to now. */
@Composable
internal fun TimeBar(uiState: SkyMapUiState, onTimeOffsetChange: (Duration) -> Unit, onBackToNow: () -> Unit) {
    val formatter = rememberUnitFormatter()
    val sliderDescription = stringResource(R.string.sky_map_time_slider)
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = SURFACE_ALPHA)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val time = uiState.instant?.let { formatter.dayAndTime(it) }.orEmpty()
                Text(
                    text = if (uiState.isLive) stringResource(R.string.sky_map_now) else stringResource(R.string.sky_map_time_shown, time),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.weight(1f).padding(start = 8.dp),
                )
                if (!uiState.isLive) TextButton(onClick = onBackToNow) { Text(stringResource(R.string.sky_map_back_to_now)) }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { onTimeOffsetChange(uiState.timeOffset.minus(ONE_HOUR).clamped()) }) {
                    Icon(Icons.Outlined.Remove, contentDescription = stringResource(R.string.sky_map_earlier_hour))
                }
                Slider(
                    value = uiState.timeOffset.toMinutes().toFloat().coerceIn(-MAX_OFFSET_MINUTES, MAX_OFFSET_MINUTES),
                    onValueChange = { minutes ->
                        val rounded = (minutes / SLIDER_STEP_MINUTES).roundToInt() * SLIDER_STEP_MINUTES
                        onTimeOffsetChange(Duration.ofMinutes(rounded.toLong()))
                    },
                    valueRange = -MAX_OFFSET_MINUTES..MAX_OFFSET_MINUTES,
                    modifier = Modifier
                        .weight(1f)
                        .testTag(SKY_MAP_TIME_SLIDER_TEST_TAG)
                        .semantics { contentDescription = sliderDescription },
                )
                IconButton(onClick = { onTimeOffsetChange(uiState.timeOffset.plus(ONE_HOUR).clamped()) }) {
                    Icon(Icons.Outlined.Add, contentDescription = stringResource(R.string.sky_map_later_hour))
                }
            }
        }
    }
}

private fun Duration.clamped(): Duration {
    val limit = Duration.ofMinutes(MAX_OFFSET_MINUTES.toLong())
    return when {
        this > limit -> limit
        this < limit.negated() -> limit.negated()
        else -> this
    }
}

@androidx.annotation.StringRes
internal fun SkyTarget.kindRes(): Int = when (this) {
    is SkyTarget.StarTarget -> R.string.sky_map_kind_star
    is SkyTarget.Body -> body.kindRes()
    is SkyTarget.Constellation -> R.string.sky_map_kind_constellation
    is SkyTarget.Satellite -> R.string.sky_map_kind_station
}

/** Keeps the target the map last turned to across configuration changes. */
internal val SkyTargetSaver = Saver<SkyTarget?, String>(
    save = { target ->
        when (target) {
            is SkyTarget.StarTarget -> "star:${target.index}"
            is SkyTarget.Body -> "body:${target.body.name}"
            is SkyTarget.Constellation -> "constellation:${target.index}"
            is SkyTarget.Satellite -> "satellite:${target.noradId}"
            null -> ""
        }
    },
    restore = { saved ->
        val (kind, value) = saved.split(':', limit = 2).let { it.first() to it.getOrNull(1) }
        when (kind) {
            "star" -> value?.toIntOrNull()?.let(SkyTarget::StarTarget)
            "body" -> SolarSystemBody.entries.firstOrNull { it.name == value }?.let(SkyTarget::Body)
            "constellation" -> value?.toIntOrNull()?.let(SkyTarget::Constellation)
            "satellite" -> value?.toIntOrNull()?.let(SkyTarget::Satellite)
            else -> null
        }
    },
)

private val RED = Color(0xFFE53935)
private val DIM_RED = Color(0xFF9A1B1B)
private val DARK_RED = Color(0xFF3A0707)
private val NIGHT_SURFACE = Color(0xFF0E0000)

/** Red on black for every control of the screen in night mode; otherwise the app's theme. */
@Composable
internal fun SkyMapTheme(isNightMode: Boolean, content: @Composable () -> Unit) {
    if (!isNightMode) return content()
    val colors = MaterialTheme.colorScheme.copy(
        primary = RED,
        onPrimary = Color.Black,
        primaryContainer = DARK_RED,
        onPrimaryContainer = RED,
        secondary = RED,
        onSecondary = Color.Black,
        secondaryContainer = DARK_RED,
        onSecondaryContainer = RED,
        background = Color.Black,
        onBackground = RED,
        surface = NIGHT_SURFACE,
        onSurface = RED,
        surfaceVariant = DARK_RED,
        onSurfaceVariant = DIM_RED,
        surfaceContainerLowest = NIGHT_SURFACE,
        surfaceContainerLow = NIGHT_SURFACE,
        surfaceContainer = NIGHT_SURFACE,
        surfaceContainerHigh = NIGHT_SURFACE,
        surfaceContainerHighest = DARK_RED,
        outline = DIM_RED,
        outlineVariant = DARK_RED,
        inverseSurface = RED,
        inverseOnSurface = Color.Black,
    )
    MaterialTheme(colorScheme = colors, typography = MaterialTheme.typography, shapes = MaterialTheme.shapes, content = content)
}

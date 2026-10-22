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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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

const val SKY_MAP_TIME_SLIDER_TEST_TAG = "sky-map-time-slider"

private const val SURFACE_ALPHA = 0.88f

/** Controls float over the sky: see-through, with the theme's text colour (red in night mode). */
@Composable
internal fun translucentSurface(): Color = MaterialTheme.colorScheme.surface.copy(alpha = SURFACE_ALPHA)

@Composable
internal fun translucentCardColors() = CardDefaults.cardColors(
    containerColor = translucentSurface(),
    contentColor = MaterialTheme.colorScheme.onSurface,
)

private const val MAX_OFFSET_MINUTES = 12 * 60f
private const val SLIDER_STEP_MINUTES = 5
private val ONE_HOUR: Duration = Duration.ofHours(1)

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
        Surface(
            color = translucentSurface(),
            contentColor = MaterialTheme.colorScheme.onSurface,
            shape = MaterialTheme.shapes.small,
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(looking, style = MaterialTheme.typography.labelLarge)
                hint?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

private const val POINT_UP_BELOW = -45.0

/** The time the map shows: a slider 12 hours either way, hour steps, and back to now. */
@Composable
internal fun TimeBar(uiState: SkyMapUiState, onTimeOffsetChange: (Duration) -> Unit, onBackToNow: () -> Unit) {
    val formatter = rememberUnitFormatter()
    val sliderDescription = stringResource(R.string.sky_map_time_slider)
    Card(
        colors = translucentCardColors(),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val time = uiState.instant?.let { formatter.dayAndTime(it) }.orEmpty()
                Text(
                    text = if (uiState.isLive) {
                        stringResource(R.string.sky_map_now)
                    } else {
                        stringResource(R.string.sky_map_time_shown, time)
                    },
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.weight(1f).padding(start = 8.dp),
                )
                if (!uiState.isLive) {
                    TextButton(onClick = onBackToNow) { Text(stringResource(R.string.sky_map_back_to_now)) }
                }
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
    MaterialTheme(
        colorScheme = colors,
        typography = MaterialTheme.typography,
        shapes = MaterialTheme.shapes,
        content = content,
    )
}

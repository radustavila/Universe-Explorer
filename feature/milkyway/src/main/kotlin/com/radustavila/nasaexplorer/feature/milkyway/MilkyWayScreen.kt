package com.radustavila.nasaexplorer.feature.milkyway

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.radustavila.nasaexplorer.core.designsystem.component.LoadingState
import com.radustavila.nasaexplorer.core.designsystem.component.SectionTitle
import com.radustavila.nasaexplorer.core.designsystem.units.UnitFormatter
import com.radustavila.nasaexplorer.core.designsystem.units.rememberUnitFormatter
import com.radustavila.nasaexplorer.core.model.GalacticCentre
import com.radustavila.nasaexplorer.core.model.MilkyWayNight
import com.radustavila.nasaexplorer.core.model.MilkyWayOutlook
import com.radustavila.nasaexplorer.core.model.MilkyWayUnavailable
import com.radustavila.nasaexplorer.core.model.NightDarkness
import com.radustavila.nasaexplorer.feature.milkyway.component.AxisLabels
import com.radustavila.nasaexplorer.feature.milkyway.component.NightAxis
import com.radustavila.nasaexplorer.feature.milkyway.component.NightBar
import com.radustavila.nasaexplorer.feature.milkyway.component.NightBarLegend
import com.radustavila.nasaexplorer.feature.milkyway.component.NightDetails
import com.radustavila.nasaexplorer.feature.milkyway.component.corePositionText
import com.radustavila.nasaexplorer.feature.milkyway.component.durationText
import com.radustavila.nasaexplorer.feature.milkyway.component.intervalText
import com.radustavila.nasaexplorer.feature.milkyway.component.seasonText
import java.time.ZoneId

const val MILKY_WAY_LIST_TEST_TAG = "milky-way-list"
const val MILKY_WAY_SUMMARY_TEST_TAG = "milky-way-summary"
const val MILKY_WAY_PLACE_PROMPT_TEST_TAG = "milky-way-place-prompt"
const val MILKY_WAY_SKY_MAP_TEST_TAG = "milky-way-sky-map"
const val MILKY_WAY_NIGHT_TEST_TAG = "milky-way-night"

@Composable
fun MilkyWayScreen(
    onBackClick: () -> Unit,
    onSetLocation: () -> Unit,
    onShowOnSkyMap: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MilkyWayViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    MilkyWayContent(
        uiState = uiState,
        onBackClick = onBackClick,
        onSetLocation = onSetLocation,
        onShowOnSkyMap = onShowOnSkyMap,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MilkyWayContent(
    uiState: MilkyWayUiState,
    onBackClick: () -> Unit,
    onSetLocation: () -> Unit,
    onShowOnSkyMap: () -> Unit,
    modifier: Modifier = Modifier,
    zone: ZoneId = ZoneId.systemDefault(),
) {
    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.milky_way_title)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.milky_way_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            val outlook = uiState.outlook
            when {
                !uiState.isLocationLoaded -> LoadingState()
                uiState.location == null -> PlacePrompt(onSetLocation, Modifier.padding(16.dp).widthIn(max = MAX_WIDTH))
                outlook == null -> LoadingState()
                else -> Planner(outlook, zone, onShowOnSkyMap)
            }
        }
    }
}

@Composable
private fun Planner(outlook: MilkyWayOutlook, zone: ZoneId, onShowOnSkyMap: () -> Unit) {
    val units = rememberUnitFormatter()
    val axis = remember(outlook.nights, zone) { NightAxis.of(outlook.nights, zone) }
    var expanded by rememberSaveable { mutableStateOf<Long?>(null) }
    val season = seasonNote(outlook)
    LazyColumn(
        modifier = Modifier
            .widthIn(max = MAX_WIDTH)
            .fillMaxSize()
            .testTag(MILKY_WAY_LIST_TEST_TAG),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val hasNights = outlook.nights.any { it.twilight.sunset != null || it.darkness != NightDarkness.NONE }
        item(key = "summary") { Summary(outlook, units, hasNights, onShowOnSkyMap) }
        season?.let { note ->
            item(key = "season") {
                Text(note, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 4.dp))
            }
        }
        // Days the Sun never sets have nothing to draw: the summary says why.
        if (!hasNights) return@LazyColumn
        item(key = "title") {
            SectionTitle(stringResource(R.string.milky_way_nights_title), modifier = Modifier.padding(top = 8.dp))
        }
        item(key = "legend") { NightBarLegend() }
        outlook.nights.firstOrNull()?.let { first ->
            item(key = "axis") {
                Row {
                    Spacer(Modifier.width(DAY_WIDTH))
                    AxisLabels(axis, first, units, Modifier.weight(1f))
                    Spacer(Modifier.width(LENGTH_WIDTH))
                }
            }
        }
        items(outlook.nights, key = { it.start.toEpochMilli() }) { night ->
            val key = night.start.toEpochMilli()
            NightRow(
                night = night,
                axis = axis,
                units = units,
                expanded = expanded == key,
                onToggle = { expanded = if (expanded == key) null else key },
            )
        }
    }
}

/** Tonight's window, the next good night's, or why there is none. */
@Composable
private fun Summary(outlook: MilkyWayOutlook, units: UnitFormatter, hasNights: Boolean, onShowOnSkyMap: () -> Unit) {
    Card(Modifier.fillMaxWidth().testTag(MILKY_WAY_SUMMARY_TEST_TAG)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val next = outlook.nextWindowNight
            val window = next?.window
            if (next != null && window != null) {
                val isTonight = next == outlook.nights.first()
                Text(
                    text = if (isTonight) {
                        stringResource(R.string.milky_way_tonight)
                    } else {
                        stringResource(R.string.milky_way_next_night, units.day(next.start))
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(intervalText(window.start, window.end, units), style = MaterialTheme.typography.headlineSmall)
                Text(corePositionText(window, units), style = MaterialTheme.typography.bodyLarge)
                Text(
                    stringResource(R.string.milky_way_window_length, durationText(window.duration)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                val (title, description) = unavailableText(outlook)
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(description, style = MaterialTheme.typography.bodyMedium)
                if (hasNights) {
                    Text(
                        stringResource(R.string.milky_way_twilight_below),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (outlook.coreHighestAltitudeDegrees > 0.0) {
                OutlinedButton(onClick = onShowOnSkyMap, modifier = Modifier.testTag(MILKY_WAY_SKY_MAP_TEST_TAG)) {
                    Text(stringResource(R.string.milky_way_show_on_sky_map))
                }
            }
        }
    }
}

@Composable
private fun unavailableText(outlook: MilkyWayOutlook): Pair<String, String> = when (outlook.unavailable) {
    MilkyWayUnavailable.CORE_TOO_LOW -> stringResource(R.string.milky_way_too_low_title) to
        if (outlook.coreHighestAltitudeDegrees > 0.0) {
            // Rounded down: 9.5° must not read as the 10° it falls short of.
            stringResource(R.string.milky_way_too_low_description, outlook.coreHighestAltitudeDegrees.toInt())
        } else {
            stringResource(R.string.milky_way_never_rises_description)
        }
    MilkyWayUnavailable.NO_DARKNESS -> stringResource(R.string.milky_way_no_darkness_title) to
        stringResource(R.string.milky_way_no_darkness_description)
    MilkyWayUnavailable.CORE_UP_BY_DAY ->
        stringResource(R.string.milky_way_by_day_title) to stringResource(R.string.milky_way_by_day_description)
    MilkyWayUnavailable.MOONLIGHT, null ->
        stringResource(R.string.milky_way_moonlight_title) to stringResource(R.string.milky_way_moonlight_description)
}

/** "Core season here: March to October, …"; none where the core never gets high enough. */
@Composable
private fun seasonNote(outlook: MilkyWayOutlook): String? {
    if (outlook.coreHighestAltitudeDegrees < GalacticCentre.MIN_ALTITUDE_DEGREES) return null
    return seasonText(outlook.season)?.let { stringResource(R.string.milky_way_season, it) }
}

@Composable
private fun NightRow(
    night: MilkyWayNight,
    axis: NightAxis,
    units: UnitFormatter,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    val window = night.window
    val day = units.day(night.start)
    val summary = stringResource(
        R.string.milky_way_night_description,
        day,
        window?.let { stringResource(R.string.milky_way_night_window, units.time(it.start), units.time(it.end)) }
            ?: stringResource(R.string.milky_way_night_none),
    )
    val state = stringResource(if (expanded) R.string.milky_way_collapse else R.string.milky_way_expand)
    Column(Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(vertical = 4.dp)
                .semantics(mergeDescendants = true) {
                    contentDescription = summary
                    stateDescription = state
                }
                .testTag(MILKY_WAY_NIGHT_TEST_TAG),
        ) {
            Text(day, style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(DAY_WIDTH))
            NightBar(night, axis, Modifier.weight(1f))
            Text(
                text = window?.let { durationText(it.duration) } ?: stringResource(R.string.milky_way_no_window),
                style = MaterialTheme.typography.labelMedium,
                textAlign = TextAlign.End,
                modifier = Modifier.width(LENGTH_WIDTH),
            )
        }
        AnimatedVisibility(expanded) {
            Column {
                NightDetails(night, units, Modifier.padding(vertical = 8.dp))
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun PlacePrompt(onSetLocation: () -> Unit, modifier: Modifier = Modifier) {
    Card(modifier = modifier.fillMaxWidth().testTag(MILKY_WAY_PLACE_PROMPT_TEST_TAG)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.milky_way_place_prompt), style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(onClick = onSetLocation) { Text(stringResource(R.string.milky_way_place_action)) }
        }
    }
}

private val MAX_WIDTH = 720.dp
private val DAY_WIDTH = 84.dp
private val LENGTH_WIDTH = 64.dp

package com.radustavila.nasaexplorer.feature.launches.details

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.RocketLaunch
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.radustavila.nasaexplorer.core.designsystem.adaptive.ReadableMaxWidth
import com.radustavila.nasaexplorer.core.designsystem.component.EmptyState
import com.radustavila.nasaexplorer.core.designsystem.component.LoadingState
import com.radustavila.nasaexplorer.core.designsystem.component.NasaAsyncImage
import com.radustavila.nasaexplorer.core.designsystem.component.SectionTitle
import com.radustavila.nasaexplorer.core.designsystem.notification.NotificationPermissionToggle
import com.radustavila.nasaexplorer.core.designsystem.notification.rememberNotificationPermissionToggle
import com.radustavila.nasaexplorer.core.designsystem.units.rememberUnitFormatter
import com.radustavila.nasaexplorer.core.map.MapCamera
import com.radustavila.nasaexplorer.core.map.SpaceMap
import com.radustavila.nasaexplorer.core.map.overlay.GeoPoint
import com.radustavila.nasaexplorer.core.map.overlay.MapMarker
import com.radustavila.nasaexplorer.core.map.overlay.MapOverlay
import com.radustavila.nasaexplorer.core.map.rememberSpaceMapState
import com.radustavila.nasaexplorer.core.model.GeoCoordinate
import com.radustavila.nasaexplorer.core.model.Launch
import com.radustavila.nasaexplorer.core.model.LaunchTimeline
import com.radustavila.nasaexplorer.core.model.Webcast
import com.radustavila.nasaexplorer.feature.launches.R
import com.radustavila.nasaexplorer.feature.launches.component.CountdownText
import com.radustavila.nasaexplorer.feature.launches.component.LaunchReminderBell
import com.radustavila.nasaexplorer.feature.launches.component.LaunchStatusChip
import com.radustavila.nasaexplorer.feature.launches.component.MILLIS_PER_SECOND
import com.radustavila.nasaexplorer.feature.launches.component.hasExactTime
import com.radustavila.nasaexplorer.feature.launches.component.netText
import com.radustavila.nasaexplorer.feature.launches.component.openUrl
import com.radustavila.nasaexplorer.feature.launches.component.rememberTickingClock
import com.radustavila.nasaexplorer.feature.launches.missionName
import java.time.Instant

const val LAUNCH_DETAILS_TEST_TAG = "launch-details"
const val LAUNCH_WEBCAST_TEST_TAG = "launch-webcast"

/** Draws the pad's position; the real map in the app, a stand-in in tests. */
typealias PadMapSlot = @Composable (position: GeoCoordinate, modifier: Modifier) -> Unit

@Composable
fun LaunchDetailsScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LaunchDetailsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val second = rememberTickingClock(MILLIS_PER_SECOND)
    val reminder = rememberNotificationPermissionToggle(
        isEnabled = uiState.hasReminder,
        onEnabledChange = viewModel::onReminderChange,
        snackbarHostState = snackbarHostState,
        blockedMessage = stringResource(R.string.launches_reminder_blocked),
    )
    LaunchDetailsContent(
        uiState = uiState,
        reminder = reminder,
        now = { second.value },
        onBackClick = onBackClick,
        onOpenUrl = context::openUrl,
        padMap = { position, mapModifier -> PadMap(position, mapModifier) },
        snackbarHostState = snackbarHostState,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LaunchDetailsContent(
    uiState: LaunchDetailsUiState,
    reminder: NotificationPermissionToggle,
    now: () -> Instant,
    onBackClick: () -> Unit,
    onOpenUrl: (String) -> Unit,
    padMap: PadMapSlot,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    val launch = uiState.launch
    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = {
                    Text(launch?.missionName.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.launches_back),
                        )
                    }
                },
                actions = {
                    if (launch != null && launch.timeline == LaunchTimeline.UPCOMING) {
                        LaunchReminderBell(toggle = reminder, launchName = launch.missionName)
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        when {
            !uiState.isLoaded -> LoadingState(Modifier.padding(padding))
            launch == null -> EmptyState(
                title = stringResource(R.string.launches_details_missing),
                modifier = Modifier.padding(padding),
            )

            else -> Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                LaunchDetails(
                    launch = launch,
                    now = now,
                    onOpenUrl = onOpenUrl,
                    padMap = padMap,
                    modifier = Modifier
                        .widthIn(max = ReadableMaxWidth)
                        .padding(16.dp)
                        .testTag(LAUNCH_DETAILS_TEST_TAG),
                )
            }
        }
    }
}

@Composable
private fun LaunchDetails(
    launch: Launch,
    now: () -> Instant,
    onOpenUrl: (String) -> Unit,
    padMap: PadMapSlot,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        NasaAsyncImage(
            url = launch.imageUrl,
            contentDescription = null,
            fallbackIcon = Icons.Outlined.RocketLaunch,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(IMAGE_ASPECT_RATIO)
                .clip(RoundedCornerShape(16.dp)),
        )
        LiftoffSection(launch, now)
        if (launch.webcasts.isNotEmpty()) WebcastsSection(launch.webcasts, onOpenUrl)
        MissionSection(launch)
        RocketSection(launch)
        if (launch.boosters.isNotEmpty()) BoostersSection(launch.boosters)
        launch.failReason?.let { reason ->
            DetailsSection(stringResource(R.string.launches_details_failure)) {
                Text(reason, style = MaterialTheme.typography.bodyMedium)
            }
        }
        PadSection(launch, padMap)
        Column {
            TextButton(onClick = { onOpenUrl(launch.pageUrl) }) {
                Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null)
                Text(stringResource(R.string.launches_details_more), modifier = Modifier.padding(start = 8.dp))
            }
            Text(
                text = stringResource(R.string.launches_credit),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LiftoffSection(launch: Launch, now: () -> Instant) {
    val units = rememberUnitFormatter()
    DetailsSection(stringResource(R.string.launches_details_liftoff)) {
        Text(launch.name, style = MaterialTheme.typography.titleMedium)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LaunchStatusChip(launch.status)
            if (launch.timeline == LaunchTimeline.UPCOMING && launch.hasExactTime) {
                CountdownText(net = launch.net, now = now)
            }
        }
        Text(netText(launch), style = MaterialTheme.typography.bodyMedium)
        val start = launch.windowStart
        val end = launch.windowEnd
        if (start != null && end != null && end.isAfter(start)) {
            Text(
                stringResource(R.string.launches_details_window, units.time(start), units.time(end)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        launch.probability?.let {
            Text(
                stringResource(R.string.launches_details_weather, it),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WebcastsSection(webcasts: List<Webcast>, onOpenUrl: (String) -> Unit) {
    DetailsSection(stringResource(R.string.launches_details_webcasts)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            webcasts.take(MAX_WEBCASTS).forEach { webcast ->
                OutlinedButton(
                    onClick = { onOpenUrl(webcast.url) },
                    modifier = Modifier.testTag(LAUNCH_WEBCAST_TEST_TAG),
                ) {
                    Icon(Icons.Outlined.PlayCircle, contentDescription = null)
                    Text(
                        text = webcast.publisher ?: webcast.title ?: webcast.url,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun MissionSection(launch: Launch) {
    val mission = launch.mission ?: return
    DetailsSection(stringResource(R.string.launches_details_mission)) {
        listOfNotNull(mission.name, mission.type).takeIf { it.isNotEmpty() }?.let {
            Text(it.joinToString(" · "), style = MaterialTheme.typography.titleSmall)
        }
        val orbitName = mission.orbitName
        val orbitAbbrev = mission.orbitAbbrev
        when {
            orbitName != null && orbitAbbrev != null && orbitAbbrev != orbitName ->
                Text(
                    stringResource(R.string.launches_details_orbit_abbrev, orbitName, orbitAbbrev),
                    style = MaterialTheme.typography.bodyMedium,
                )

            orbitName != null || orbitAbbrev != null ->
                Text(
                    stringResource(R.string.launches_details_orbit, orbitName ?: orbitAbbrev.orEmpty()),
                    style = MaterialTheme.typography.bodyMedium,
                )
        }
        mission.description?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
    }
}

@Composable
private fun RocketSection(launch: Launch) {
    DetailsSection(stringResource(R.string.launches_details_rocket)) {
        Text(launch.rocket, style = MaterialTheme.typography.titleSmall)
        launch.provider?.let {
            Text(stringResource(R.string.launches_details_provider, it), style = MaterialTheme.typography.bodyMedium)
        }
        if (launch.isCrewed) {
            Text(
                pluralStringResource(R.plurals.launches_details_crew, launch.crewCount, launch.crewCount),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun PadSection(launch: Launch, padMap: PadMapSlot) {
    if (launch.pad.isBlank() && launch.padPosition == null) return
    DetailsSection(stringResource(R.string.launches_details_pad)) {
        if (launch.pad.isNotBlank()) Text(launch.pad, style = MaterialTheme.typography.bodyMedium)
        launch.padPosition?.let { position ->
            padMap(
                position,
                Modifier
                    .fillMaxWidth()
                    .height(PAD_MAP_HEIGHT)
                    .clip(RoundedCornerShape(12.dp)),
            )
        }
    }
}

/** The pad on the Blue Marble base map: where on Earth, not which pad (no "visible from you" zone). */
@Composable
private fun PadMap(position: GeoCoordinate, modifier: Modifier) {
    val point = GeoPoint(latitude = position.latitude, longitude = position.longitude)
    val state = rememberSpaceMapState(MapCamera(center = point, zoom = PAD_MAP_ZOOM))
    val color = MaterialTheme.colorScheme.primary
    val overlays = remember(point, color) {
        listOf(MapOverlay(id = "pad", shapes = listOf(MapMarker(id = "pad", position = point, color = color))))
    }
    SpaceMap(imagery = null, state = state, overlays = overlays, modifier = modifier)
}

@Composable
internal fun DetailsSection(title: String, content: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SectionTitle(text = title)
            content()
        }
    }
}

private const val IMAGE_ASPECT_RATIO = 16f / 9f
private const val MAX_WEBCASTS = 4
private const val PAD_MAP_ZOOM = 4.0
private val PAD_MAP_HEIGHT = 180.dp

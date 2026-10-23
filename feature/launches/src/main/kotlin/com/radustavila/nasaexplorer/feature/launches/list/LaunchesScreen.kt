package com.radustavila.nasaexplorer.feature.launches.list

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.RocketLaunch
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.radustavila.nasaexplorer.core.designsystem.adaptive.CardColumnMinWidth
import com.radustavila.nasaexplorer.core.designsystem.component.EmptyState
import com.radustavila.nasaexplorer.core.designsystem.component.FreshnessLabel
import com.radustavila.nasaexplorer.core.designsystem.component.LoadingState
import com.radustavila.nasaexplorer.core.designsystem.component.NasaAsyncImage
import com.radustavila.nasaexplorer.core.designsystem.component.OfflineBanner
import com.radustavila.nasaexplorer.core.designsystem.component.OfflineState
import com.radustavila.nasaexplorer.core.designsystem.notification.NotificationPermissionToggle
import com.radustavila.nasaexplorer.core.designsystem.notification.rememberNotificationPermissionToggle
import com.radustavila.nasaexplorer.core.model.Launch
import com.radustavila.nasaexplorer.feature.launches.LIFTOFF_GRACE
import com.radustavila.nasaexplorer.feature.launches.R
import com.radustavila.nasaexplorer.feature.launches.component.CountdownText
import com.radustavila.nasaexplorer.feature.launches.component.LaunchReminderBell
import com.radustavila.nasaexplorer.feature.launches.component.LaunchStatusChip
import com.radustavila.nasaexplorer.feature.launches.component.MILLIS_PER_MINUTE
import com.radustavila.nasaexplorer.feature.launches.component.MILLIS_PER_SECOND
import com.radustavila.nasaexplorer.feature.launches.component.hasExactTime
import com.radustavila.nasaexplorer.feature.launches.component.netText
import com.radustavila.nasaexplorer.feature.launches.component.rememberTickingClock
import com.radustavila.nasaexplorer.feature.launches.missionName
import java.time.Instant

const val LAUNCHES_LIST_TEST_TAG = "launches-list"
const val LAUNCH_ITEM_TEST_TAG = "launch-item"
const val LAUNCHES_TAB_UPCOMING_TEST_TAG = "launches-tab-upcoming"
const val LAUNCHES_TAB_PAST_TEST_TAG = "launches-tab-past"
const val LAUNCHES_PROVIDER_FILTER_TEST_TAG = "launches-provider-filter"
const val LAUNCHES_CREWED_FILTER_TEST_TAG = "launches-crewed-filter"

@Composable
fun LaunchesScreen(
    onBackClick: () -> Unit,
    onLaunchClick: (id: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LaunchesViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val blockedMessage = stringResource(R.string.launches_reminder_blocked)
    LaunchesContent(
        uiState = uiState,
        onBackClick = onBackClick,
        onLaunchClick = onLaunchClick,
        onTabSelected = viewModel::onTabSelected,
        onProviderSelected = viewModel::onProviderSelected,
        onCrewedOnlyChange = viewModel::onCrewedOnlyChange,
        reminderToggle = { launch ->
            rememberNotificationPermissionToggle(
                isEnabled = launch.id in uiState.reminders,
                onEnabledChange = { enabled -> viewModel.onReminderChange(launch.id, enabled) },
                snackbarHostState = snackbarHostState,
                blockedMessage = blockedMessage,
            )
        },
        snackbarHostState = snackbarHostState,
        modifier = modifier,
    )
}

/**
 * Upcoming and Past tabs over the cached launches, filtered by provider and to crewed flights. An
 * upcoming launch carries a reminder bell ([reminderToggle]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LaunchesContent(
    uiState: LaunchesUiState,
    onBackClick: () -> Unit,
    onLaunchClick: (id: String) -> Unit,
    onTabSelected: (LaunchesTab) -> Unit,
    onProviderSelected: (String?) -> Unit,
    onCrewedOnlyChange: (Boolean) -> Unit,
    reminderToggle: @Composable (Launch) -> NotificationPermissionToggle,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    now: (() -> Instant)? = null,
) {
    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.launches_title)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.launches_back),
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(Modifier.padding(padding)) {
            LaunchTabs(selected = uiState.filters.tab, onTabSelected = onTabSelected)
            if (uiState.freshness.isOffline && uiState.freshness.lastUpdatedAt != null) OfflineBanner()
            val launches = uiState.tabLaunches
            when {
                launches == null -> LoadingState()
                launches.isEmpty() -> NothingCached(isOffline = uiState.freshness.isOffline)
                else -> LaunchList(
                    uiState = uiState,
                    onLaunchClick = onLaunchClick,
                    onProviderSelected = onProviderSelected,
                    onCrewedOnlyChange = onCrewedOnlyChange,
                    reminderToggle = reminderToggle,
                    fixedNow = now,
                )
            }
        }
    }
}

@Composable
private fun LaunchTabs(selected: LaunchesTab, onTabSelected: (LaunchesTab) -> Unit) {
    PrimaryTabRow(selectedTabIndex = selected.ordinal) {
        Tab(
            selected = selected == LaunchesTab.UPCOMING,
            onClick = { onTabSelected(LaunchesTab.UPCOMING) },
            text = { Text(stringResource(R.string.launches_tab_upcoming)) },
            modifier = Modifier.testTag(LAUNCHES_TAB_UPCOMING_TEST_TAG),
        )
        Tab(
            selected = selected == LaunchesTab.PAST,
            onClick = { onTabSelected(LaunchesTab.PAST) },
            text = { Text(stringResource(R.string.launches_tab_past)) },
            modifier = Modifier.testTag(LAUNCHES_TAB_PAST_TEST_TAG),
        )
    }
}

@Composable
private fun NothingCached(isOffline: Boolean) {
    if (isOffline) {
        OfflineState(
            title = stringResource(R.string.launches_offline_title),
            description = stringResource(R.string.launches_offline_description),
        )
    } else {
        EmptyState(
            title = stringResource(R.string.launches_none_title),
            description = stringResource(R.string.launches_none_description),
        )
    }
}

@Composable
private fun LaunchList(
    uiState: LaunchesUiState,
    onLaunchClick: (String) -> Unit,
    onProviderSelected: (String?) -> Unit,
    onCrewedOnlyChange: (Boolean) -> Unit,
    reminderToggle: @Composable (Launch) -> NotificationPermissionToggle,
    fixedNow: (() -> Instant)?,
) {
    // The list changes on the scale of minutes; only the countdowns tick each second.
    val minuteClock by rememberTickingClock(MILLIS_PER_MINUTE)
    val secondClock = rememberTickingClock(MILLIS_PER_SECOND)
    val now: () -> Instant = fixedNow ?: { secondClock.value }
    val minute = fixedNow?.invoke() ?: minuteClock
    val isUpcoming = uiState.filters.tab == LaunchesTab.UPCOMING
    // The cache refreshes hourly at most: a launch long gone is dropped here.
    val launches = remember(uiState.shown, isUpcoming, minute) {
        if (isUpcoming) uiState.shown.filter { it.net > minute.minus(LIFTOFF_GRACE) } else uiState.shown
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(CardColumnMinWidth),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize().testTag(LAUNCHES_LIST_TEST_TAG),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }, contentType = "filters") {
            LaunchFiltersRow(
                providers = uiState.providers,
                filters = uiState.filters,
                onProviderSelected = onProviderSelected,
                onCrewedOnlyChange = onCrewedOnlyChange,
            )
        }
        if (launches.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }, contentType = "empty") {
                Text(
                    text = stringResource(R.string.launches_filtered_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp),
                )
            }
        }
        items(launches, key = { it.id }, contentType = { "launch" }) { launch ->
            LaunchItem(
                launch = launch,
                now = now,
                reminder = if (isUpcoming) reminderToggle(launch) else null,
                onClick = { onLaunchClick(launch.id) },
            )
        }
        item(span = { GridItemSpan(maxLineSpan) }, contentType = "footer") {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                FreshnessLabel(uiState.freshness)
                Text(
                    text = stringResource(R.string.launches_credit),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun LaunchFiltersRow(
    providers: List<String>,
    filters: LaunchFilters,
    onProviderSelected: (String?) -> Unit,
    onCrewedOnlyChange: (Boolean) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.horizontalScroll(rememberScrollState()),
    ) {
        Box {
            FilterChip(
                selected = filters.provider != null,
                onClick = { menuOpen = true },
                label = { Text(filters.provider ?: stringResource(R.string.launches_filter_provider_all)) },
                trailingIcon = { Icon(Icons.Outlined.ArrowDropDown, contentDescription = null) },
                modifier = Modifier.testTag(LAUNCHES_PROVIDER_FILTER_TEST_TAG),
            )
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.launches_filter_provider_all)) },
                    onClick = {
                        menuOpen = false
                        onProviderSelected(null)
                    },
                )
                providers.forEach { provider ->
                    DropdownMenuItem(
                        text = { Text(provider) },
                        onClick = {
                            menuOpen = false
                            onProviderSelected(provider)
                        },
                    )
                }
            }
        }
        FilterChip(
            selected = filters.crewedOnly,
            onClick = { onCrewedOnlyChange(!filters.crewedOnly) },
            label = { Text(stringResource(R.string.launches_filter_crewed)) },
            modifier = Modifier.testTag(LAUNCHES_CREWED_FILTER_TEST_TAG),
        )
    }
}

@Composable
private fun LaunchItem(
    launch: Launch,
    now: () -> Instant,
    reminder: NotificationPermissionToggle?,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                onClickLabel = stringResource(R.string.launches_open_page),
                role = Role.Button,
                onClick = onClick,
            )
            .testTag(LAUNCH_ITEM_TEST_TAG),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(12.dp)) {
            NasaAsyncImage(
                url = launch.imageUrl,
                contentDescription = null,
                fallbackIcon = Icons.Outlined.RocketLaunch,
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(12.dp)),
            )
            Spacer(Modifier.width(12.dp))
            LaunchSummary(
                launch = launch,
                now = now,
                showCountdown = reminder != null && launch.hasExactTime,
                modifier = Modifier.weight(1f),
            )
            if (reminder != null) LaunchReminderBell(toggle = reminder, launchName = launch.missionName)
        }
    }
}

/** Mission, rocket and provider, status, and when: the countdown ([showCountdown]) and the NET. */
@Composable
private fun LaunchSummary(
    launch: Launch,
    now: () -> Instant,
    showCountdown: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = modifier) {
        Text(
            text = launch.missionName,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = listOfNotNull(launch.rocket.takeIf(String::isNotBlank), launch.provider).joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LaunchStatusChip(status = launch.status)
            if (launch.isCrewed) {
                Text(
                    text = stringResource(R.string.launches_crewed),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
        }
        if (showCountdown) CountdownText(net = launch.net, now = now)
        Text(
            text = netText(launch),
            style = MaterialTheme.typography.labelMedium,
            fontFamily = if (launch.hasExactTime) null else FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

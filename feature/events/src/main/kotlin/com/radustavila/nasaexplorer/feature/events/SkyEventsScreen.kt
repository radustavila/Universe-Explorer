package com.radustavila.nasaexplorer.feature.events

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.radustavila.nasaexplorer.core.designsystem.adaptive.centeringPadding
import com.radustavila.nasaexplorer.core.designsystem.component.EmptyState
import com.radustavila.nasaexplorer.core.designsystem.component.LoadingState
import com.radustavila.nasaexplorer.core.designsystem.notification.rememberNotificationPermissionToggle
import com.radustavila.nasaexplorer.core.designsystem.units.rememberUnitFormatter
import com.radustavila.nasaexplorer.core.domain.sky.SkyEventReminderLead
import com.radustavila.nasaexplorer.core.model.ObserverLocation
import com.radustavila.nasaexplorer.core.model.SkyEvent
import com.radustavila.nasaexplorer.core.model.SkyEventCategory
import com.radustavila.nasaexplorer.feature.events.component.SkyEventDetails
import com.radustavila.nasaexplorer.feature.events.component.SkyEventRow
import com.radustavila.nasaexplorer.feature.events.component.icon
import com.radustavila.nasaexplorer.feature.events.component.labelRes
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

const val SKY_EVENTS_LIST_TEST_TAG = "sky-events-list"

@Composable
fun SkyEventsScreen(
    onBackClick: () -> Unit,
    onSetLocation: () -> Unit,
    onCometClick: (designation: String) -> Unit,
    modifier: Modifier = Modifier,
    onMilkyWayClick: () -> Unit = {},
    viewModel: SkyEventsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    SkyEventsContent(
        uiState = uiState,
        onBackClick = onBackClick,
        onSetLocation = onSetLocation,
        onPreviousMonth = viewModel::onPreviousMonth,
        onNextMonth = viewModel::onNextMonth,
        onThisMonth = viewModel::onThisMonth,
        onFilterToggle = viewModel::onFilterToggle,
        onEventClick = viewModel::onEventClick,
        onSheetDismiss = viewModel::onSheetDismiss,
        onReminderChange = viewModel::onReminderChange,
        onCometClick = onCometClick,
        onMilkyWayClick = onMilkyWayClick,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SkyEventsContent(
    uiState: SkyEventsUiState,
    onBackClick: () -> Unit,
    onSetLocation: () -> Unit,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onThisMonth: () -> Unit,
    onFilterToggle: (SkyEventCategory) -> Unit,
    onEventClick: (SkyEvent) -> Unit,
    onSheetDismiss: () -> Unit,
    onReminderChange: (String, SkyEventReminderLead?) -> Unit,
    onCometClick: (designation: String) -> Unit,
    modifier: Modifier = Modifier,
    onMilkyWayClick: () -> Unit = {},
) {
    val snackbarHostState = remember { SnackbarHostState() }
    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.sky_events_title)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.sky_events_back),
                        )
                    }
                },
                actions = {
                    if (!uiState.isCurrentMonth) {
                        IconButton(onClick = onThisMonth) {
                            Icon(
                                Icons.Outlined.Today,
                                contentDescription = stringResource(R.string.sky_events_this_month),
                            )
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        BoxWithConstraints(Modifier.padding(padding).fillMaxSize()) {
            val sidePadding = centeringPadding(containerWidth = maxWidth)
            Column(Modifier.fillMaxSize().padding(horizontal = sidePadding)) {
                MonthSwitcher(uiState.month, onPreviousMonth, onNextMonth)
                CategoryChips(uiState.filters, onFilterToggle)
                when {
                    uiState.isLoading -> LoadingState()
                    else -> EventList(uiState, onSetLocation, onEventClick)
                }
            }
        }
    }

    uiState.selected?.let { event ->
        EventSheet(event, uiState, snackbarHostState, onSheetDismiss, onReminderChange, onCometClick, onMilkyWayClick)
    }
}

/** The event's details over the calendar, with its reminder switch while the event is ahead. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EventSheet(
    event: SkyEvent,
    uiState: SkyEventsUiState,
    snackbarHostState: SnackbarHostState,
    onDismiss: () -> Unit,
    onReminderChange: (String, SkyEventReminderLead?) -> Unit,
    onCometClick: (designation: String) -> Unit,
    onMilkyWayClick: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val lead = uiState.reminders[event.id]
    val reminder = rememberNotificationPermissionToggle(
        isEnabled = lead != null,
        onEnabledChange = { on ->
            onReminderChange(event.id, if (on) lead ?: SkyEventReminderLead.DEFAULT else null)
        },
        snackbarHostState = snackbarHostState,
        blockedMessage = stringResource(R.string.sky_events_notifications_blocked),
    )
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        SkyEventDetails(
            event = event,
            hasPlace = uiState.location != null,
            isPast = event.start.isBefore(uiState.now),
            reminderOn = reminder.isOn,
            reminderLead = lead,
            onReminderClick = reminder.onClick,
            onLeadChange = { onReminderChange(event.id, it) },
            onCometClick = onCometClick,
            onMilkyWayClick = onMilkyWayClick,
            modifier = Modifier.verticalScroll(rememberScrollState()),
        )
    }
}


@Composable
private fun MonthSwitcher(month: YearMonth, onPrevious: () -> Unit, onNext: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
        IconButton(onClick = onPrevious) {
            Icon(
                Icons.AutoMirrored.Outlined.KeyboardArrowLeft,
                contentDescription = stringResource(R.string.sky_events_previous_month),
            )
        }
        Text(
            text = month.format(skeleton = "yMMMM"),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center,
        )
        IconButton(onClick = onNext) {
            Icon(
                Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = stringResource(R.string.sky_events_next_month),
            )
        }
    }
}

@Composable
private fun CategoryChips(selected: Set<SkyEventCategory>, onToggle: (SkyEventCategory) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(SkyEventCategory.entries) { category ->
            FilterChip(
                selected = category in selected,
                onClick = { onToggle(category) },
                label = { Text(stringResource(category.labelRes)) },
                leadingIcon = { Icon(category.icon, contentDescription = null, modifier = Modifier.size(18.dp)) },
            )
        }
    }
}

@Composable
private fun EventList(uiState: SkyEventsUiState, onSetLocation: () -> Unit, onEventClick: (SkyEvent) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag(SKY_EVENTS_LIST_TEST_TAG),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        if (uiState.isLocationLoaded) {
            item(key = "place") { PlaceLine(uiState.location, onSetLocation) }
        }
        if (uiState.weeks.isEmpty()) {
            item(key = "empty") { EmptyState(title = stringResource(R.string.sky_events_empty)) }
        }
        uiState.weeks.forEach { week ->
            item(key = "week-${week.firstDay}") { WeekHeader(week.firstDay, week.lastDay) }
            items(week.events, key = { it.id }) { event ->
                SkyEventRow(
                    event = event,
                    hasPlace = uiState.location != null,
                    hasReminder = event.id in uiState.reminders,
                    isPast = event.start.isBefore(uiState.now),
                    onClick = { onEventClick(event) },
                )
            }
        }
    }
}

@Composable
private fun PlaceLine(location: ObserverLocation?, onSetLocation: () -> Unit) {
    if (location == null) {
        OutlinedCard(Modifier.fillMaxWidth().padding(16.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.sky_events_no_place), style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = onSetLocation, modifier = Modifier.align(Alignment.End)) {
                    Text(stringResource(R.string.sky_events_set_place))
                }
            }
        }
    } else {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Icon(
                Icons.Outlined.LocationOn,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val units = rememberUnitFormatter()
            val place = location.name ?: units.coordinate(location.coordinate.latitude, location.coordinate.longitude)
            Text(
                text = stringResource(R.string.sky_events_for_place, place),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun WeekHeader(firstDay: LocalDate, lastDay: LocalDate) {
    val text = if (firstDay == lastDay) {
        firstDay.format(skeleton = "EEEdMMM")
    } else {
        stringResource(R.string.sky_events_week, firstDay.format(skeleton = "dMMM"), lastDay.format(skeleton = "dMMM"))
    }
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

/** A date in the locale's own order and words for [skeleton] ("yMMMM" is "October 2026"). */
@Composable
private fun YearMonth.format(skeleton: String): String = atDay(1).format(skeleton)

@Composable
private fun LocalDate.format(skeleton: String): String {
    val locale = Locale.current.platformLocale
    return format(DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, skeleton), locale))
}

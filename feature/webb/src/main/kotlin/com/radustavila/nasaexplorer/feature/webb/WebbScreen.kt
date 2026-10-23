package com.radustavila.nasaexplorer.feature.webb

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.radustavila.nasaexplorer.core.designsystem.component.EmptyState
import com.radustavila.nasaexplorer.core.designsystem.component.FreshnessLabel
import com.radustavila.nasaexplorer.core.designsystem.component.LoadingState
import com.radustavila.nasaexplorer.core.designsystem.component.OfflineBanner
import com.radustavila.nasaexplorer.core.designsystem.component.OfflineState
import com.radustavila.nasaexplorer.core.designsystem.component.SectionTitle
import com.radustavila.nasaexplorer.core.designsystem.units.rememberUnitFormatter
import com.radustavila.nasaexplorer.core.model.WebbNow
import com.radustavila.nasaexplorer.core.model.WebbVisit
import com.radustavila.nasaexplorer.feature.webb.component.durationText
import com.radustavila.nasaexplorer.feature.webb.component.instrumentLine
import com.radustavila.nasaexplorer.feature.webb.component.subjectLine
import com.radustavila.nasaexplorer.feature.webb.component.timeNear
import java.time.LocalDate
import java.time.ZoneId

const val WEBB_TIMELINE_TEST_TAG = "webb-timeline"

@Composable
fun WebbScreen(
    onBackClick: () -> Unit,
    onTargetClick: (query: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: WebbViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val failedMessage = stringResource(R.string.webb_refresh_failed)
    LaunchedEffect(uiState.refreshFailed) {
        if (uiState.refreshFailed) {
            snackbarHostState.showSnackbar(failedMessage)
            viewModel.onRefreshFailedShown()
        }
    }
    WebbContent(
        uiState = uiState,
        onBackClick = onBackClick,
        onTargetClick = onTargetClick,
        onRefresh = viewModel::onRefresh,
        snackbarHostState = snackbarHostState,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebbContent(
    uiState: WebbUiState,
    onBackClick: () -> Unit,
    onTargetClick: (query: String) -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.webb_title)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.webb_back),
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(Modifier.padding(padding)) {
            if (uiState.freshness.isOffline && uiState.freshness.lastUpdatedAt != null) OfflineBanner()
            PullToRefreshBox(
                isRefreshing = uiState.isRefreshing,
                onRefresh = onRefresh,
                modifier = Modifier.fillMaxSize(),
            ) {
                val now = uiState.now
                when {
                    !uiState.isLoaded -> LoadingState()
                    now == null -> NeverDownloaded(uiState.freshness.isOffline)
                    else -> Timeline(uiState, now, onTargetClick)
                }
            }
        }
    }
}

@Composable
private fun NeverDownloaded(isOffline: Boolean) {
    if (isOffline) {
        OfflineState(
            title = stringResource(R.string.webb_offline_title),
            description = stringResource(R.string.webb_offline_description),
        )
    } else {
        EmptyState(
            title = stringResource(R.string.webb_none_title),
            description = stringResource(R.string.webb_none_description),
        )
    }
}

@Composable
private fun Timeline(uiState: WebbUiState, now: WebbNow, onTargetClick: (String) -> Unit) {
    val zone = ZoneId.systemDefault()
    val days = remember(uiState.visits, zone) { uiState.visits.groupBy { it.start.atZone(zone).toLocalDate() } }
    // Opens just above the visit scheduled now (or the next one); a stale week opens on its note.
    val focus = now.current ?: now.upNext.firstOrNull()
    val focusIndex = remember(days, focus, now.isStale) {
        focus?.takeUnless { now.isStale }?.let { rowIndex(days, it) } ?: 0
    }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = focusIndex)
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            state = listState,
            modifier = Modifier.widthIn(max = TIMELINE_MAX_WIDTH).fillMaxSize().testTag(WEBB_TIMELINE_TEST_TAG),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "freshness") { FreshnessLabel(uiState.freshness) }
            if (now.isStale) item(key = "stale") { StaleNote(now) }
            days.forEach { (day, visits) -> day(day, visits, now, onTargetClick) }
            item(key = "footer") { Footer() }
        }
    }
}

/** The index of the item just above [visit]'s row: after the freshness line, each day's title and its visits. */
private fun rowIndex(days: Map<LocalDate, List<WebbVisit>>, visit: WebbVisit): Int {
    var index = 1
    days.values.forEach { visits ->
        index++ // the day's title
        val position = visits.indexOf(visit)
        if (position >= 0) return index + position - 1
        index += visits.size
    }
    return 0
}

private fun LazyListScope.day(
    day: LocalDate,
    visits: List<WebbVisit>,
    now: WebbNow,
    onTargetClick: (String) -> Unit,
) {
    item(key = "day-$day") {
        val units = rememberUnitFormatter()
        SectionTitle(units.day(visits.first().start), modifier = Modifier.padding(top = 8.dp))
    }
    items(visits, key = { "${it.visitId}@${it.start}" }) { visit ->
        VisitRow(visit, now, onTargetClick)
    }
}

@Composable
private fun VisitRow(visit: WebbVisit, now: WebbNow, onTargetClick: (String) -> Unit) {
    val units = rememberUnitFormatter()
    val isCurrent = visit == now.current
    val isPast = !visit.end.isAfter(now.now)
    val colors = MaterialTheme.colorScheme
    val background = if (isCurrent) colors.primaryContainer else colors.surfaceContainer
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (isPast) PAST_ALPHA else 1f)
            .background(background, RoundedCornerShape(12.dp))
            .padding(12.dp),
    ) {
        Column(Modifier.widthIn(min = TIME_COLUMN_WIDTH)) {
            Text(units.time(visit.start), style = MaterialTheme.typography.labelLarge)
            Text(
                durationText(visit.duration),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (isCurrent) {
                Text(
                    stringResource(R.string.webb_now_badge),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f)) {
            TargetName(visit, onTargetClick)
            Text(visit.instrumentLine(), style = MaterialTheme.typography.bodyMedium)
            visit.subjectLine()?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (visit.parallels.isNotEmpty()) {
                Text(
                    stringResource(
                        R.string.webb_parallels,
                        visit.parallels.joinToString(stringResource(R.string.webb_list_separator)),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                listOf(
                    stringResource(
                        R.string.webb_time_range,
                        units.time(visit.start),
                        units.timeNear(visit.end, visit.start),
                    ),
                    stringResource(R.string.webb_program, visit.program),
                ).joinToString(stringResource(R.string.webb_separator)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Footer() {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 8.dp)) {
        Text(
            stringResource(R.string.webb_disclaimer),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            stringResource(R.string.webb_credit),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private const val PAST_ALPHA = 0.6f
private val TIMELINE_MAX_WIDTH = 720.dp
private val TIME_COLUMN_WIDTH = 64.dp

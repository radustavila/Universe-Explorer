package com.radustavila.nasaexplorer.feature.feed

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberDatePickerState
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.radustavila.nasaexplorer.core.designsystem.component.ApodCard
import com.radustavila.nasaexplorer.core.designsystem.component.EmptyState
import com.radustavila.nasaexplorer.core.designsystem.component.LoadingState
import com.radustavila.nasaexplorer.core.designsystem.component.OfflineBanner
import com.radustavila.nasaexplorer.core.designsystem.component.OfflineState
import com.radustavila.nasaexplorer.core.designsystem.error.asText
import com.radustavila.nasaexplorer.core.designsystem.theme.NasaTheme
import com.radustavila.nasaexplorer.core.model.Apod
import com.radustavila.nasaexplorer.core.model.MediaType
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter

const val FEED_LIST_TEST_TAG = "feed-list"
const val FEED_CALENDAR_BUTTON_TEST_TAG = "feed-calendar-button"

@Composable
fun FeedScreen(
    onApodClick: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: FeedViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    FeedContent(
        uiState = uiState,
        onApodClick = onApodClick,
        onToggleFavorite = viewModel::onToggleFavorite,
        onRefresh = viewModel::onRefresh,
        onRetry = viewModel::onRetry,
        onLoadMore = viewModel::onLoadMore,
        onErrorDismissed = viewModel::onErrorDismissed,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FeedContent(
    uiState: FeedUiState,
    onApodClick: (LocalDate) -> Unit,
    onToggleFavorite: (Apod) -> Unit,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onErrorDismissed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    val errorMessage = uiState.error?.asText()
    var isDatePickerOpen by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(errorMessage) {
        if (errorMessage != null && uiState.hasContent) {
            snackbarHostState.showSnackbar(errorMessage)
            onErrorDismissed()
        }
    }

    NotifyWhenNearEnd(listState = listState, itemCount = uiState.items.size, onLoadMore = onLoadMore)

    Scaffold(
        modifier = modifier,
        // The top bar already insets itself below the status bar, and the navigation bar is padded
        // by the app scaffold; taking the insets again here left a gap at both ends of the list.
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.feed_title)) },
                actions = {
                    IconButton(
                        onClick = { isDatePickerOpen = true },
                        modifier = Modifier.testTag(FEED_CALENDAR_BUTTON_TEST_TAG),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.CalendarMonth,
                            contentDescription = stringResource(R.string.feed_pick_date),
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                if (uiState.isOffline && uiState.hasContent) {
                    OfflineBanner()
                }

                when {
                    uiState.isLoading && !uiState.hasContent -> LoadingState()

                    uiState.isOfflineEmpty -> OfflineState(
                        title = stringResource(R.string.feed_offline_title),
                        description = stringResource(R.string.feed_offline_description),
                        onRetry = onRetry,
                    )

                    uiState.isEmpty -> EmptyState(
                        title = errorMessage ?: stringResource(R.string.feed_empty_title),
                        description = stringResource(R.string.feed_empty_description),
                        onRetry = onRetry,
                    )

                    else -> FeedList(
                        uiState = uiState,
                        listState = listState,
                        onApodClick = onApodClick,
                        onToggleFavorite = onToggleFavorite,
                    )
                }
            }
        }
    }

    if (isDatePickerOpen) {
        ArchiveDatePickerDialog(
            latestSelectableDate = uiState.latestSelectableDate,
            onDateSelected = { date ->
                isDatePickerOpen = false
                onApodClick(date)
            },
            onDismiss = { isDatePickerOpen = false },
        )
    }
}

/**
 * Jumps straight to any published day instead of scrolling the archive back to it. The details
 * screen downloads the day if the feed has not cached it yet, so the whole archive is reachable in
 * one tap.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ArchiveDatePickerDialog(
    latestSelectableDate: LocalDate,
    onDateSelected: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = latestSelectableDate.toUtcMillis(),
        yearRange = Apod.FIRST_PUBLICATION_DATE.year..latestSelectableDate.year,
        selectableDates = remember(latestSelectableDate) {
            PublishedDates(latestSelectableDate)
        },
    )
    val selectedDate = state.selectedDateMillis?.toUtcLocalDate()

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = { selectedDate?.let(onDateSelected) },
                enabled = selectedDate != null,
            ) {
                Text(stringResource(R.string.feed_date_picker_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.feed_date_picker_dismiss))
            }
        },
    ) {
        DatePicker(
            state = state,
            title = {
                Text(
                    text = stringResource(R.string.feed_date_picker_title),
                    modifier = Modifier.padding(start = 24.dp, end = 12.dp, top = 16.dp),
                )
            },
        )
    }
}

/**
 * Only days NASA has actually published: nothing before the first APOD, nothing in the future.
 */
@OptIn(ExperimentalMaterial3Api::class)
internal class PublishedDates(private val latest: LocalDate) : SelectableDates {

    override fun isSelectableDate(utcTimeMillis: Long): Boolean {
        val date = utcTimeMillis.toUtcLocalDate()
        return !date.isBefore(Apod.FIRST_PUBLICATION_DATE) && !date.isAfter(latest)
    }

    override fun isSelectableYear(year: Int): Boolean =
        year in Apod.FIRST_PUBLICATION_DATE.year..latest.year
}

// The Material date picker speaks in UTC milliseconds; the archive speaks in UTC dates.
internal fun LocalDate.toUtcMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

internal fun Long.toUtcLocalDate(): LocalDate =
    Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()

@Composable
private fun FeedList(
    uiState: FeedUiState,
    listState: LazyListState,
    onApodClick: (LocalDate) -> Unit,
    onToggleFavorite: (Apod) -> Unit,
) {
    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .testTag(FEED_LIST_TEST_TAG),
        // The gutter matches the gap between cards, so the cards sit in an even 16dp frame.
        contentPadding = PaddingValues(all = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        items(items = uiState.items, key = { apod -> apod.date.toString() }) { apod ->
            ApodCard(
                apod = apod,
                onClick = { onApodClick(apod.date) },
                onToggleFavorite = { onToggleFavorite(apod) },
            )
        }

        if (uiState.isLoadingMore) {
            item(key = "loading-more") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.feed_loading_more),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (uiState.hasReachedArchiveStart) {
            item(key = "archive-start") {
                Text(
                    text = stringResource(R.string.feed_archive_start),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                )
            }
        }
    }
}

/**
 * Date-range pagination trigger: when the user gets within [LOAD_MORE_THRESHOLD] items of the end,
 * ask for the next (older) window. Driven by `snapshotFlow` so scrolling itself stays allocation
 * free.
 */
@Composable
private fun NotifyWhenNearEnd(
    listState: LazyListState,
    itemCount: Int,
    onLoadMore: () -> Unit,
) {
    val shouldLoadMore by remember(itemCount) {
        derivedStateOf {
            if (itemCount == 0) return@derivedStateOf false
            val lastVisibleIndex = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisibleIndex >= itemCount - LOAD_MORE_THRESHOLD
        }
    }

    LaunchedEffect(listState, itemCount) {
        snapshotFlow { shouldLoadMore }
            .distinctUntilChanged()
            .filter { it }
            .collect { onLoadMore() }
    }
}

private const val LOAD_MORE_THRESHOLD = 3

@Preview
@Composable
private fun FeedScreenPreview() {
    NasaTheme {
        FeedContent(
            uiState = FeedUiState(
                items = listOf(
                    Apod(
                        date = LocalDate.of(2026, 9, 10),
                        title = "Spiral Galaxy NGC 1300",
                        explanation = "A barred spiral galaxy.",
                        mediaType = MediaType.IMAGE,
                        url = "https://apod.nasa.gov/apod/image/ngc1300.jpg",
                        isFavorite = true,
                    ),
                ),
            ),
            onApodClick = {},
            onToggleFavorite = {},
            onRefresh = {},
            onRetry = {},
            onLoadMore = {},
            onErrorDismissed = {},
        )
    }
}

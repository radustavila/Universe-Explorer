package com.radustavila.nasaexplorer.feature.earth

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.radustavila.nasaexplorer.core.designsystem.component.EmptyState
import com.radustavila.nasaexplorer.core.designsystem.component.LoadingState
import com.radustavila.nasaexplorer.core.designsystem.component.NasaAsyncImage
import com.radustavila.nasaexplorer.core.designsystem.component.OfflineBanner
import com.radustavila.nasaexplorer.core.designsystem.component.OfflineState
import com.radustavila.nasaexplorer.core.designsystem.component.ZoomableImageDialog
import com.radustavila.nasaexplorer.core.designsystem.error.asText
import com.radustavila.nasaexplorer.core.designsystem.theme.NasaTheme
import com.radustavila.nasaexplorer.core.designsystem.util.formatDateTime
import com.radustavila.nasaexplorer.core.model.EpicCollection
import com.radustavila.nasaexplorer.core.model.EpicImage
import com.radustavila.nasaexplorer.core.model.GeoPoint
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale
import kotlin.math.abs
import kotlinx.coroutines.delay

const val EARTH_PAGER_TEST_TAG = "earth-pager"
const val EARTH_CALENDAR_BUTTON_TEST_TAG = "earth-calendar-button"
const val EARTH_PLAY_BUTTON_TEST_TAG = "earth-play-button"

@Composable
fun EarthTodayScreen(
    onBackClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    viewModel: EarthTodayViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    EarthTodayContent(
        uiState = uiState,
        onBackClick = onBackClick,
        onCollectionSelected = viewModel::onCollectionSelected,
        onDateSelected = viewModel::onDateSelected,
        onRetry = viewModel::onRetry,
        onErrorDismissed = viewModel::onErrorDismissed,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EarthTodayContent(
    uiState: EarthTodayUiState,
    onBackClick: (() -> Unit)?,
    onCollectionSelected: (EpicCollection) -> Unit,
    onDateSelected: (LocalDate?) -> Unit,
    onRetry: () -> Unit,
    onErrorDismissed: () -> Unit,
    modifier: Modifier = Modifier,
    /** Tests turn this off: an endless rotation would never let the UI go idle. */
    autoPlay: Boolean = true,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val errorMessage = uiState.error?.asText()
    var isDatePickerOpen by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(errorMessage) {
        if (errorMessage != null && uiState.hasContent) {
            snackbarHostState.showSnackbar(errorMessage)
            onErrorDismissed()
        }
    }

    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.earth_today_title)) },
                navigationIcon = {
                    if (onBackClick != null) {
                        IconButton(onClick = onBackClick) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                                contentDescription = stringResource(R.string.earth_today_back),
                            )
                        }
                    }
                },
                actions = {
                    if (uiState.selectedDate != null) {
                        IconButton(onClick = { onDateSelected(null) }) {
                            Icon(
                                imageVector = Icons.Outlined.Today,
                                contentDescription = stringResource(R.string.earth_today_latest),
                            )
                        }
                    }
                    IconButton(
                        onClick = { isDatePickerOpen = true },
                        enabled = uiState.canPickDate,
                        modifier = Modifier.testTag(EARTH_CALENDAR_BUTTON_TEST_TAG),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.CalendarMonth,
                            contentDescription = stringResource(R.string.earth_today_pick_date),
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (uiState.isOffline && uiState.hasContent) OfflineBanner()
            if (uiState.isRefreshing) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())

            CollectionToggle(
                selected = uiState.collection,
                onSelected = onCollectionSelected,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )

            when {
                uiState.isLoading && !uiState.hasContent -> LoadingState()

                uiState.isOfflineEmpty -> OfflineState(
                    title = stringResource(R.string.earth_today_offline_title),
                    description = stringResource(R.string.earth_today_offline_description),
                    onRetry = onRetry,
                )

                uiState.isEmpty -> EmptyState(
                    title = errorMessage ?: stringResource(R.string.earth_today_empty_title),
                    description = stringResource(R.string.earth_today_empty_description),
                    onRetry = onRetry,
                )

                // Keyed by the day and set, so a new selection starts its rotation from the first frame.
                else -> key(uiState.collection, uiState.displayedDate) {
                    EarthRotation(images = uiState.images, autoPlay = autoPlay)
                }
            }
        }
    }

    if (isDatePickerOpen) {
        EpicDatePickerDialog(
            availableDates = uiState.availableDates,
            initialDate = uiState.displayedDate,
            onDateSelected = { date ->
                isDatePickerOpen = false
                onDateSelected(date)
            },
            onDismiss = { isDatePickerOpen = false },
        )
    }
}

@Composable
private fun CollectionToggle(
    selected: EpicCollection,
    onSelected: (EpicCollection) -> Unit,
    modifier: Modifier = Modifier,
) {
    val labels = mapOf(
        EpicCollection.NATURAL to stringResource(R.string.earth_today_collection_natural),
        EpicCollection.ENHANCED to stringResource(R.string.earth_today_collection_enhanced),
    )
    SingleChoiceSegmentedButtonRow(modifier = modifier) {
        EpicCollection.entries.forEachIndexed { index, collection ->
            SegmentedButton(
                selected = collection == selected,
                onClick = { onSelected(collection) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = EpicCollection.entries.size),
            ) {
                Text(labels.getValue(collection))
            }
        }
    }
}

/**
 * The day's photos as a rotation: a pager that advances on its own, so Earth visibly turns.
 *
 * While playing, frames are the few-KB thumbnails, which the sync prefetches and therefore play
 * offline. Once paused (or swiped by hand) the frame on screen sharpens to the full JPEG, drawn
 * over its thumbnail; tapping it opens the zoomable full-size view.
 */
@Composable
private fun EarthRotation(images: List<EpicImage>, autoPlay: Boolean) {
    val pagerState = rememberPagerState(pageCount = { images.size })
    var isPlaying by rememberSaveable { mutableStateOf(autoPlay) }
    var zoomedImage by remember { mutableStateOf<EpicImage?>(null) }
    val isDragged by pagerState.interactionSource.collectIsDraggedAsState()

    // Taking the pager in hand means looking at one frame: stop turning it underneath the finger.
    LaunchedEffect(isDragged) {
        if (isDragged) isPlaying = false
    }

    AutoAdvance(pagerState = pagerState, isPlaying = isPlaying && images.size > 1)

    val current = images[pagerState.currentPage.coerceIn(images.indices)]

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        HorizontalPager(
            state = pagerState,
            key = { page -> images[page].identifier },
            modifier = Modifier
                .widthIn(max = MAX_DISC_WIDTH)
                .fillMaxWidth()
                .aspectRatio(1f)
                .testTag(EARTH_PAGER_TEST_TAG),
        ) { page ->
            val image = images[page]
            val description = stringResource(
                R.string.earth_today_image_description,
                image.capturedAt.formatDateTime(),
                image.centroid.toCoordinateLabel(),
            )
            NasaAsyncImage(
                url = if (isPlaying) image.thumbnailUrl else image.imageUrl,
                previewUrl = image.thumbnailUrl,
                contentDescription = description,
                contentScale = ContentScale.Fit,
                backgroundColor = Color.Black,
                modifier = Modifier
                    .fillMaxSize()
                    .clickable {
                        isPlaying = false
                        zoomedImage = image
                    },
            )
        }

        FrameDetails(
            image = current,
            frameNumber = pagerState.currentPage + 1,
            frameCount = images.size,
            isPlaying = isPlaying,
            onPlayPause = { isPlaying = !isPlaying },
        )
    }

    zoomedImage?.let { image ->
        ZoomableImageDialog(
            url = image.imageUrl,
            previewUrl = image.thumbnailUrl,
            contentDescription = image.caption,
            onDismiss = { zoomedImage = null },
        )
    }
}

@Composable
private fun AutoAdvance(pagerState: PagerState, isPlaying: Boolean) {
    LaunchedEffect(pagerState, isPlaying) {
        if (!isPlaying) return@LaunchedEffect
        while (true) {
            delay(FRAME_DURATION_MILLIS)
            val next = (pagerState.currentPage + 1) % pagerState.pageCount
            // Wrapping around jumps instead of sliding back across the whole day.
            if (next == 0) pagerState.scrollToPage(next) else pagerState.animateScrollToPage(next)
        }
    }
}

@Composable
private fun FrameDetails(
    image: EpicImage,
    frameNumber: Int,
    frameCount: Int,
    isPlaying: Boolean,
    onPlayPause: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            FilledTonalIconButton(
                onClick = onPlayPause,
                modifier = Modifier.testTag(EARTH_PLAY_BUTTON_TEST_TAG),
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                    contentDescription = stringResource(
                        if (isPlaying) R.string.earth_today_pause else R.string.earth_today_play,
                    ),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = image.capturedAt.formatDateTime(),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = stringResource(R.string.earth_today_facing, image.centroid.toCoordinateLabel()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = stringResource(R.string.earth_today_frame, frameNumber, frameCount),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = stringResource(R.string.earth_today_zoom_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(R.string.earth_today_source),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Start,
        )
    }
}

/** Offers exactly the days EPIC has photos for: the archive has gaps, and nothing exists beyond it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EpicDatePickerDialog(
    availableDates: Set<LocalDate>,
    initialDate: LocalDate?,
    onDateSelected: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    val selectableDates = remember(availableDates) { AvailableEpicDates(availableDates) }
    val state = rememberDatePickerState(
        initialSelectedDateMillis = (initialDate ?: availableDates.maxOrNull())?.toUtcMillis(),
        yearRange = selectableDates.yearRange,
        selectableDates = selectableDates,
    )
    val selectedDate = state.selectedDateMillis?.toUtcLocalDate()?.takeIf { it in availableDates }

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = { selectedDate?.let(onDateSelected) },
                enabled = selectedDate != null,
            ) {
                Text(stringResource(R.string.earth_today_date_picker_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.earth_today_date_picker_dismiss))
            }
        },
    ) {
        DatePicker(
            state = state,
            title = {
                Text(
                    text = stringResource(R.string.earth_today_date_picker_title),
                    modifier = Modifier.padding(start = 24.dp, end = 12.dp, top = 16.dp),
                )
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
internal class AvailableEpicDates(private val dates: Set<LocalDate>) : SelectableDates {

    private val years: Set<Int> = dates.mapTo(mutableSetOf(), LocalDate::getYear)

    val yearRange: IntRange =
        (years.minOrNull() ?: EpicImage.FIRST_CAPTURE_DATE.year)..(years.maxOrNull() ?: LocalDate.now().year)

    override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis.toUtcLocalDate() in dates

    override fun isSelectableYear(year: Int): Boolean = year in years
}

// The Material date picker speaks in UTC milliseconds; EPIC files its archive by UTC day.
internal fun LocalDate.toUtcMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

internal fun Long.toUtcLocalDate(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()

/** `0.7° N, 163.7° E`: compass letters rather than signs, which read naturally under a photo. */
internal fun GeoPoint.toCoordinateLabel(): String {
    val latitudeHemisphere = if (latitude < 0) "S" else "N"
    val longitudeHemisphere = if (longitude < 0) "W" else "E"
    return "%.1f° %s, %.1f° %s".format(
        Locale.ROOT,
        abs(latitude),
        latitudeHemisphere,
        abs(longitude),
        longitudeHemisphere,
    )
}

private val MAX_DISC_WIDTH = 480.dp
private const val FRAME_DURATION_MILLIS = 1_000L

@Preview
@Composable
private fun EarthTodayPreview() {
    val date = LocalDate.of(2026, 10, 16)
    NasaTheme {
        EarthTodayContent(
            uiState = EarthTodayUiState(
                images = List(3) { index ->
                    EpicImage(
                        identifier = "2026101600$index",
                        collection = EpicCollection.NATURAL,
                        capturedAt = date.atStartOfDay(ZoneOffset.UTC).toInstant(),
                        caption = "This image was taken by NASA's EPIC camera onboard the NOAA DSCOVR spacecraft",
                        centroid = GeoPoint(latitude = 0.66, longitude = 163.7 - index * 27),
                        thumbnailUrl = "",
                        imageUrl = "",
                    )
                },
                availableDates = setOf(date),
            ),
            onBackClick = {},
            onCollectionSelected = {},
            onDateSelected = {},
            onRetry = {},
            onErrorDismissed = {},
            autoPlay = false,
        )
    }
}

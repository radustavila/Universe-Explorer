package com.radustavila.nasaexplorer.feature.mars.photos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
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
import com.radustavila.nasaexplorer.core.designsystem.util.formatMedium
import com.radustavila.nasaexplorer.core.model.MarsRover
import com.radustavila.nasaexplorer.feature.mars.MarsPhotoThumbnail
import com.radustavila.nasaexplorer.feature.mars.R
import java.time.ZoneId

const val MARS_PHOTOS_LIST_TEST_TAG = "mars-photos-list"

fun marsRoverTabTestTag(rover: MarsRover): String = "mars-rover-${rover.key}"

@Composable
fun MarsPhotosScreen(
    onBackClick: () -> Unit,
    onPhotoClick: (id: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MarsPhotosViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    MarsPhotosContent(
        uiState = uiState,
        onBackClick = onBackClick,
        onPhotoClick = onPhotoClick,
        onRoverSelected = viewModel::onRoverSelected,
        onRefresh = viewModel::onRefresh,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MarsPhotosContent(
    uiState: MarsPhotosUiState,
    onBackClick: () -> Unit,
    onPhotoClick: (id: String) -> Unit,
    onRoverSelected: (MarsRover) -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        // Inside the Universe tab: the app scaffold's navigation bar owns the bottom inset.
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.mars_photos_title)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.mars_photos_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            RoverSwitcher(
                selected = uiState.rover,
                onSelected = onRoverSelected,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
            if (uiState.isOffline) OfflineBanner()

            PullToRefreshBox(
                isRefreshing = uiState.isSyncing && uiState.sols.isNotEmpty(),
                onRefresh = onRefresh,
                modifier = Modifier.fillMaxSize(),
            ) {
                when {
                    uiState.sols.isNotEmpty() -> SolList(uiState = uiState, onPhotoClick = onPhotoClick)
                    !uiState.isLoaded || uiState.isSyncing -> LoadingState()
                    uiState.isOffline -> ScrollableMessage { messageModifier ->
                        OfflineState(
                            title = stringResource(R.string.mars_photos_offline_title),
                            description = stringResource(R.string.mars_photos_offline_description),
                            modifier = messageModifier,
                        )
                    }

                    uiState.lastAttemptFailed -> ScrollableMessage { messageModifier ->
                        EmptyState(
                            title = stringResource(R.string.mars_photos_failed_title, uiState.rover.displayName),
                            description = stringResource(R.string.mars_photos_failed_description),
                            onRetry = onRefresh,
                            modifier = messageModifier,
                        )
                    }

                    else -> ScrollableMessage { messageModifier ->
                        EmptyState(
                            title = stringResource(R.string.mars_photos_empty_title, uiState.rover.displayName),
                            description = stringResource(R.string.mars_photos_empty_description),
                            onRetry = onRefresh,
                            modifier = messageModifier,
                        )
                    }
                }
            }
        }
    }
}

/**
 * A message that is centred while it fits and scrolls once it does not (landscape), which also lets
 * pull-to-refresh work over it.
 */
@Composable
private fun ScrollableMessage(content: @Composable (Modifier) -> Unit) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val viewportHeight = maxHeight
        Box(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            content(Modifier.heightIn(min = viewportHeight))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RoverSwitcher(
    selected: MarsRover,
    onSelected: (MarsRover) -> Unit,
    modifier: Modifier = Modifier,
) {
    SingleChoiceSegmentedButtonRow(modifier = modifier) {
        MarsRover.entries.forEachIndexed { index, rover ->
            SegmentedButton(
                selected = rover == selected,
                onClick = { onSelected(rover) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = MarsRover.entries.size),
                modifier = Modifier.testTag(marsRoverTabTestTag(rover)),
            ) {
                Text(rover.displayName)
            }
        }
    }
}

@Composable
private fun SolList(
    uiState: MarsPhotosUiState,
    onPhotoClick: (id: String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag(MARS_PHOTOS_LIST_TEST_TAG),
        contentPadding = PaddingValues(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (uiState.lastUpdatedAt != null) {
            item(key = "freshness", contentType = "freshness") {
                FreshnessLabel(uiState.freshness, modifier = Modifier.padding(horizontal = 16.dp))
            }
        }
        uiState.sols.forEach { group ->
            item(key = "${uiState.rover.key}-sol-${group.sol}", contentType = "sol") { SolHeader(group) }
            items(
                items = group.cameras,
                key = { camera -> "${uiState.rover.key}-${group.sol}-${camera.cameraName}" },
                contentType = { "camera" },
            ) { camera ->
                CameraRow(camera = camera, onPhotoClick = onPhotoClick)
            }
        }
    }
}

@Composable
private fun SolHeader(group: SolGroup) {
    val date = group.takenAt?.atZone(ZoneId.systemDefault())?.toLocalDate()?.formatMedium()
    Column(modifier = Modifier.padding(horizontal = 16.dp).padding(top = 8.dp)) {
        SectionTitle(
            text = stringResource(R.string.mars_photo_sol, group.sol),
            style = MaterialTheme.typography.titleLarge,
        )
        if (date != null) {
            Text(
                text = date,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CameraRow(camera: CameraGroup, onPhotoClick: (id: String) -> Unit) {
    Column {
        SectionTitle(
            text = camera.cameraName,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(items = camera.photos, key = { it.id }) { photo ->
                MarsPhotoThumbnail(
                    photo = photo,
                    onClick = { onPhotoClick(photo.id) },
                    modifier = Modifier.width(THUMBNAIL_WIDTH),
                )
            }
        }
    }
}

private val THUMBNAIL_WIDTH = 112.dp

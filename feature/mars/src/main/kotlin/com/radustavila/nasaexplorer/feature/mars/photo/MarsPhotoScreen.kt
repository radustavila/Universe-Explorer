package com.radustavila.nasaexplorer.feature.mars.photo

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.ZoomIn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.radustavila.nasaexplorer.core.designsystem.component.EmptyState
import com.radustavila.nasaexplorer.core.designsystem.component.FavoriteButton
import com.radustavila.nasaexplorer.core.designsystem.component.LoadingState
import com.radustavila.nasaexplorer.core.designsystem.component.NasaAsyncImage
import com.radustavila.nasaexplorer.core.designsystem.component.ZoomableImageDialog
import com.radustavila.nasaexplorer.core.designsystem.error.asText
import com.radustavila.nasaexplorer.core.designsystem.util.formatDateTime
import com.radustavila.nasaexplorer.core.model.MarsPhoto
import com.radustavila.nasaexplorer.feature.mars.R

const val MARS_PHOTO_CONTENT_TEST_TAG = "mars-photo-content"
const val MARS_PHOTO_ZOOM_TEST_TAG = "mars-photo-zoom"

@Composable
fun MarsPhotoScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MarsPhotoViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    MarsPhotoContent(
        uiState = uiState,
        onBackClick = onBackClick,
        onToggleFavorite = viewModel::onToggleFavorite,
        onErrorDismissed = viewModel::onErrorDismissed,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MarsPhotoContent(
    uiState: MarsPhotoUiState,
    onBackClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onErrorDismissed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val errorMessage = (uiState as? MarsPhotoUiState.Content)?.error?.asText()

    LaunchedEffect(errorMessage) {
        if (errorMessage != null) {
            snackbarHostState.showSnackbar(errorMessage)
            onErrorDismissed()
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.mars_photos_back),
                        )
                    }
                },
                actions = {
                    if (uiState is MarsPhotoUiState.Content) {
                        FavoriteButton(isFavorite = uiState.isFavorite, onToggle = onToggleFavorite)
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when (uiState) {
                MarsPhotoUiState.Loading -> LoadingState()

                MarsPhotoUiState.Unavailable -> EmptyState(
                    title = stringResource(R.string.mars_photo_missing_title),
                    description = stringResource(R.string.mars_photo_missing_description),
                )

                is MarsPhotoUiState.Content -> PhotoDetails(uiState.photo)
            }
        }
    }
}

@Composable
private fun PhotoDetails(photo: MarsPhoto) {
    var isViewerOpen by rememberSaveable(photo.id) { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .testTag(MARS_PHOTO_CONTENT_TEST_TAG),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(HERO_ASPECT_RATIO)
                .background(Color.Black),
        ) {
            NasaAsyncImage(
                url = photo.imageUrl,
                previewUrl = photo.thumbnailUrl,
                contentDescription = photo.title,
                contentScale = ContentScale.Fit,
                backgroundColor = Color.Black,
                modifier = Modifier
                    .fillMaxSize()
                    .clickable { isViewerOpen = true },
            )
            FilledIconButton(
                onClick = { isViewerOpen = true },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(12.dp)
                    .testTag(MARS_PHOTO_ZOOM_TEST_TAG),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = Color.Black.copy(alpha = 0.5f),
                    contentColor = Color.White,
                ),
            ) {
                Icon(Icons.Outlined.ZoomIn, contentDescription = stringResource(R.string.mars_photo_view_full_size))
            }
        }

        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.mars_photo_rover_sol, photo.rover.displayName, photo.sol),
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(text = photo.cameraName, style = MaterialTheme.typography.titleMedium)
            photo.takenAt?.let { takenAt ->
                Text(
                    text = stringResource(R.string.mars_photo_taken, takenAt.formatDateTime()),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            photo.credit?.let { credit ->
                Text(
                    text = stringResource(R.string.mars_photo_credit, credit),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = stringResource(R.string.mars_photo_raw_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            photo.description?.let { description ->
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }

    if (isViewerOpen) {
        ZoomableImageDialog(
            url = photo.imageUrl,
            previewUrl = photo.thumbnailUrl,
            contentDescription = photo.title,
            onDismiss = { isViewerOpen = false },
        )
    }
}

private const val HERO_ASPECT_RATIO = 4f / 3f

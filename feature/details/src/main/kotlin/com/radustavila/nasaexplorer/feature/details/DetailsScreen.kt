package com.radustavila.nasaexplorer.feature.details

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.radustavila.nasaexplorer.core.designsystem.component.FavoriteButton
import com.radustavila.nasaexplorer.core.designsystem.component.LoadingState
import com.radustavila.nasaexplorer.core.designsystem.component.MediaTypeBadge
import com.radustavila.nasaexplorer.core.designsystem.component.NasaAsyncImage
import com.radustavila.nasaexplorer.core.designsystem.component.OfflineBanner
import com.radustavila.nasaexplorer.core.designsystem.component.VideoPlaceholder
import com.radustavila.nasaexplorer.core.designsystem.component.OfflineState
import com.radustavila.nasaexplorer.core.designsystem.component.EmptyState
import com.radustavila.nasaexplorer.core.designsystem.error.asText
import com.radustavila.nasaexplorer.core.designsystem.util.formatMedium
import com.radustavila.nasaexplorer.core.model.Apod

const val DETAILS_CONTENT_TEST_TAG = "details-content"

@Composable
fun DetailsScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DetailsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    DetailsContent(
        uiState = uiState,
        onBackClick = onBackClick,
        onToggleFavorite = viewModel::onToggleFavorite,
        onRetry = viewModel::onRetry,
        onErrorDismissed = viewModel::onErrorDismissed,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DetailsContent(
    uiState: DetailsUiState,
    onBackClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onRetry: () -> Unit,
    onErrorDismissed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val errorMessage = (uiState as? DetailsUiState.Content)?.error?.asText()

    LaunchedEffect(errorMessage) {
        if (errorMessage != null) {
            snackbarHostState.showSnackbar(errorMessage)
            onErrorDismissed()
        }
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.details_title)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.details_back),
                        )
                    }
                },
                actions = {
                    val apod = (uiState as? DetailsUiState.Content)?.apod
                    if (apod != null) {
                        FavoriteButton(isFavorite = apod.isFavorite, onToggle = onToggleFavorite)
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier
            .fillMaxSize()
            .padding(padding)) {
            when (uiState) {
                DetailsUiState.Loading -> LoadingState()

                is DetailsUiState.Unavailable -> if (uiState.isOffline) {
                    OfflineState(
                        title = stringResource(R.string.details_missing_title),
                        description = stringResource(R.string.details_missing_description),
                        onRetry = onRetry,
                    )
                } else {
                    EmptyState(
                        title = stringResource(R.string.details_missing_title),
                        description = stringResource(R.string.details_missing_description),
                        onRetry = onRetry,
                    )
                }

                is DetailsUiState.Content -> {
                    if (uiState.isOffline) OfflineBanner()
                    ApodDetails(apod = uiState.apod)
                }
            }
        }
    }
}

@Composable
private fun ApodDetails(apod: Apod, modifier: Modifier = Modifier) {
    val context = LocalContext.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .testTag(DETAILS_CONTENT_TEST_TAG),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (apod.fullImageUrl == null && apod.isPlayable) {
            VideoPlaceholder(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f),
            )
        } else {
            NasaAsyncImage(
                url = apod.fullImageUrl,
                contentDescription = apod.title,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp),
            )
        }

        Column(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MediaTypeBadge(mediaType = apod.mediaType)

            Text(text = apod.title, style = MaterialTheme.typography.headlineSmall)

            Text(
                text = apod.date.formatMedium(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            apod.copyright?.let { copyright ->
                Text(
                    text = stringResource(R.string.details_copyright, copyright),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (apod.isPlayable) {
                Button(
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, apod.url.toUri())
                        try {
                            context.startActivity(intent)
                        } catch (_: ActivityNotFoundException) {
                            // Nothing can handle the link; the still frame remains visible.
                        }
                    },
                ) {
                    Icon(imageVector = Icons.Outlined.PlayCircle, contentDescription = null)
                    Text(
                        text = stringResource(R.string.details_watch_video),
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }

            Text(
                text = apod.explanation,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = 24.dp),
            )
        }
    }
}

package com.radustavila.nasaexplorer.feature.universe.details

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.ZoomIn
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.radustavila.nasaexplorer.core.designsystem.component.EmptyState
import com.radustavila.nasaexplorer.core.designsystem.component.FavoriteButton
import com.radustavila.nasaexplorer.core.designsystem.component.LoadingState
import com.radustavila.nasaexplorer.core.designsystem.component.NasaAsyncImage
import com.radustavila.nasaexplorer.core.designsystem.component.NasaMediaPlayer
import com.radustavila.nasaexplorer.core.designsystem.component.OfflineBanner
import com.radustavila.nasaexplorer.core.designsystem.component.OfflineState
import com.radustavila.nasaexplorer.core.designsystem.component.SectionTitle
import com.radustavila.nasaexplorer.core.designsystem.component.ZoomableImageDialog
import com.radustavila.nasaexplorer.core.designsystem.error.asText
import com.radustavila.nasaexplorer.core.designsystem.util.formatMedium
import com.radustavila.nasaexplorer.core.model.NasaMedia
import com.radustavila.nasaexplorer.core.model.NasaMediaType
import com.radustavila.nasaexplorer.feature.universe.R
import kotlinx.coroutines.launch

const val MEDIA_DETAILS_CONTENT_TEST_TAG = "media-details-content"
const val MEDIA_DETAILS_ZOOM_TEST_TAG = "media-details-zoom"

@Composable
fun NasaMediaDetailsScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: NasaMediaDetailsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    NasaMediaDetailsContent(
        uiState = uiState,
        onBackClick = onBackClick,
        onToggleFavorite = viewModel::onToggleFavorite,
        onRetry = viewModel::onRetry,
        onRetryPlayback = viewModel::onRetryPlayback,
        onErrorDismissed = viewModel::onErrorDismissed,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NasaMediaDetailsContent(
    uiState: NasaMediaDetailsUiState,
    onBackClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onRetry: () -> Unit,
    onRetryPlayback: () -> Unit,
    onErrorDismissed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val errorMessage = (uiState as? NasaMediaDetailsUiState.Content)?.error?.asText()
    val downloadStarted = stringResource(R.string.media_details_download_started)
    val shareLabel = stringResource(R.string.media_details_share)

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
                            contentDescription = stringResource(R.string.media_details_back),
                        )
                    }
                },
                actions = {
                    if (uiState is NasaMediaDetailsUiState.Content) {
                        IconButton(onClick = { context.shareNasaMedia(uiState.media, shareLabel) }) {
                            Icon(Icons.Outlined.Share, contentDescription = shareLabel)
                        }
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
                NasaMediaDetailsUiState.Loading -> LoadingState()

                is NasaMediaDetailsUiState.Unavailable -> if (uiState.isOffline) {
                    OfflineState(
                        title = stringResource(R.string.media_details_missing_title),
                        description = stringResource(R.string.media_details_missing_description),
                        onRetry = onRetry,
                    )
                } else {
                    EmptyState(
                        title = uiState.error?.asText() ?: stringResource(R.string.media_details_missing_title),
                        description = stringResource(R.string.media_details_missing_description),
                        onRetry = onRetry,
                    )
                }

                is NasaMediaDetailsUiState.Content -> MediaDetails(
                    content = uiState,
                    onRetryPlayback = onRetryPlayback,
                    onDownload = { url ->
                        context.downloadOriginal(url, uiState.media)
                        scope.launch { snackbarHostState.showSnackbar(downloadStarted) }
                    },
                    onViewOnNasa = { context.openUrl(uiState.media.webPageUrl) },
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MediaDetails(
    content: NasaMediaDetailsUiState.Content,
    onRetryPlayback: () -> Unit,
    onDownload: (url: String) -> Unit,
    onViewOnNasa: () -> Unit,
) {
    val media = content.media
    var isViewerOpen by rememberSaveable(media.nasaId) { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .testTag(MEDIA_DETAILS_CONTENT_TEST_TAG),
    ) {
        when (media.mediaType) {
            NasaMediaType.IMAGE -> ImageHero(
                media = media,
                onViewFullSize = media.fullImageUrl?.let { { isViewerOpen = true } },
            )

            NasaMediaType.VIDEO, NasaMediaType.AUDIO -> PlayerHero(
                media = media,
                playback = content.playback,
                onRetry = onRetryPlayback,
            )
        }

        if (content.isOffline) OfflineBanner()

        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(text = media.title, style = MaterialTheme.typography.headlineSmall)

            val meta = listOfNotNull(media.dateCreated?.formatMedium(), media.center).joinToString(" · ")
            if (meta.isNotEmpty()) {
                Text(
                    text = meta,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            media.creator?.let { creator ->
                Text(
                    text = stringResource(R.string.media_details_credit, creator),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (media.description.isNotEmpty()) Description(text = media.description)

            if (media.keywords.isNotEmpty()) {
                SectionTitle(
                    text = stringResource(R.string.media_details_keywords),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 8.dp),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    media.keywords.forEach { keyword ->
                        SuggestionChip(onClick = {}, enabled = false, label = { Text(keyword) })
                    }
                }
            }

            media.originalUrl?.let { url ->
                Button(onClick = { onDownload(url) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(
                        text = stringResource(R.string.media_details_download),
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
            OutlinedButton(onClick = onViewOnNasa, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(
                    text = stringResource(R.string.media_details_view_on_nasa),
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    }

    val fullImageUrl = media.fullImageUrl
    if (isViewerOpen && fullImageUrl != null) {
        ZoomableImageDialog(
            url = fullImageUrl,
            previewUrl = media.imageUrl,
            contentDescription = media.altText ?: media.title,
            onDismiss = { isViewerOpen = false },
        )
    }
}

/** `~large` on screen (the copy the Favourites snapshot and the offline cache keep). */
@Composable
private fun ImageHero(media: NasaMedia, onViewFullSize: (() -> Unit)?) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(HERO_ASPECT_RATIO)
            .background(Color.Black),
    ) {
        NasaAsyncImage(
            url = media.imageUrl,
            previewUrl = media.thumbnailUrl,
            contentDescription = media.altText ?: media.title,
            contentScale = ContentScale.Fit,
            backgroundColor = Color.Black,
            modifier = Modifier
                .fillMaxSize()
                .let { if (onViewFullSize == null) it else it.clickable(onClick = onViewFullSize) },
        )
        if (onViewFullSize != null) {
            FilledIconButton(
                onClick = onViewFullSize,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(12.dp)
                    .testTag(MEDIA_DETAILS_ZOOM_TEST_TAG),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = Color.Black.copy(alpha = 0.5f),
                    contentColor = Color.White,
                ),
            ) {
                Icon(Icons.Outlined.ZoomIn, contentDescription = stringResource(R.string.media_details_view_full_size))
            }
        }
    }
}

@Composable
private fun PlayerHero(media: NasaMedia, playback: PlaybackState, onRetry: () -> Unit) {
    val heroModifier = Modifier
        .fillMaxWidth()
        .aspectRatio(PLAYER_ASPECT_RATIO)

    if (playback is PlaybackState.Ready) {
        NasaMediaPlayer(
            streamUrl = playback.url,
            posterUrl = media.imageUrl,
            audioOnly = media.mediaType == NasaMediaType.AUDIO,
            modifier = heroModifier,
        )
        return
    }

    Box(modifier = heroModifier.background(Color.Black), contentAlignment = Alignment.Center) {
        media.imageUrl?.let { poster ->
            NasaAsyncImage(
                url = poster,
                contentDescription = null,
                backgroundColor = Color.Black,
                modifier = Modifier.fillMaxSize(),
            )
        }
        when (playback) {
            PlaybackState.Idle, PlaybackState.Resolving -> CircularProgressIndicator()

            PlaybackState.NeedsConnection -> PlaybackMessage(
                text = stringResource(R.string.media_details_playback_offline),
                onRetry = onRetry,
            )

            is PlaybackState.Failed -> PlaybackMessage(
                text = stringResource(R.string.media_details_playback_failed),
                onRetry = onRetry.takeIf { playback.error.isRetryable },
            )

            is PlaybackState.Ready -> Unit
        }
    }
}

@Composable
private fun PlaybackMessage(text: String, onRetry: (() -> Unit)?) {
    Column(
        modifier = Modifier
            .background(Color.Black.copy(alpha = 0.6f), MaterialTheme.shapes.medium)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = text, color = Color.White, textAlign = TextAlign.Center)
        if (onRetry != null) {
            TextButton(onClick = onRetry) { Text(stringResource(R.string.media_details_playback_retry)) }
        }
    }
}

/**
 * Library descriptions run from one line to full podcast transcripts, so a long one starts
 * collapsed (and laid out from its opening only, which keeps the screen quick to open).
 */
@Composable
private fun Description(text: String) {
    var isExpanded by rememberSaveable { mutableStateOf(false) }
    val isLong = text.length > COLLAPSED_DESCRIPTION_CHARS

    Text(
        text = if (isLong && !isExpanded) text.take(COLLAPSED_DESCRIPTION_CHARS).trimEnd() + "…" else text,
        style = MaterialTheme.typography.bodyLarge,
    )
    if (isLong) {
        TextButton(onClick = { isExpanded = !isExpanded }) {
            Text(
                stringResource(
                    if (isExpanded) R.string.media_details_read_less else R.string.media_details_read_more,
                ),
            )
        }
    }
}

private const val HERO_ASPECT_RATIO = 4f / 3f
private const val PLAYER_ASPECT_RATIO = 16f / 9f
private const val COLLAPSED_DESCRIPTION_CHARS = 600

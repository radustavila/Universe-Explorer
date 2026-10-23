package com.radustavila.nasaexplorer.feature.news.podcast.episode

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.radustavila.nasaexplorer.core.designsystem.adaptive.ReadableMaxWidth
import com.radustavila.nasaexplorer.core.designsystem.component.EmptyState
import com.radustavila.nasaexplorer.core.designsystem.component.FavoriteButton
import com.radustavila.nasaexplorer.core.designsystem.component.LoadingState
import com.radustavila.nasaexplorer.core.designsystem.error.asText
import com.radustavila.nasaexplorer.core.designsystem.podcast.nameRes
import com.radustavila.nasaexplorer.core.model.PodcastDownloadState
import com.radustavila.nasaexplorer.core.model.PodcastEpisode
import com.radustavila.nasaexplorer.feature.news.R
import com.radustavila.nasaexplorer.feature.news.openInCustomTab
import com.radustavila.nasaexplorer.feature.news.podcast.EpisodeStatus
import com.radustavila.nasaexplorer.feature.news.podcast.PodcastArtwork
import com.radustavila.nasaexplorer.feature.news.podcast.ShowAndAge
import com.radustavila.nasaexplorer.feature.news.podcast.player.playbackErrorRes

const val PODCAST_EPISODE_CONTENT_TEST_TAG = "podcast-episode-content"
const val PODCAST_EPISODE_PLAY_TEST_TAG = "podcast-episode-play"
const val PODCAST_EPISODE_DOWNLOAD_TEST_TAG = "podcast-episode-download"

@Composable
fun PodcastEpisodeScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PodcastEpisodeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val toolbarColor = MaterialTheme.colorScheme.surface.toArgb()
    PodcastEpisodeContent(
        uiState = uiState,
        onBackClick = onBackClick,
        onPlayClick = viewModel::onPlayClick,
        onToggleDownload = viewModel::onToggleDownload,
        onToggleFavorite = viewModel::onToggleFavorite,
        onOpenPage = { url -> context.openInCustomTab(url, toolbarColor) },
        onErrorDismissed = viewModel::onErrorDismissed,
        onPlaybackErrorDismissed = viewModel::onPlaybackErrorDismissed,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PodcastEpisodeContent(
    uiState: PodcastEpisodeUiState,
    onBackClick: () -> Unit,
    onPlayClick: () -> Unit,
    onToggleDownload: () -> Unit,
    onToggleFavorite: () -> Unit,
    onOpenPage: (String) -> Unit,
    onErrorDismissed: () -> Unit,
    onPlaybackErrorDismissed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val errorMessage = (uiState as? PodcastEpisodeUiState.Content)?.error?.asText()
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
                            contentDescription = stringResource(R.string.news_back),
                        )
                    }
                },
                actions = {
                    if (uiState is PodcastEpisodeUiState.Content) {
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
            contentAlignment = Alignment.TopCenter,
        ) {
            when (uiState) {
                PodcastEpisodeUiState.Loading -> LoadingState()

                PodcastEpisodeUiState.Unavailable -> EmptyState(
                    title = stringResource(R.string.podcast_missing_title),
                    description = stringResource(R.string.podcast_missing_description),
                )

                is PodcastEpisodeUiState.Content -> EpisodeDetails(
                    state = uiState,
                    onPlayClick = onPlayClick,
                    onToggleDownload = onToggleDownload,
                    onOpenPage = onOpenPage,
                    onPlaybackErrorDismissed = onPlaybackErrorDismissed,
                )
            }
        }
    }
}

@Composable
private fun EpisodeDetails(
    state: PodcastEpisodeUiState.Content,
    onPlayClick: () -> Unit,
    onToggleDownload: () -> Unit,
    onOpenPage: (String) -> Unit,
    onPlaybackErrorDismissed: () -> Unit,
) {
    val episode = state.episode
    Column(
        modifier = Modifier
            .widthIn(max = ReadableMaxWidth)
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .testTag(PODCAST_EPISODE_CONTENT_TEST_TAG),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            PodcastArtwork(url = episode.imageUrl, size = ARTWORK_SIZE)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                ShowAndAge(episode, state.now, maxLines = 2)
                EpisodeStatus(episode, position = state.playback.position.takeIf { state.isLoaded })
            }
        }
        Text(text = episode.title, style = MaterialTheme.typography.headlineSmall)
        EpisodeActions(state, onPlayClick, onToggleDownload)
        state.playback.error?.takeIf { !state.isLoaded || state.playback.episode == null }?.let { error ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(error.playbackErrorRes),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onPlaybackErrorDismissed) { Text(stringResource(R.string.podcast_error_dismiss)) }
            }
        }
        episode.summary?.let { summary ->
            Text(text = summary, style = MaterialTheme.typography.bodyLarge)
        }
        episode.pageUrl?.let { url ->
            TextButton(
                onClick = { onOpenPage(url) },
                contentPadding = ButtonDefaults.TextButtonWithIconContentPadding,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
                    contentDescription = null,
                    modifier = Modifier.padding(end = ButtonDefaults.IconSpacing),
                )
                Text(stringResource(R.string.podcast_open_page))
            }
        }
        Text(
            text = stringResource(R.string.podcast_credit, stringResource(episode.show.nameRes)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 16.dp),
        )
    }
}

@Composable
private fun EpisodeActions(
    state: PodcastEpisodeUiState.Content,
    onPlayClick: () -> Unit,
    onToggleDownload: () -> Unit,
) {
    val episode = state.episode
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Button(onClick = onPlayClick, modifier = Modifier.testTag(PODCAST_EPISODE_PLAY_TEST_TAG)) {
            Icon(
                imageVector = if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = null,
                modifier = Modifier.padding(end = ButtonDefaults.IconSpacing),
            )
            Text(stringResource(playLabel(state, episode)))
        }
        OutlinedButton(onClick = onToggleDownload, modifier = Modifier.testTag(PODCAST_EPISODE_DOWNLOAD_TEST_TAG)) {
            val (icon, label) = when (episode.download?.state) {
                null -> Icons.Outlined.Download to R.string.podcast_download
                PodcastDownloadState.QUEUED, PodcastDownloadState.DOWNLOADING ->
                    Icons.Outlined.Close to R.string.podcast_cancel_download
                PodcastDownloadState.DOWNLOADED -> Icons.Outlined.Delete to R.string.podcast_remove_download
                PodcastDownloadState.FAILED -> Icons.Outlined.Refresh to R.string.podcast_retry_download
            }
            Icon(icon, contentDescription = null, modifier = Modifier.padding(end = ButtonDefaults.IconSpacing))
            Text(stringResource(label))
        }
    }
}

private fun playLabel(state: PodcastEpisodeUiState.Content, episode: PodcastEpisode): Int = when {
    state.isPlaying -> R.string.podcast_pause
    state.isLoaded || episode.progress?.let { !it.isFinished && !it.position.isZero } == true -> R.string.podcast_resume
    else -> R.string.podcast_play
}

private val ARTWORK_SIZE = 120.dp

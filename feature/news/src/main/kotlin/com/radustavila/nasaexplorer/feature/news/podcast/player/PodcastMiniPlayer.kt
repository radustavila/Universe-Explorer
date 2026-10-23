package com.radustavila.nasaexplorer.feature.news.podcast.player

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.radustavila.nasaexplorer.core.designsystem.podcast.nameRes
import com.radustavila.nasaexplorer.core.model.PodcastEpisode
import com.radustavila.nasaexplorer.core.model.PodcastPlayback
import com.radustavila.nasaexplorer.core.model.PodcastPlaybackError
import com.radustavila.nasaexplorer.feature.news.R
import com.radustavila.nasaexplorer.feature.news.podcast.PodcastArtwork

const val PODCAST_MINI_PLAYER_TEST_TAG = "podcast-mini-player"
const val PODCAST_MINI_PLAYER_PLAY_TEST_TAG = "podcast-mini-player-play"
const val PODCAST_PLAYBACK_ERROR_TEST_TAG = "podcast-playback-error"

/**
 * The podcast playing, as a bar `:app` shows above the navigation bar while an episode is loaded,
 * and why one could not play. Tapping it opens the full player.
 */
@Composable
fun PodcastMiniPlayer(
    modifier: Modifier = Modifier,
    viewModel: PodcastPlayerViewModel = hiltViewModel(),
) {
    val playback by viewModel.playback.collectAsStateWithLifecycle()
    var isSheetOpen by rememberSaveable { mutableStateOf(false) }

    MiniPlayerContent(
        playback = playback,
        onOpen = { isSheetOpen = true },
        onPlayPause = viewModel::onPlayPause,
        onClose = viewModel::onClose,
        onErrorDismissed = viewModel::onErrorDismissed,
        modifier = modifier,
    )
    if (isSheetOpen && playback.episode != null) {
        PodcastPlayerSheet(
            playback = playback,
            onDismiss = { isSheetOpen = false },
            onPlayPause = viewModel::onPlayPause,
            onSkipBack = viewModel::onSkipBack,
            onSkipForward = viewModel::onSkipForward,
            onSeek = viewModel::onSeek,
            onSpeedSelected = viewModel::onSpeedSelected,
        )
    }
}

@Composable
internal fun MiniPlayerContent(
    playback: PodcastPlayback,
    onOpen: () -> Unit,
    onPlayPause: () -> Unit,
    onClose: () -> Unit,
    onErrorDismissed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val episode = playback.episode
    AnimatedVisibility(
        visible = episode != null || playback.error != null,
        enter = expandVertically(),
        exit = shrinkVertically(),
        modifier = modifier,
    ) {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, tonalElevation = 3.dp) {
            Column(modifier = Modifier.fillMaxWidth()) {
                playback.error?.let { error -> PlaybackErrorLine(error, onErrorDismissed) }
                if (episode != null) {
                    playback.fraction()?.let { fraction ->
                        LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
                    }
                    NowPlayingRow(episode, playback, onOpen, onPlayPause, onClose)
                }
            }
        }
    }
}

@Composable
private fun PlaybackErrorLine(error: PodcastPlaybackError, onDismiss: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 8.dp)
            .testTag(PODCAST_PLAYBACK_ERROR_TEST_TAG),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = Icons.Outlined.ErrorOutline,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = stringResource(error.playbackErrorRes),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 8.dp),
        )
        TextButton(onClick = onDismiss) {
            Text(stringResource(R.string.podcast_error_dismiss))
        }
    }
}

@Composable
private fun NowPlayingRow(
    episode: PodcastEpisode,
    playback: PodcastPlayback,
    onOpen: () -> Unit,
    onPlayPause: () -> Unit,
    onClose: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                role = Role.Button,
                onClickLabel = stringResource(R.string.podcast_player_open),
                onClick = onOpen,
            )
            .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp)
            .testTag(PODCAST_MINI_PLAYER_TEST_TAG),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PodcastArtwork(url = episode.imageUrl, size = 44.dp)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = episode.title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(episode.show.nameRes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        PlayPauseButton(playback, onPlayPause, Modifier.testTag(PODCAST_MINI_PLAYER_PLAY_TEST_TAG))
        IconButton(onClick = onClose) {
            Icon(
                imageVector = Icons.Outlined.Close,
                contentDescription = stringResource(R.string.podcast_player_close),
            )
        }
    }
}

/** Play or pause, with a spinner around it while the stream buffers. */
@Composable
internal fun PlayPauseButton(playback: PodcastPlayback, onPlayPause: () -> Unit, modifier: Modifier = Modifier) {
    Box(contentAlignment = Alignment.Center) {
        if (playback.isPlaying && playback.isBuffering) {
            CircularProgressIndicator(modifier = Modifier.size(40.dp), strokeWidth = 2.dp)
        }
        IconButton(onClick = onPlayPause, modifier = modifier) {
            Icon(
                imageVector = if (playback.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = stringResource(playback.playPauseLabel),
            )
        }
    }
}

@get:StringRes
internal val PodcastPlayback.playPauseLabel: Int
    get() = if (isPlaying) R.string.podcast_pause else R.string.podcast_play

/** How far into the episode the player is, once its length is known. */
internal fun PodcastPlayback.fraction(): Float? {
    val length = duration?.toMillis()?.takeIf { it > 0 } ?: return null
    return (position.toMillis().toFloat() / length).coerceIn(0f, 1f)
}

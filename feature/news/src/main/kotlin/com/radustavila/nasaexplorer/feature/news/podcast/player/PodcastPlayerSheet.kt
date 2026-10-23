package com.radustavila.nasaexplorer.feature.news.podcast.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Forward30
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.radustavila.nasaexplorer.core.designsystem.component.NasaAsyncImage
import com.radustavila.nasaexplorer.core.designsystem.podcast.nameRes
import com.radustavila.nasaexplorer.core.domain.podcast.PodcastPlayer
import com.radustavila.nasaexplorer.core.model.PodcastPlayback
import com.radustavila.nasaexplorer.feature.news.R
import com.radustavila.nasaexplorer.feature.news.podcast.formatPlaybackTime
import java.time.Duration
import java.util.Locale

const val PODCAST_PLAYER_SHEET_TEST_TAG = "podcast-player-sheet"
const val PODCAST_PLAYER_SKIP_BACK_TEST_TAG = "podcast-player-skip-back"
const val PODCAST_PLAYER_SKIP_FORWARD_TEST_TAG = "podcast-player-skip-forward"

fun podcastSpeedTestTag(speed: Float) = "podcast-speed-$speed"

/** The full player: artwork, position, skip back 15 s and forward 30 s, and the playback speed. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PodcastPlayerSheet(
    playback: PodcastPlayback,
    onDismiss: () -> Unit,
    onPlayPause: () -> Unit,
    onSkipBack: () -> Unit,
    onSkipForward: () -> Unit,
    onSeek: (Duration) -> Unit,
    onSpeedSelected: (Float) -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        PlayerSheetContent(playback, onPlayPause, onSkipBack, onSkipForward, onSeek, onSpeedSelected)
    }
}

@Composable
internal fun PlayerSheetContent(
    playback: PodcastPlayback,
    onPlayPause: () -> Unit,
    onSkipBack: () -> Unit,
    onSkipForward: () -> Unit,
    onSeek: (Duration) -> Unit,
    onSpeedSelected: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val episode = playback.episode ?: return
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .padding(bottom = 24.dp)
            .testTag(PODCAST_PLAYER_SHEET_TEST_TAG),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        NasaAsyncImage(
            url = episode.imageUrl,
            contentDescription = null,
            modifier = Modifier
                .widthIn(max = ARTWORK_MAX_WIDTH)
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(MaterialTheme.shapes.large),
        )
        Text(
            text = stringResource(episode.show.nameRes),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
        )
        Text(
            text = episode.title,
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )
        SeekBar(playback, onSeek)
        Controls(playback, onPlayPause, onSkipBack, onSkipForward)
        SpeedChooser(selected = playback.speed, onSpeedSelected = onSpeedSelected)
    }
}

@Composable
private fun SeekBar(playback: PodcastPlayback, onSeek: (Duration) -> Unit) {
    val length = playback.duration?.takeIf { !it.isZero && !it.isNegative }
    // While the thumb is dragged it follows the finger; the player seeks once it is let go.
    var dragged by remember { mutableStateOf<Float?>(null) }
    val position = dragged?.let { fraction -> length?.let { Duration.ofMillis((it.toMillis() * fraction).toLong()) } }
        ?: playback.position
    val seekLabel = stringResource(R.string.podcast_player_seek)
    Column(modifier = Modifier.fillMaxWidth()) {
        Slider(
            value = dragged ?: playback.fraction() ?: 0f,
            onValueChange = { dragged = it },
            onValueChangeFinished = {
                val fraction = dragged
                if (fraction != null && length != null) {
                    onSeek(Duration.ofMillis((length.toMillis() * fraction).toLong()))
                }
                dragged = null
            },
            enabled = length != null,
            modifier = Modifier.semantics { contentDescription = seekLabel },
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = position.formatPlaybackTime(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (length != null) {
                Text(
                    text = stringResource(
                        R.string.podcast_player_remaining,
                        length.minus(position).coerceAtLeastZero().formatPlaybackTime(),
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun Controls(
    playback: PodcastPlayback,
    onPlayPause: () -> Unit,
    onSkipBack: () -> Unit,
    onSkipForward: () -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = onSkipBack,
            modifier = Modifier
                .size(56.dp)
                .testTag(PODCAST_PLAYER_SKIP_BACK_TEST_TAG),
        ) {
            // Material has no "replay 15" icon: the replay arrow with the seconds in it.
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Filled.Replay,
                    contentDescription = stringResource(R.string.podcast_player_skip_back),
                    modifier = Modifier.size(36.dp),
                )
                Text(
                    text = PodcastPlayer.SKIP_BACK.seconds.toString(),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        FilledIconButton(onClick = onPlayPause, modifier = Modifier.size(72.dp)) {
            Icon(
                imageVector = if (playback.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = stringResource(playback.playPauseLabel),
                modifier = Modifier.size(40.dp),
            )
        }
        IconButton(
            onClick = onSkipForward,
            modifier = Modifier
                .size(56.dp)
                .testTag(PODCAST_PLAYER_SKIP_FORWARD_TEST_TAG),
        ) {
            Icon(
                imageVector = Icons.Filled.Forward30,
                contentDescription = stringResource(R.string.podcast_player_skip_forward),
                modifier = Modifier.size(36.dp),
            )
        }
    }
}

@Composable
private fun SpeedChooser(selected: Float, onSpeedSelected: (Float) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = stringResource(R.string.podcast_player_speed),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
            PodcastPlayer.SPEEDS.forEach { speed ->
                FilterChip(
                    selected = speed == selected,
                    onClick = { onSpeedSelected(speed) },
                    label = { Text(stringResource(R.string.podcast_player_speed_value, speed.formatSpeed())) },
                    modifier = Modifier.testTag(podcastSpeedTestTag(speed)),
                )
            }
        }
    }
}

/** `1`, `1.5`, `0.8`: the speed as people say it, in the user's number format. */
private fun Float.formatSpeed(): String =
    if (this % 1f == 0f) "%d".format(Locale.getDefault(), toInt()) else "%.1f".format(Locale.getDefault(), this)

private fun Duration.coerceAtLeastZero(): Duration = if (isNegative) Duration.ZERO else this

private val ARTWORK_MAX_WIDTH = 280.dp

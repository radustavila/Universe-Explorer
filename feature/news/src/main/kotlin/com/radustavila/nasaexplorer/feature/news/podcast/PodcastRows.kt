package com.radustavila.nasaexplorer.feature.news.podcast

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.DownloadDone
import androidx.compose.material.icons.outlined.Downloading
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Podcasts
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.radustavila.nasaexplorer.core.designsystem.component.NasaAsyncImage
import com.radustavila.nasaexplorer.core.designsystem.podcast.nameRes
import com.radustavila.nasaexplorer.core.model.PodcastDownloadState
import com.radustavila.nasaexplorer.core.model.PodcastEpisode
import com.radustavila.nasaexplorer.feature.news.R
import com.radustavila.nasaexplorer.feature.news.formatAge
import java.time.Duration
import java.time.Instant
import java.util.Locale

fun podcastRowTestTag(id: String) = "podcast-row-$id"

fun podcastPlayButtonTestTag(id: String) = "podcast-play-$id"

/**
 * One episode in a list: artwork, show and age, title, then its length (or the time left and how
 * far the user got) and whether it is downloaded; a play button at the end. [isPlaying] is this
 * episode playing right now, [position] where the player is in it (else the saved position).
 */
@Composable
internal fun PodcastEpisodeRow(
    episode: PodcastEpisode,
    now: Instant,
    isPlaying: Boolean,
    position: Duration?,
    onClick: () -> Unit,
    onPlayClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .testTag(podcastRowTestTag(episode.id)),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PodcastArtwork(url = episode.imageUrl, size = ROW_ARTWORK_SIZE)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            ShowAndAge(episode, now)
            Text(
                text = episode.title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            EpisodeStatus(episode, position)
        }
        FilledTonalIconButton(
            onClick = onPlayClick,
            modifier = Modifier.testTag(podcastPlayButtonTestTag(episode.id)),
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = stringResource(if (isPlaying) R.string.podcast_pause else R.string.podcast_play),
            )
        }
    }
}

@Composable
internal fun ShowAndAge(episode: PodcastEpisode, now: Instant, modifier: Modifier = Modifier) {
    Text(
        text = stringResource(
            R.string.podcast_show_and_age,
            stringResource(episode.show.nameRes),
            episode.publishedAt.formatAge(now),
        ),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

/** "61 min", "12 min left" with a progress bar, or "Played"; then the download's state. */
@Composable
internal fun EpisodeStatus(episode: PodcastEpisode, position: Duration?, modifier: Modifier = Modifier) {
    val duration = episode.duration
    val listened = position ?: episode.progress?.position?.takeIf { episode.progress?.isFinished == false }
    val fraction = if (duration != null && listened != null && !duration.isZero) {
        (listened.toMillis().toFloat() / duration.toMillis()).coerceIn(0f, 1f)
    } else {
        null
    }
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val length = when {
            episode.progress?.isFinished == true && position == null -> stringResource(R.string.podcast_played)
            duration != null && fraction != null && fraction > 0f -> {
                val left = duration.minus(listened ?: Duration.ZERO).toMinutesRoundedUp()
                pluralStringResource(R.plurals.podcast_minutes_left, left, left)
            }

            duration != null -> {
                val minutes = duration.toMinutesRoundedUp()
                pluralStringResource(R.plurals.podcast_minutes, minutes, minutes)
            }

            else -> null
        }
        if (fraction != null && fraction > 0f) {
            LinearProgressIndicator(progress = { fraction }, modifier = Modifier.size(width = 48.dp, height = 4.dp))
        }
        length?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        DownloadMark(episode.download?.state)
    }
}

@Composable
private fun DownloadMark(state: PodcastDownloadState?) {
    val (icon, label) = when (state) {
        null -> return
        PodcastDownloadState.DOWNLOADED -> Icons.Outlined.DownloadDone to R.string.podcast_downloaded
        PodcastDownloadState.QUEUED, PodcastDownloadState.DOWNLOADING ->
            Icons.Outlined.Downloading to R.string.podcast_downloading
        PodcastDownloadState.FAILED -> Icons.Outlined.ErrorOutline to R.string.podcast_download_failed
    }
    Icon(
        imageVector = icon,
        contentDescription = stringResource(label),
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(16.dp),
    )
}

@Composable
internal fun PodcastArtwork(url: String?, size: Dp, modifier: Modifier = Modifier) {
    NasaAsyncImage(
        url = url,
        contentDescription = null,
        fallbackIcon = Icons.Outlined.Podcasts,
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(8.dp)),
    )
}

/** `12:04` or `1:02:15`: a position in an episode, not a time of day. */
internal fun Duration.formatPlaybackTime(): String {
    val total = seconds.coerceAtLeast(0)
    val hours = total / SECONDS_PER_HOUR
    val minutes = total % SECONDS_PER_HOUR / SECONDS_PER_MINUTE
    val secs = total % SECONDS_PER_MINUTE
    return if (hours > 0) {
        "%d:%02d:%02d".format(Locale.ROOT, hours, minutes, secs)
    } else {
        "%d:%02d".format(Locale.ROOT, minutes, secs)
    }
}

private fun Duration.toMinutesRoundedUp(): Int =
    ((seconds.coerceAtLeast(0) + SECONDS_PER_MINUTE - 1) / SECONDS_PER_MINUTE).toInt().coerceAtLeast(1)

private val ROW_ARTWORK_SIZE = 64.dp
private const val SECONDS_PER_MINUTE = 60L
private const val SECONDS_PER_HOUR = 3_600L

package com.radustavila.nasaexplorer.feature.news.podcast.player

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import com.radustavila.nasaexplorer.core.domain.podcast.PodcastPlayer
import com.radustavila.nasaexplorer.core.model.PodcastPlayback
import com.radustavila.nasaexplorer.core.model.PodcastPlaybackError
import com.radustavila.nasaexplorer.feature.news.R
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Duration
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

/** The mini player and the full player sheet: the app's one [PodcastPlayer], as it plays. */
@HiltViewModel
class PodcastPlayerViewModel @Inject constructor(
    private val player: PodcastPlayer,
) : ViewModel() {

    val playback: StateFlow<PodcastPlayback> = player.playback

    fun onPlayPause() {
        val current = playback.value
        val episode = current.episode
        when {
            current.isPlaying -> player.pause()
            episode != null -> player.play(episode)
        }
    }

    fun onSkipBack() = player.seekBy(PodcastPlayer.SKIP_BACK.negated())

    fun onSkipForward() = player.seekBy(PodcastPlayer.SKIP_FORWARD)

    fun onSeek(position: Duration) = player.seekTo(position)

    fun onSpeedSelected(speed: Float) = player.setSpeed(speed)

    fun onClose() = player.stop()

    fun onErrorDismissed() = player.clearError()
}

@get:StringRes
internal val PodcastPlaybackError.playbackErrorRes: Int
    get() = when (this) {
        PodcastPlaybackError.OFFLINE_NOT_DOWNLOADED -> R.string.podcast_error_offline
        PodcastPlaybackError.CANNOT_PLAY -> R.string.podcast_error_cannot_play
    }

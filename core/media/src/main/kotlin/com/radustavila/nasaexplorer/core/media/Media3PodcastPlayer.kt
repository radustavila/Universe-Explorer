package com.radustavila.nasaexplorer.core.media

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.annotation.MainThread
import androidx.annotation.OptIn
import androidx.core.net.toUri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.radustavila.nasaexplorer.core.common.dispatchers.ApplicationScope
import com.radustavila.nasaexplorer.core.designsystem.podcast.nameRes
import com.radustavila.nasaexplorer.core.domain.connectivity.NetworkMonitor
import com.radustavila.nasaexplorer.core.domain.podcast.PodcastPlayer
import com.radustavila.nasaexplorer.core.domain.repository.PodcastRepository
import com.radustavila.nasaexplorer.core.model.PodcastEpisode
import com.radustavila.nasaexplorer.core.model.PodcastPlayback
import com.radustavila.nasaexplorer.core.model.PodcastPlaybackError
import com.radustavila.nasaexplorer.core.model.PodcastProgress
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * [PodcastPlayer] on one Media3 [ExoPlayer] for the whole process. [PodcastPlaybackService] wraps
 * the same player in a media session, which gives it the media notification, lock-screen and
 * headset controls and keeps the process in the foreground while it plays.
 *
 * The player handles audio focus (pauses for a call, ducks under a navigation prompt) and pauses
 * when headphones are unplugged. Positions are saved per episode while playing, on pause and at
 * the end, so an episode resumes where the user left it, also after the app was closed.
 */
// The contract's controls plus the player's own wiring (listener, position ticker, saving).
@Suppress("TooManyFunctions")
@Singleton
class Media3PodcastPlayer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: PodcastRepository,
    private val networkMonitor: NetworkMonitor,
    @ApplicationScope private val applicationScope: CoroutineScope,
) : PodcastPlayer {

    // The player lives on the main thread; saving positions outlives any screen.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val state = MutableStateFlow(PodcastPlayback())
    override val playback: StateFlow<PodcastPlayback> = state.asStateFlow()

    private var exoPlayer: ExoPlayer? = null
    private var loadJob: Job? = null
    private var tickerJob: Job? = null

    /** The player the media session wraps; created on first use. */
    @MainThread
    fun sessionPlayer(): Player = player()

    override fun play(episode: PodcastEpisode) {
        val loaded = exoPlayer?.takeIf { state.value.episode?.id == episode.id && it.mediaItemCount > 0 }
        if (loaded != null) {
            if (loaded.playbackState == Player.STATE_ENDED) loaded.seekTo(0)
            resume()
            return
        }
        loadJob?.cancel()
        loadJob = scope.launch {
            val localPath = repository.downloadedAudioPath(episode.id)
            if (localPath == null && !networkMonitor.isOnline.first()) {
                state.update { it.copy(error = PodcastPlaybackError.OFFLINE_NOT_DOWNLOADED) }
                return@launch
            }
            saveCurrentProgress()
            val start = resumePosition(episode.progress, episode.duration)
            state.update {
                PodcastPlayback(
                    episode = episode,
                    isPlaying = true,
                    isBuffering = true,
                    position = start,
                    duration = episode.duration,
                    speed = it.speed,
                )
            }
            startService()
            player().apply {
                setMediaItem(mediaItemOf(episode, localPath), start.toMillis())
                prepare()
                play()
            }
        }
    }

    override fun pause() {
        exoPlayer?.pause()
    }

    override fun resume() {
        val player = exoPlayer ?: return
        // The system stops the service some time after playback paused; this brings the session back.
        startService()
        if (player.playbackState == Player.STATE_IDLE) player.prepare()
        player.play()
    }

    override fun seekTo(position: Duration) {
        val player = exoPlayer ?: return
        player.seekTo(position.toMillis().coerceAtLeast(0))
        syncState(player)
    }

    override fun seekBy(offset: Duration) {
        val player = exoPlayer ?: return
        val target = (player.currentPosition + offset.toMillis()).coerceAtLeast(0)
        val length = player.duration.takeIf { it != C.TIME_UNSET }
        player.seekTo(if (length != null) target.coerceAtMost(length) else target)
        syncState(player)
    }

    override fun setSpeed(speed: Float) {
        exoPlayer?.setPlaybackSpeed(speed)
        state.update { it.copy(speed = speed) }
    }

    override fun stop() {
        loadJob?.cancel()
        saveCurrentProgress()
        exoPlayer?.run {
            stop()
            clearMediaItems()
        }
        state.update { PodcastPlayback(speed = it.speed) }
        context.stopService(Intent(context, PodcastPlaybackService::class.java))
    }

    override fun clearError() {
        state.update { it.copy(error = null) }
    }

    private fun player(): ExoPlayer = exoPlayer ?: buildPlayer().also { exoPlayer = it }

    @OptIn(UnstableApi::class)
    private fun buildPlayer(): ExoPlayer {
        // Curious Universe's enclosures go through a tracking redirect before Megaphone's CDN.
        val http = DefaultHttpDataSource.Factory().setAllowCrossProtocolRedirects(true)
        return ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(DefaultDataSource.Factory(context, http)))
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                    .build(),
                // handleAudioFocus
                true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .setSeekBackIncrementMs(PodcastPlayer.SKIP_BACK.toMillis())
            .setSeekForwardIncrementMs(PodcastPlayer.SKIP_FORWARD.toMillis())
            .build()
            .apply { addListener(listener) }
    }

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            syncState(player)
            if (events.contains(Player.EVENT_IS_PLAYING_CHANGED)) {
                if (player.isPlaying) startTicker(player) else stopTicker(save = true)
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) {
                val episode = state.value.episode ?: return
                save(episode.id, PodcastProgress(Duration.ZERO, isFinished = true))
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            saveCurrentProgress()
            state.update { it.copy(isPlaying = false, isBuffering = false, error = PodcastPlaybackError.CANNOT_PLAY) }
        }
    }

    private fun syncState(player: Player) {
        val ended = player.playbackState == Player.STATE_ENDED
        state.update { current ->
            if (current.episode == null) return@update current
            current.copy(
                isPlaying = player.playWhenReady && !ended && player.playbackState != Player.STATE_IDLE,
                isBuffering = player.playbackState == Player.STATE_BUFFERING,
                position = if (ended) Duration.ZERO else Duration.ofMillis(player.currentPosition.coerceAtLeast(0)),
                duration = player.duration.takeIf { it != C.TIME_UNSET && it > 0 }?.let(Duration::ofMillis)
                    ?: current.duration,
                speed = player.playbackParameters.speed,
            )
        }
    }

    /** Moves the position along once a second, and saves it every [SAVE_INTERVAL]. */
    private fun startTicker(player: Player) {
        tickerJob?.cancel()
        tickerJob = scope.launch {
            var sinceSave = Duration.ZERO
            while (isActive) {
                delay(TICK.toMillis())
                syncState(player)
                sinceSave += TICK
                if (sinceSave >= SAVE_INTERVAL) {
                    saveCurrentProgress()
                    sinceSave = Duration.ZERO
                }
            }
        }
    }

    private fun stopTicker(save: Boolean) {
        tickerJob?.cancel()
        tickerJob = null
        if (save) saveCurrentProgress()
    }

    private fun saveCurrentProgress() {
        val player = exoPlayer?.takeIf { it.mediaItemCount > 0 && it.playbackState != Player.STATE_ENDED }
        val episode = state.value.episode
        if (player != null && episode != null) {
            save(episode.id, PodcastProgress(Duration.ofMillis(player.currentPosition.coerceAtLeast(0))))
        }
    }

    private fun save(episodeId: String, progress: PodcastProgress) {
        applicationScope.launch { repository.saveProgress(episodeId, progress) }
    }

    private fun startService() {
        try {
            context.startService(Intent(context, PodcastPlaybackService::class.java))
        } catch (_: IllegalStateException) {
            // Not allowed from the background; the session then starts when a controller binds.
        }
    }

    private fun mediaItemOf(episode: PodcastEpisode, localPath: String?): MediaItem = MediaItem.Builder()
        .setMediaId(episode.id)
        .setUri(localPath?.let { Uri.fromFile(File(it)) } ?: episode.audioUrl.toUri())
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(episode.title)
                .setArtist(context.getString(episode.show.nameRes))
                .setAlbumTitle(context.getString(episode.show.nameRes))
                .setArtworkUri(episode.imageUrl?.toUri())
                .setMediaType(MediaMetadata.MEDIA_TYPE_PODCAST_EPISODE)
                .build(),
        )
        .build()

    internal companion object {
        val TICK: Duration = Duration.ofSeconds(1)
        val SAVE_INTERVAL: Duration = Duration.ofSeconds(15)

        /** An episode within this of its end starts over rather than resuming for a moment. */
        val NEAR_THE_END: Duration = Duration.ofSeconds(10)

        /** Where an episode starts: where the user left it, unless they finished it or nearly did. */
        fun resumePosition(progress: PodcastProgress?, duration: Duration?): Duration = when {
            progress == null || progress.isFinished -> Duration.ZERO
            duration != null && progress.position >= duration - NEAR_THE_END -> Duration.ZERO
            else -> progress.position
        }
    }
}

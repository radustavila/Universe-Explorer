package com.radustavila.nasaexplorer.core.testing.fake

import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.domain.podcast.PodcastPlayer
import com.radustavila.nasaexplorer.core.domain.repository.PodcastRepository
import com.radustavila.nasaexplorer.core.model.PodcastDownload
import com.radustavila.nasaexplorer.core.model.PodcastDownloadState
import com.radustavila.nasaexplorer.core.model.PodcastEpisode
import com.radustavila.nasaexplorer.core.model.PodcastPlayback
import com.radustavila.nasaexplorer.core.model.PodcastProgress
import com.radustavila.nasaexplorer.core.model.PodcastShow
import java.time.Duration
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * In-memory [PodcastRepository]: the cache is whatever the test seeds, newest first. Downloads
 * complete at once (as [PodcastDownloadState.DOWNLOADED], 1 MB) unless [completeDownloads] is off.
 */
class FakePodcastRepository(initialEpisodes: List<PodcastEpisode> = emptyList()) : PodcastRepository {

    private val episodes = MutableStateFlow(initialEpisodes.sortedByDescending(PodcastEpisode::publishedAt))

    /** What [refresh] answers per show; shows without an entry succeed with 0 episodes. */
    var refreshResults: Map<PodcastShow, DataResult<Int>> = emptyMap()

    /** The shows [refresh] was called for, in order. */
    val refreshedShows = mutableListOf<PodcastShow>()

    var completeDownloads: Boolean = true

    val current: List<PodcastEpisode> get() = episodes.value

    fun setEpisodes(value: List<PodcastEpisode>) {
        episodes.value = value.sortedByDescending(PodcastEpisode::publishedAt)
    }

    override fun observeEpisodes(): Flow<List<PodcastEpisode>> = episodes

    override fun observeEpisode(id: String): Flow<PodcastEpisode?> =
        episodes.map { all -> all.firstOrNull { it.id == id } }

    override suspend fun refresh(show: PodcastShow): DataResult<Int> {
        synchronized(refreshedShows) { refreshedShows += show }
        return refreshResults[show] ?: DataResult.Success(0)
    }

    override suspend fun saveProgress(episodeId: String, progress: PodcastProgress): DataResult<Unit> {
        updateEpisode(episodeId) { it.copy(progress = progress) }
        return DataResult.Success(Unit)
    }

    override suspend fun requestDownload(episodeId: String): DataResult<Unit> {
        val download = if (completeDownloads) {
            PodcastDownload(PodcastDownloadState.DOWNLOADED, bytes = 1_000_000)
        } else {
            PodcastDownload(PodcastDownloadState.QUEUED)
        }
        updateEpisode(episodeId) { it.copy(download = download) }
        return DataResult.Success(Unit)
    }

    override suspend fun removeDownload(episodeId: String): DataResult<Unit> {
        updateEpisode(episodeId) { it.copy(download = null) }
        return DataResult.Success(Unit)
    }

    override suspend fun downloadedAudioPath(episodeId: String): String? =
        episodes.value.firstOrNull { it.id == episodeId }?.takeIf(PodcastEpisode::isDownloaded)
            ?.let { "/data/podcasts/${it.id}.mp3" }

    private fun updateEpisode(id: String, change: (PodcastEpisode) -> PodcastEpisode) {
        episodes.update { all -> all.map { if (it.id == id) change(it) else it } }
    }
}

/** A [PodcastPlayer] that only records what it was asked and moves its state as a real one would. */
class FakePodcastPlayer : PodcastPlayer {

    private val state = MutableStateFlow(PodcastPlayback())
    override val playback: StateFlow<PodcastPlayback> = state.asStateFlow()

    /** Every call, e.g. `play:<id>`, `seekBy:PT30S`, in order. */
    val calls = mutableListOf<String>()

    fun setPlayback(value: PodcastPlayback) {
        state.value = value
    }

    override fun play(episode: PodcastEpisode) {
        calls += "play:${episode.id}"
        state.update {
            it.copy(
                episode = episode,
                isPlaying = true,
                position = episode.progress?.position ?: Duration.ZERO,
                duration = episode.duration,
                error = null,
            )
        }
    }

    override fun pause() {
        calls += "pause"
        state.update { it.copy(isPlaying = false) }
    }

    override fun resume() {
        calls += "resume"
        state.update { it.copy(isPlaying = it.episode != null) }
    }

    override fun seekTo(position: Duration) {
        calls += "seekTo:$position"
        state.update { it.copy(position = position) }
    }

    override fun seekBy(offset: Duration) {
        calls += "seekBy:$offset"
        state.update { it.copy(position = (it.position + offset).coerceAtLeast(Duration.ZERO)) }
    }

    override fun setSpeed(speed: Float) {
        calls += "setSpeed:$speed"
        state.update { it.copy(speed = speed) }
    }

    override fun stop() {
        calls += "stop"
        state.value = PodcastPlayback(speed = state.value.speed)
    }

    override fun clearError() {
        calls += "clearError"
        state.update { it.copy(error = null) }
    }
}

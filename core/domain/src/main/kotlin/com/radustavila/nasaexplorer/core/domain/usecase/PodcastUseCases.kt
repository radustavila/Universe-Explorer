package com.radustavila.nasaexplorer.core.domain.usecase

import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.domain.repository.PodcastRepository
import com.radustavila.nasaexplorer.core.domain.sync.SourceRefreshResult
import com.radustavila.nasaexplorer.core.model.FavoriteSource
import com.radustavila.nasaexplorer.core.model.PodcastDownloadState
import com.radustavila.nasaexplorer.core.model.PodcastEpisode
import com.radustavila.nasaexplorer.core.model.PodcastShow
import com.radustavila.nasaexplorer.core.model.SyncSource
import java.time.Duration
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow

/** The cached episodes of every show, newest first. */
class ObservePodcastEpisodesUseCase @Inject constructor(
    private val repository: PodcastRepository,
) {
    operator fun invoke(): Flow<List<PodcastEpisode>> = repository.observeEpisodes()
}

class ObservePodcastEpisodeUseCase @Inject constructor(
    private val repository: PodcastRepository,
) {
    operator fun invoke(id: String): Flow<PodcastEpisode?> = repository.observeEpisode(id)
}

/**
 * Refreshes every show at once, each through [RefreshSourceUseCase] as its own [SyncSource], at
 * most once per [MIN_REFRESH_INTERVAL] unless [force]d. Like the news (F14) there is no background
 * job: it runs while the Podcasts tab is shown.
 */
class RefreshPodcastsUseCase @Inject constructor(
    private val repository: PodcastRepository,
    private val refreshSource: RefreshSourceUseCase,
) {
    /**
     * @return the first failure when every show that was due failed (e.g. offline), else success,
     * also when no show was due.
     */
    suspend operator fun invoke(force: Boolean = false): DataResult<Unit> = coroutineScope {
        val outcomes = PodcastShow.entries.map { show ->
            async {
                refreshSource(show.syncSource, minInterval = MIN_REFRESH_INTERVAL, force = force) {
                    repository.refresh(show)
                }
            }
        }.awaitAll()
        val results = outcomes.filterIsInstance<SourceRefreshResult.Completed<Int>>().map { it.result }
        val failures = results.filterIsInstance<DataResult.Failure>()
        if (results.isNotEmpty() && failures.size == results.size) failures.first() else DataResult.Success(Unit)
    }

    companion object {
        /** New episodes come out weekly at most; the host asks clients to cache for minutes. */
        val MIN_REFRESH_INTERVAL: Duration = Duration.ofHours(3)

        /** Every source the Podcasts tab is fed by, for its freshness line. */
        val SOURCES: Array<SyncSource> = PodcastShow.entries.map(PodcastShow::syncSource).toTypedArray()
    }
}

/** Saves an episode to Favourites with its artwork as the snapshot; a favourite keeps its download. */
class SavePodcastEpisodeFavoriteUseCase @Inject constructor(
    private val saveFavorite: SaveFavoriteUseCase,
) {
    suspend operator fun invoke(episode: PodcastEpisode): DataResult<Unit> = saveFavorite(
        source = FavoriteSource.NASA_PODCAST_EPISODE,
        itemId = episode.id,
        title = episode.title,
        imageUrl = episode.imageUrl,
    )
}

/**
 * Downloads an episode for offline listening (again, after a failed attempt), or cancels or removes
 * the download.
 */
class TogglePodcastDownloadUseCase @Inject constructor(
    private val repository: PodcastRepository,
) {
    suspend operator fun invoke(episode: PodcastEpisode): DataResult<Unit> =
        if ((episode.download?.state ?: PodcastDownloadState.FAILED) == PodcastDownloadState.FAILED) {
            repository.requestDownload(episode.id)
        } else {
            repository.removeDownload(episode.id)
        }
}

package com.radustavila.nasaexplorer.data.news.repository

import androidx.work.ExistingWorkPolicy
import androidx.work.WorkManager
import com.radustavila.nasaexplorer.core.common.error.AppError
import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.common.result.flatMap
import com.radustavila.nasaexplorer.core.common.result.map
import com.radustavila.nasaexplorer.core.common.time.TimeProvider
import com.radustavila.nasaexplorer.core.domain.repository.FavoritesRepository
import com.radustavila.nasaexplorer.core.domain.repository.PodcastRepository
import com.radustavila.nasaexplorer.core.model.FavoriteSource
import com.radustavila.nasaexplorer.core.model.PodcastDownloadState
import com.radustavila.nasaexplorer.core.model.PodcastEpisode
import com.radustavila.nasaexplorer.core.model.PodcastProgress
import com.radustavila.nasaexplorer.core.model.PodcastShow
import com.radustavila.nasaexplorer.data.news.download.PodcastDownloadWorker
import com.radustavila.nasaexplorer.data.news.download.PodcastFiles
import com.radustavila.nasaexplorer.data.news.local.PodcastDownloadEntity
import com.radustavila.nasaexplorer.data.news.local.PodcastLocalDataSource
import com.radustavila.nasaexplorer.data.news.local.PodcastProgressEntity
import com.radustavila.nasaexplorer.data.news.mapper.toPodcastEpisodeOrNull
import com.radustavila.nasaexplorer.data.news.remote.PodcastRemoteDataSource
import com.radustavila.nasaexplorer.data.news.remote.dto.FeedDocumentDto
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/**
 * NASA's podcasts, offline-first: screens read Room, a refresh replaces one show's episodes in a
 * transaction, and progress and downloads sit in their own tables so no refresh touches them.
 * Downloads run in WorkManager ([PodcastDownloadWorker]), one unique job per episode.
 */
@Singleton
class PodcastRepositoryImpl @Inject constructor(
    private val localDataSource: PodcastLocalDataSource,
    private val remoteDataSource: PodcastRemoteDataSource,
    private val favoritesRepository: FavoritesRepository,
    private val files: PodcastFiles,
    private val workManager: dagger.Lazy<WorkManager>,
    private val timeProvider: TimeProvider,
) : PodcastRepository {

    override fun observeEpisodes(): Flow<List<PodcastEpisode>> = localDataSource.observeEpisodes()

    override fun observeEpisode(id: String): Flow<PodcastEpisode?> = localDataSource.observeEpisode(id)

    override suspend fun refresh(show: PodcastShow): DataResult<Int> =
        remoteDataSource.fetch(show).flatMap { document -> store(show, document) }

    private suspend fun store(show: PodcastShow, document: FeedDocumentDto): DataResult<Int> {
        val episodes = document.entries
            .mapNotNull { it.toPodcastEpisodeOrNull(show, document.imageUrl) }
            .distinctBy(PodcastEpisode::id)
        // Every show has hundreds of episodes: nothing usable means the format changed.
        if (episodes.isEmpty()) return DataResult.Failure(AppError.EmptyResponse)

        val favorites = favoritesRepository.observeFavoriteIds(FavoriteSource.NASA_PODCAST_EPISODE).first()
        return localDataSource.replaceShow(show, episodes, keepIds = favorites, at = timeProvider.now())
            .map { episodes.size }
    }

    override suspend fun saveProgress(episodeId: String, progress: PodcastProgress): DataResult<Unit> =
        localDataSource.transaction {
            upsertProgress(
                PodcastProgressEntity(
                    episodeId = episodeId,
                    positionMillis = progress.position.toMillis(),
                    isFinished = progress.isFinished,
                    updatedAtMillis = timeProvider.now().toEpochMilli(),
                ),
            )
        }

    override suspend fun requestDownload(episodeId: String): DataResult<Unit> = localDataSource.transaction {
        val existing = findDownload(episodeId)
        if (existing?.state == PodcastDownloadState.DOWNLOADED.name) return@transaction false
        upsertDownload(
            PodcastDownloadEntity(
                episodeId = episodeId,
                state = PodcastDownloadState.QUEUED.name,
                filePath = null,
                bytes = 0,
                updatedAtMillis = timeProvider.now().toEpochMilli(),
            ),
        )
        true
    }.map { queued ->
        if (queued) {
            workManager.get().enqueueUniqueWork(
                PodcastDownloadWorker.uniqueWorkName(episodeId),
                ExistingWorkPolicy.REPLACE,
                PodcastDownloadWorker.request(episodeId),
            )
        }
    }

    override suspend fun removeDownload(episodeId: String): DataResult<Unit> {
        workManager.get().cancelUniqueWork(PodcastDownloadWorker.uniqueWorkName(episodeId))
        return localDataSource.transaction {
            deleteDownload(episodeId)
            files.delete(episodeId)
        }
    }

    override suspend fun downloadedAudioPath(episodeId: String): String? {
        // On the database's IO thread, which also suits the file check.
        val path = localDataSource.transaction {
            findDownload(episodeId)
                ?.takeIf { it.state == PodcastDownloadState.DOWNLOADED.name }
                ?.filePath
                ?.let(files::resolve)
                ?.takeIf { it.isFile }
                ?.absolutePath
        }
        return (path as? DataResult.Success)?.data
    }
}

package com.radustavila.nasaexplorer.core.domain.usecase

import com.radustavila.nasaexplorer.core.common.error.AppError
import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.model.FavoriteSource
import com.radustavila.nasaexplorer.core.model.PodcastDownload
import com.radustavila.nasaexplorer.core.model.PodcastDownloadState
import com.radustavila.nasaexplorer.core.model.PodcastShow
import com.radustavila.nasaexplorer.core.testing.data.TestPodcasts
import com.radustavila.nasaexplorer.core.testing.fake.FakeFavoritesRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakePodcastRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeSyncMetadataRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeTimeProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PodcastUseCasesTest {

    private val metadata = FakeSyncMetadataRepository()
    private val clock = FakeTimeProvider()
    private val repository = FakePodcastRepository()
    private val refreshPodcasts = RefreshPodcastsUseCase(repository, RefreshSourceUseCase(metadata, clock))

    @Test
    fun `every show is refreshed and recorded as its own source`() = runTest {
        assertEquals(DataResult.Success(Unit), refreshPodcasts())

        assertEquals(PodcastShow.entries.toSet(), repository.refreshedShows.toSet())
        PodcastShow.entries.forEach { show ->
            assertEquals(clock.currentInstant, metadata.statusOf(show.syncSource).lastSuccessAt)
        }
    }

    @Test
    fun `shows are not asked again within three hours unless forced`() = runTest {
        refreshPodcasts()
        clock.currentInstant = clock.currentInstant.plus(RefreshPodcastsUseCase.MIN_REFRESH_INTERVAL).minusSeconds(1)

        refreshPodcasts()
        assertEquals(PodcastShow.entries.size, repository.refreshedShows.size)

        refreshPodcasts(force = true)
        assertEquals(2 * PodcastShow.entries.size, repository.refreshedShows.size)
    }

    @Test
    fun `one failing show does not fail the refresh, all failing does`() = runTest {
        repository.refreshResults = mapOf(PodcastShow.CURIOUS_UNIVERSE to DataResult.Failure(AppError.Http(500)))
        assertEquals(DataResult.Success(Unit), refreshPodcasts())

        repository.refreshResults = PodcastShow.entries.associateWith { DataResult.Failure(AppError.NoConnection) }
        assertEquals(DataResult.Failure(AppError.NoConnection), refreshPodcasts(force = true))
    }

    @Test
    fun `an episode is saved to favourites with its artwork`() = runTest {
        val favorites = FakeFavoritesRepository()
        val episode = TestPodcasts.houston()

        SavePodcastEpisodeFavoriteUseCase(SaveFavoriteUseCase(favorites, clock))(episode)

        val saved = favorites.current.single()
        assertEquals(FavoriteSource.NASA_PODCAST_EPISODE, saved.source)
        assertEquals(episode.id, saved.itemId)
        assertEquals(episode.imageUrl, saved.imageUrl)
    }

    @Test
    fun `toggling a download requests it, retries a failed one and removes a finished one`() = runTest {
        val episode = TestPodcasts.houston()
        repository.setEpisodes(listOf(episode))
        val toggle = TogglePodcastDownloadUseCase(repository)

        toggle(episode)
        assertEquals(PodcastDownloadState.DOWNLOADED, repository.current.single().download?.state)

        toggle(repository.current.single())
        assertNull(repository.current.single().download)

        repository.completeDownloads = false
        toggle(episode.copy(download = PodcastDownload(PodcastDownloadState.FAILED)))
        assertEquals(PodcastDownloadState.QUEUED, repository.current.single().download?.state)
    }
}

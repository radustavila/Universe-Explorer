package com.radustavila.nasaexplorer.core.domain.usecase

import com.radustavila.nasaexplorer.core.common.error.AppError
import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.model.FavoriteSource
import com.radustavila.nasaexplorer.core.model.NewsFeed
import com.radustavila.nasaexplorer.core.testing.data.TestNews
import com.radustavila.nasaexplorer.core.testing.fake.FakeFavoritesRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeNewsRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeSyncMetadataRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeTimeProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class NewsUseCasesTest {

    private val metadata = FakeSyncMetadataRepository()
    private val clock = FakeTimeProvider()
    private val repository = FakeNewsRepository()
    private val refreshNews = RefreshNewsUseCase(repository, RefreshSourceUseCase(metadata, clock))

    @Test
    fun `every feed is refreshed and recorded as its own source`() = runTest {
        assertEquals(DataResult.Success(Unit), refreshNews())

        assertEquals(NewsFeed.entries.toSet(), repository.refreshedFeeds.toSet())
        NewsFeed.entries.forEach { feed ->
            assertEquals(clock.currentInstant, metadata.statusOf(feed.syncSource).lastSuccessAt)
        }
    }

    @Test
    fun `feeds are not asked again within three hours unless forced`() = runTest {
        refreshNews()
        clock.currentInstant = clock.currentInstant.plus(RefreshNewsUseCase.MIN_REFRESH_INTERVAL).minusSeconds(1)

        refreshNews()
        assertEquals(NewsFeed.entries.size, repository.refreshedFeeds.size)

        refreshNews(force = true)
        assertEquals(2 * NewsFeed.entries.size, repository.refreshedFeeds.size)
    }

    @Test
    fun `one failing feed does not fail the refresh`() = runTest {
        repository.refreshResults = mapOf(NewsFeed.YOUTUBE to DataResult.Failure(AppError.Http(500)))

        assertEquals(DataResult.Success(Unit), refreshNews())
        assertEquals(true, metadata.statusOf(NewsFeed.YOUTUBE.syncSource).isFailing)
    }

    @Test
    fun `the refresh fails when every feed did`() = runTest {
        repository.refreshResults = NewsFeed.entries.associateWith { DataResult.Failure(AppError.NoConnection) }

        assertEquals(DataResult.Failure(AppError.NoConnection), refreshNews())
    }

    @Test
    fun `an Image of the Day is saved with its largest picture`() = runTest {
        val favorites = FakeFavoritesRepository()
        val image = TestNews.imageOfTheDay()

        SaveNewsImageFavoriteUseCase(SaveFavoriteUseCase(favorites, clock))(image)

        val saved = favorites.current.single()
        assertEquals(FavoriteSource.NASA_IMAGE_OF_THE_DAY, saved.source)
        assertEquals(image.id, saved.itemId)
        assertEquals(image.fullImageUrl, saved.imageUrl)
    }
}

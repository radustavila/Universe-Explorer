package com.radustavila.nasaexplorer.core.domain.usecase

import com.radustavila.nasaexplorer.core.common.error.AppError
import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.domain.sync.SourceRefreshResult
import com.radustavila.nasaexplorer.core.model.FavoriteSource
import com.radustavila.nasaexplorer.core.model.NasaMediaQuery
import com.radustavila.nasaexplorer.core.model.SyncSource
import com.radustavila.nasaexplorer.core.testing.data.TestNasaMedia
import com.radustavila.nasaexplorer.core.testing.fake.FakeFavoritesRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeNasaMediaRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeSyncMetadataRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeTimeProvider
import java.time.Duration
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NasaMediaUseCasesTest {

    private val repository = FakeNasaMediaRepository()
    private val metadata = FakeSyncMetadataRepository()
    private val clock = FakeTimeProvider()
    private val favorites = FakeFavoritesRepository()

    private val refreshGallery = RefreshGalleryUseCase(repository, RefreshSourceUseCase(metadata, clock))
    private val saveFavorite = SaveNasaMediaFavoriteUseCase(repository, SaveFavoriteUseCase(favorites, clock))

    @Test
    fun `rails refresh at most once a day`() = runTest {
        refreshGallery()
        clock.currentInstant = clock.currentInstant.plus(Duration.ofHours(23))
        val skipped = refreshGallery()
        clock.currentInstant = clock.currentInstant.plus(Duration.ofHours(1))
        refreshGallery()

        assertTrue(skipped is SourceRefreshResult.Skipped)
        assertEquals(2, repository.refreshCount)
        assertEquals(clock.currentInstant, metadata.statusOf(SyncSource.NASA_LIBRARY).lastSuccessAt)
    }

    @Test
    fun `pull to refresh ignores the day`() = runTest {
        refreshGallery()

        refreshGallery(force = true)

        assertEquals(2, repository.refreshCount)
    }

    @Test
    fun `a failed refresh leaves the rails due`() = runTest {
        repository.refreshResult = DataResult.Failure(AppError.NoConnection)
        refreshGallery()

        repository.refreshResult = DataResult.Success(Unit)
        refreshGallery()

        assertEquals(2, repository.refreshCount)
        assertEquals(AppError.NoConnection.toString(), metadata.statusOf(SyncSource.NASA_LIBRARY).lastErrorDescription)
    }

    @Test
    fun `searches are trimmed`() = runTest {
        SearchNasaMediaUseCase(repository)(NasaMediaQuery(text = "  webb  "))

        assertEquals("webb", repository.searchCalls.single().first.text)
    }

    @Test
    fun `saving keeps the item and snapshots its preview`() = runTest {
        val media = TestNasaMedia.image()

        assertEquals(DataResult.Success(Unit), saveFavorite(media))

        assertEquals(media, repository.kept[media.nasaId])
        val favorite = favorites.current.single()
        assertEquals(FavoriteSource.NASA_LIBRARY, favorite.source)
        assertEquals(media.nasaId, favorite.itemId)
        assertEquals(media.title, favorite.title)
        assertEquals(media.imageUrl, favorite.imageUrl)
    }

    @Test
    fun `an item that cannot be kept is not saved`() = runTest {
        repository.keepResult = DataResult.Failure(AppError.Database("full"))

        assertEquals(DataResult.Failure(AppError.Database("full")), saveFavorite(TestNasaMedia.image()))
        assertTrue(favorites.current.isEmpty())
    }
}

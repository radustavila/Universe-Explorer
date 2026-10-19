package com.radustavila.nasaexplorer.core.domain.usecase

import app.cash.turbine.test
import com.radustavila.nasaexplorer.core.common.error.AppError
import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.domain.sync.SourceRefreshResult
import com.radustavila.nasaexplorer.core.model.FavoriteItem
import com.radustavila.nasaexplorer.core.model.FavoriteSource
import com.radustavila.nasaexplorer.core.model.SyncSource
import com.radustavila.nasaexplorer.core.testing.data.TestMarsPhotos
import com.radustavila.nasaexplorer.core.testing.fake.FakeFavoritesRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeMarsPhotoRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeMarsSyncScheduler
import com.radustavila.nasaexplorer.core.testing.fake.FakeSyncMetadataRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeTimeProvider
import java.time.Duration
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarsPhotoUseCasesTest {

    private val repository = FakeMarsPhotoRepository()
    private val scheduler = FakeMarsSyncScheduler()
    private val metadata = FakeSyncMetadataRepository()
    private val clock = FakeTimeProvider()
    private val favorites = FakeFavoritesRepository()

    private val synchronize = SynchronizeMarsPhotosUseCase(repository, RefreshSourceUseCase(metadata, clock))

    @Test
    fun `photos refresh at most once a day`() = runTest {
        synchronize()
        clock.currentInstant = clock.currentInstant.plus(Duration.ofHours(23))
        val skipped = synchronize()
        clock.currentInstant = clock.currentInstant.plus(Duration.ofHours(1))
        synchronize()

        assertTrue(skipped is SourceRefreshResult.Skipped)
        assertEquals(2, repository.refreshCount)
        assertEquals(clock.currentInstant, metadata.statusOf(SyncSource.MARS).lastSuccessAt)
    }

    @Test
    fun `a failed refresh is recorded and stays due`() = runTest {
        repository.refreshResult = DataResult.Failure(AppError.Http(code = 403))

        synchronize()
        synchronize()

        assertEquals(2, repository.refreshCount)
        assertTrue(metadata.statusOf(SyncSource.MARS).isFailing)
    }

    @Test
    fun `asking for a refresh also registers the daily one`() {
        val request = RequestMarsSyncUseCase(scheduler)

        request()
        request(force = true)

        assertEquals(listOf(false, true), scheduler.requests)
        assertEquals(2, scheduler.periodicSyncCallCount)
    }

    @Test
    fun `the sync status combines the job with the last outcome`() = runTest {
        ObserveMarsSyncStatusUseCase(scheduler, metadata)().test {
            assertEquals(MarsSyncStatus(), awaitItem())

            scheduler.setSyncing(true)
            assertTrue(awaitItem().isSyncing)

            metadata.recordSyncFailure(SyncSource.MARS, clock.now(), AppError.Timeout, nextAllowedRefreshAt = null)
            val failed = awaitItem()
            assertTrue(failed.lastAttemptFailed)

            metadata.recordSyncSuccess(SyncSource.MARS, clock.now().plusSeconds(1), nextAllowedRefreshAt = null)
            assertFalse(awaitItem().lastAttemptFailed)
        }
    }

    @Test
    fun `a saved photo is snapshotted under the Mars source`() = runTest {
        val photo = TestMarsPhotos.perseverance()

        SaveMarsPhotoFavoriteUseCase(SaveFavoriteUseCase(favorites, clock))(photo)

        assertEquals(
            listOf(
                FavoriteItem(
                    source = FavoriteSource.MARS,
                    itemId = photo.id,
                    title = "Perseverance, sol 1650: Left Navigation Camera (Navcam)",
                    imageUrl = photo.thumbnailUrl,
                    savedAt = clock.now(),
                ),
            ),
            favorites.current,
        )
    }

    @Test
    fun `the rail takes the newest photos of each rover`() = runTest {
        repository.setPhotos(
            listOf(
                TestMarsPhotos.curiosity(id = "c1", sol = 5048),
                TestMarsPhotos.curiosity(id = "c2", sol = 5047),
                TestMarsPhotos.perseverance(id = "p1", sol = 1650),
            ),
        )

        ObserveLatestMarsPhotosUseCase(repository)(perRover = 1).test {
            assertEquals(listOf("c1", "p1"), awaitItem().map { it.id })
        }
    }
}

package com.radustavila.nasaexplorer.core.domain.usecase

import com.radustavila.nasaexplorer.core.common.error.AppError
import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.model.Apod
import com.radustavila.nasaexplorer.core.testing.fake.FakeApodRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeNetworkMonitor
import com.radustavila.nasaexplorer.core.testing.fake.FakeSyncMetadataRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class LoadOlderApodsUseCaseTest {

    private val repository = FakeApodRepository()
    private val networkMonitor = FakeNetworkMonitor()

    @Test
    fun `loads the next window when online`() = runTest {
        repository.loadOlderResult = DataResult.Success(7)
        val useCase = useCase(FakeSyncMetadataRepository(initialOldestRequested = OLDEST_LOADED))

        val result = useCase()

        assertEquals(DataResult.Success(7), result)
        assertEquals(1, repository.loadOlderCallCount)
    }

    @Test
    fun `stops paging once the first published apod is reached`() = runTest {
        val useCase = useCase(
            FakeSyncMetadataRepository(initialOldestRequested = Apod.FIRST_PUBLICATION_DATE),
        )

        val result = useCase()

        assertEquals(DataResult.Success(0), result)
        assertEquals(0, repository.loadOlderCallCount)
    }

    @Test
    fun `reports missing connectivity instead of calling the network`() = runTest {
        networkMonitor.setOnline(false)
        val useCase = useCase(FakeSyncMetadataRepository(initialOldestRequested = OLDEST_LOADED))

        val result = useCase()

        assertEquals(DataResult.Failure(AppError.NoConnection), result)
        assertEquals(0, repository.loadOlderCallCount)
    }

    private fun useCase(metadata: FakeSyncMetadataRepository) = LoadOlderApodsUseCase(
        apodRepository = repository,
        syncMetadataRepository = metadata,
        networkMonitor = networkMonitor,
    )

    private companion object {
        val OLDEST_LOADED: java.time.LocalDate = java.time.LocalDate.of(2026, 9, 4)
    }
}

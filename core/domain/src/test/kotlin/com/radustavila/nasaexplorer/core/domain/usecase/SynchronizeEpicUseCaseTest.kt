package com.radustavila.nasaexplorer.core.domain.usecase

import com.radustavila.nasaexplorer.core.common.error.AppError
import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.model.EpicCollection
import com.radustavila.nasaexplorer.core.testing.fake.FakeEpicRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeNetworkMonitor
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SynchronizeEpicUseCaseTest {

    private val repository = FakeEpicRepository()
    private val networkMonitor = FakeNetworkMonitor()

    private val synchronizeEpic = SynchronizeEpicUseCase(
        epicRepository = repository,
        networkMonitor = networkMonitor,
    )

    @Test
    fun `refreshes both collections and prefetches their rotation thumbnails`() = runTest {
        repository.refreshResults[EpicCollection.NATURAL] = DataResult.Success(13)
        repository.refreshResults[EpicCollection.ENHANCED] = DataResult.Success(12)

        val result = synchronizeEpic()

        assertEquals(DataResult.Success(25), result)
        assertEquals(EpicCollection.entries, repository.refreshedCollections)
        assertEquals(EpicCollection.entries, repository.prefetchedCollections)
    }

    @Test
    fun `does not hit the network when offline`() = runTest {
        networkMonitor.setOnline(false)

        val result = synchronizeEpic()

        assertEquals(DataResult.Failure(AppError.NoConnection), result)
        assertTrue(repository.refreshedCollections.isEmpty())
    }

    @Test
    fun `one failing collection does not stop the other and is what gets reported`() = runTest {
        repository.refreshResults[EpicCollection.NATURAL] = DataResult.Failure(AppError.Timeout)
        repository.refreshResults[EpicCollection.ENHANCED] = DataResult.Success(12)

        val result = synchronizeEpic()

        assertEquals(DataResult.Failure(AppError.Timeout), result)
        assertEquals(EpicCollection.entries, repository.refreshedCollections)
        // Only what was actually refreshed is worth prefetching.
        assertEquals(listOf(EpicCollection.ENHANCED), repository.prefetchedCollections)
    }
}

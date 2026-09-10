package com.radustavila.nasaexplorer.core.domain.usecase

import com.radustavila.nasaexplorer.core.common.error.AppError
import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.testing.fake.FakeApodRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeNetworkMonitor
import com.radustavila.nasaexplorer.core.testing.fake.FakeSyncMetadataRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeTimeProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SynchronizeApodsUseCaseTest {

    private val repository = FakeApodRepository()
    private val metadata = FakeSyncMetadataRepository()
    private val networkMonitor = FakeNetworkMonitor()
    private val timeProvider = FakeTimeProvider()

    private val synchronizeApods = SynchronizeApodsUseCase(
        apodRepository = repository,
        syncMetadataRepository = metadata,
        networkMonitor = networkMonitor,
        timeProvider = timeProvider,
    )

    @Test
    fun `records the sync timestamp after a successful refresh`() = runTest {
        repository.refreshResult = DataResult.Success(7)

        val result = synchronizeApods()

        assertEquals(DataResult.Success(7), result)
        assertEquals(timeProvider.currentInstant, metadata.currentLastSync)
    }

    @Test
    fun `does not hit the network when offline`() = runTest {
        networkMonitor.setOnline(false)

        val result = synchronizeApods()

        assertEquals(DataResult.Failure(AppError.NoConnection), result)
        assertEquals(0, repository.refreshCallCount)
        assertNull(metadata.currentLastSync)
    }

    @Test
    fun `leaves the last sync timestamp untouched when the refresh fails`() = runTest {
        repository.refreshResult = DataResult.Failure(AppError.Http(code = 500))

        val result = synchronizeApods()

        assertEquals(DataResult.Failure(AppError.Http(code = 500)), result)
        assertNull(metadata.currentLastSync)
    }
}

package com.radustavila.nasaexplorer.core.domain.usecase

import com.radustavila.nasaexplorer.core.common.error.AppError
import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.domain.repository.LaunchRefreshOutcome
import com.radustavila.nasaexplorer.core.testing.fake.FakeLaunchRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeNetworkMonitor
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class SynchronizeUpcomingLaunchesUseCaseTest {

    private val repository = FakeLaunchRepository()
    private val networkMonitor = FakeNetworkMonitor()

    private val synchronizeLaunches = SynchronizeUpcomingLaunchesUseCase(
        launchRepository = repository,
        networkMonitor = networkMonitor,
    )

    @Test
    fun `refreshes through the repository when online`() = runTest {
        repository.refreshResult = DataResult.Success(LaunchRefreshOutcome.SKIPPED)

        val result = synchronizeLaunches()

        assertEquals(DataResult.Success(LaunchRefreshOutcome.SKIPPED), result)
        assertEquals(1, repository.refreshCallCount)
    }

    @Test
    fun `does not touch the repository while offline`() = runTest {
        networkMonitor.setOnline(false)

        val result = synchronizeLaunches()

        assertEquals(DataResult.Failure(AppError.NoConnection), result)
        assertEquals(0, repository.refreshCallCount)
    }
}

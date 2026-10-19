package com.radustavila.nasaexplorer.feature.launches

import app.cash.turbine.test
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveUpcomingLaunchesUseCase
import com.radustavila.nasaexplorer.core.testing.data.TestLaunches
import com.radustavila.nasaexplorer.core.testing.fake.FakeLaunchRepository
import com.radustavila.nasaexplorer.core.testing.rule.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class NextLaunchesViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeLaunchRepository()

    @Test
    fun `exposes the cached launches without refreshing them`() = runTest {
        val launches = TestLaunches.upcoming(size = 4)
        repository.setLaunches(launches)

        createViewModel().uiState.test {
            var state = awaitItem()
            while (state.launches.isEmpty()) state = awaitItem()

            assertEquals(launches, state.launches)
            assertEquals(0, repository.refreshCallCount)
        }
    }

    @Test
    fun `follows the cache as the sync replaces it`() = runTest {
        val viewModel = createViewModel()

        viewModel.uiState.test {
            assertTrue(awaitItem().launches.isEmpty())

            repository.setLaunches(TestLaunches.upcoming(size = 2))

            assertEquals(2, awaitItem().launches.size)
        }
    }

    private fun createViewModel() = NextLaunchesViewModel(
        observeUpcomingLaunches = ObserveUpcomingLaunchesUseCase(repository),
    )
}

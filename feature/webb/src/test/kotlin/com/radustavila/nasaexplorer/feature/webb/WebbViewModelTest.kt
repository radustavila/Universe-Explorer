package com.radustavila.nasaexplorer.feature.webb

import app.cash.turbine.test
import com.radustavila.nasaexplorer.core.common.error.AppError
import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveDataFreshnessUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveWebbNowUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveWebbScheduleUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.RefreshSourceUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.RefreshWebbScheduleUseCase
import com.radustavila.nasaexplorer.core.testing.data.TestWebb
import com.radustavila.nasaexplorer.core.testing.fake.FakeNetworkMonitor
import com.radustavila.nasaexplorer.core.testing.fake.FakeSyncMetadataRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeTimeProvider
import com.radustavila.nasaexplorer.core.testing.fake.FakeWebbScheduleRepository
import com.radustavila.nasaexplorer.core.testing.rule.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WebbViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val clock = FakeTimeProvider(currentInstant = TestWebb.NOW)
    private val metadata = FakeSyncMetadataRepository()
    private val repository = FakeWebbScheduleRepository(TestWebb.week())

    private fun viewModel() = WebbViewModel(
        observeNow = ObserveWebbNowUseCase(repository, clock),
        observeSchedule = ObserveWebbScheduleUseCase(repository),
        observeFreshness = ObserveDataFreshnessUseCase(metadata, FakeNetworkMonitor()),
        refreshSchedule = RefreshWebbScheduleUseCase(repository, RefreshSourceUseCase(metadata, clock)),
    )

    @Test
    fun `opening refreshes once and shows the visit scheduled now and the week`() = runTest {
        val viewModel = viewModel()

        viewModel.uiState.test {
            val state = expectMostRecentItem()
            assertTrue(state.isLoaded)
            assertEquals(TestWebb.WASP_39, state.now?.current)
            assertEquals(5, state.visits.size)
            assertFalse(state.isRefreshing)
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(1, repository.refreshCount)
    }

    @Test
    fun `a second opening within the day does not download again, pull to refresh does`() = runTest {
        viewModel()
        val viewModel = viewModel()

        viewModel.onRefresh()

        assertEquals(2, repository.refreshCount)
    }

    @Test
    fun `before the first download there is nothing scheduled`() = runTest {
        repository.schedule.value = null
        repository.refreshResult = DataResult.Failure(AppError.NoConnection)

        viewModel().uiState.test {
            val state = expectMostRecentItem()
            assertTrue(state.isLoaded)
            assertNull(state.now)
            assertTrue(state.visits.isEmpty())
            assertFalse(state.refreshFailed)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `only a failed pull to refresh is reported, once`() = runTest {
        repository.refreshResult = DataResult.Failure(AppError.NoConnection)
        val viewModel = viewModel()

        viewModel.uiState.test {
            assertFalse(expectMostRecentItem().refreshFailed)
            viewModel.onRefresh()
            assertTrue(expectMostRecentItem().refreshFailed)
            viewModel.onRefreshFailedShown()
            assertFalse(expectMostRecentItem().refreshFailed)
            cancelAndIgnoreRemainingEvents()
        }
    }
}

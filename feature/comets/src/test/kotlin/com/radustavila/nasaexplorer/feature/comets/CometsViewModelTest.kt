package com.radustavila.nasaexplorer.feature.comets

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.radustavila.nasaexplorer.core.common.error.AppError
import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveCometTonightUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveCometsTonightUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveDataFreshnessUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.RefreshCometsUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.RefreshSourceUseCase
import com.radustavila.nasaexplorer.core.testing.data.TestComets
import com.radustavila.nasaexplorer.core.testing.fake.FakeCometRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeCometSkyCalculator
import com.radustavila.nasaexplorer.core.testing.fake.FakeNetworkMonitor
import com.radustavila.nasaexplorer.core.testing.fake.FakeObserverLocationRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeSyncMetadataRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeTimeProvider
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
class CometsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val clock = FakeTimeProvider(currentInstant = TestComets.NOW)
    private val metadata = FakeSyncMetadataRepository()
    private val bok = TestComets.comet()
    private val repository = FakeCometRepository(listOf(bok))
    private val calculator = FakeCometSkyCalculator(mapOf(bok.designation to TestComets.tonight(bok)))
    private val location = FakeObserverLocationRepository()

    private fun viewModel() = CometsViewModel(
        observeCometsTonight = ObserveCometsTonightUseCase(
            repository,
            location,
            calculator,
            clock,
            mainDispatcherRule.testDispatcher,
        ),
        observeFreshness = ObserveDataFreshnessUseCase(metadata, FakeNetworkMonitor()),
        refreshComets = RefreshCometsUseCase(repository, RefreshSourceUseCase(metadata, clock)),
    )

    @Test
    fun `opening the screen refreshes once and shows the cached comets`() = runTest {
        val viewModel = viewModel()

        viewModel.uiState.test {
            val state = expectMostRecentItem()
            assertTrue(state.isLoaded)
            assertEquals(listOf("2026 A2"), state.comets!!.faint.map { it.comet.designation })
            assertTrue(state.comets!!.visible.isEmpty())
            assertFalse(state.hasPlace)
            assertEquals(TestComets.NOW, state.freshness.lastUpdatedAt)
        }
        assertEquals(1, repository.refreshCount)
    }

    @Test
    fun `pull to refresh forces a download and reports a failure once`() = runTest {
        val viewModel = viewModel()
        repository.refreshResult = DataResult.Failure(AppError.NoConnection)

        viewModel.uiState.test {
            viewModel.onRefresh()
            assertTrue(expectMostRecentItem().refreshFailed)
            viewModel.onRefreshFailedShown()
            assertFalse(awaitItem().refreshFailed)
        }
        assertEquals(2, repository.refreshCount)
    }

    @Test
    fun `details follow one comet by its route's designation`() = runTest {
        val handle = SavedStateHandle(mapOf("designation" to "2026 A2"))
        val details = CometDetailsViewModel(
            handle,
            ObserveCometTonightUseCase(repository, location, calculator, clock, mainDispatcherRule.testDispatcher),
        )

        details.uiState.test {
            val state = expectMostRecentItem()
            assertTrue(state.isLoaded)
            assertEquals("C/2026 A2 (Bok)", state.comet!!.comet.name)
            repository.comets.value = emptyList()
            assertNull(awaitItem().comet)
        }
    }
}

package com.radustavila.nasaexplorer.feature.climate

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.radustavila.nasaexplorer.core.common.error.AppError
import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveClimateUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveDataFreshnessUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.RefreshClimateUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.RefreshSourceUseCase
import com.radustavila.nasaexplorer.core.model.ClimateIndicator
import com.radustavila.nasaexplorer.core.testing.data.TestClimate
import com.radustavila.nasaexplorer.core.testing.fake.FakeClimateRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeNetworkMonitor
import com.radustavila.nasaexplorer.core.testing.fake.FakeSyncMetadataRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeTimeProvider
import com.radustavila.nasaexplorer.core.testing.rule.MainDispatcherRule
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ClimateViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val clock = FakeTimeProvider(currentInstant = Instant.parse("2026-10-23T09:00:00Z"))
    private val metadata = FakeSyncMetadataRepository()
    private val repository = FakeClimateRepository(TestClimate.all())
    private val refresh = RefreshClimateUseCase(repository, RefreshSourceUseCase(metadata, clock))
    private val freshness = ObserveDataFreshnessUseCase(metadata, FakeNetworkMonitor())

    private fun detail(key: String) = ClimateViewModel(
        savedStateHandle = SavedStateHandle(mapOf("indicator" to key)),
        observeClimate = ObserveClimateUseCase(repository),
        observeFreshness = freshness,
        refreshClimate = refresh,
    )

    @Test
    fun `the section shows every stored sign and refreshes them all once`() = runTest {
        val viewModel = VitalSignsViewModel(ObserveClimateUseCase(repository), freshness, refresh)

        viewModel.uiState.test {
            val state = expectMostRecentItem()
            assertTrue(state.isLoaded)
            assertEquals(ClimateIndicator.entries, state.series.map { it.indicator })
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(ClimateIndicator.entries.toSet(), repository.refreshed.toSet())
        VitalSignsViewModel(ObserveClimateUseCase(repository), freshness, refresh)
        assertEquals(4, repository.refreshed.size)
    }

    @Test
    fun `the detail shows its indicator and pull to refresh downloads only its file`() = runTest {
        val viewModel = detail("sea_level")

        viewModel.uiState.test {
            val state = expectMostRecentItem()
            assertEquals(ClimateIndicator.SEA_LEVEL, state.indicator)
            assertEquals(TestClimate.seaLevel(), state.series)
            cancelAndIgnoreRemainingEvents()
        }
        viewModel.onRefresh()

        assertEquals(listOf(ClimateIndicator.SEA_LEVEL, ClimateIndicator.SEA_LEVEL), repository.refreshed)
    }

    @Test
    fun `only a failed refresh the user asked for is reported`() = runTest {
        repository.refreshResult = DataResult.Failure(AppError.NoConnection)
        val viewModel = detail("co2")

        viewModel.uiState.test {
            assertFalse(expectMostRecentItem().refreshFailed)
            viewModel.onRefresh()
            assertTrue(expectMostRecentItem().refreshFailed)
            viewModel.onRefreshFailedShown()
            assertFalse(expectMostRecentItem().refreshFailed)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `an unknown key falls back to CO2`() {
        assertEquals(ClimateIndicator.CO2, detail("ozone").uiState.value.indicator)
    }
}

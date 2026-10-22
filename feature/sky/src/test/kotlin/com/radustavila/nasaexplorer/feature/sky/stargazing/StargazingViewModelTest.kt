package com.radustavila.nasaexplorer.feature.sky.stargazing

import app.cash.turbine.test
import com.radustavila.nasaexplorer.core.common.error.AppError
import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveConnectivityUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveObserverLocationUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveStargazingForecastUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.RefreshCloudForecastUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.RefreshSourceUseCase
import com.radustavila.nasaexplorer.core.testing.data.TestSky
import com.radustavila.nasaexplorer.core.testing.fake.FakeCloudForecastRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeDarkSkyCalculator
import com.radustavila.nasaexplorer.core.testing.fake.FakeNetworkMonitor
import com.radustavila.nasaexplorer.core.testing.fake.FakeObserverLocationRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeSyncMetadataRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeTimeProvider
import com.radustavila.nasaexplorer.core.testing.fake.TestStargazing
import com.radustavila.nasaexplorer.core.testing.rule.MainDispatcherRule
import java.time.Instant
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class StargazingViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val noon = Instant.parse("2026-09-10T12:00:00Z")
    private val clock = FakeTimeProvider(currentInstant = noon)
    private val location = FakeObserverLocationRepository()
    private val forecasts = FakeCloudForecastRepository()
    private val network = FakeNetworkMonitor()
    private val calculator = FakeDarkSkyCalculator(
        listOf(
            TestStargazing.darkNight(darkStart = Instant.parse("2026-09-10T20:00:00Z")),
            TestStargazing.darkNight(darkStart = Instant.parse("2026-09-11T20:00:00Z")),
        ),
    )

    private fun createViewModel() = StargazingViewModel(
        observeLocation = ObserveObserverLocationUseCase(location),
        observeForecast = ObserveStargazingForecastUseCase(forecasts, calculator, clock, UnconfinedTestDispatcher()),
        refreshForecast = RefreshCloudForecastUseCase(forecasts, RefreshSourceUseCase(FakeSyncMetadataRepository(), clock)),
        observeConnectivity = ObserveConnectivityUseCase(network),
    )

    @Test
    fun `without a place there is nothing to fetch or show`() = runTest {
        createViewModel().uiState.test {
            val state = awaitUntil { it.isLoaded }
            assertFalse(state.hasLocation)
            assertTrue(forecasts.refreshRequests.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the saved place is refreshed and its nights scored from the cache`() = runTest {
        location.setLocation(TestSky.location())
        forecasts.forecast.value = TestStargazing.cloudForecast(from = noon) { 0.0 }

        createViewModel().uiState.test {
            val state = awaitUntil { it.forecast != null }
            assertEquals(listOf(100, 100), state.forecast!!.nights.map { it.score })
            assertEquals(noon, state.freshness.lastUpdatedAt)
            assertEquals(listOf(TestSky.BUCHAREST), forecasts.refreshRequests)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `offline, the cached outlook stays with an offline freshness line`() = runTest {
        location.setLocation(TestSky.location())
        forecasts.forecast.value = TestStargazing.cloudForecast(from = noon) { 0.0 }
        forecasts.refreshResult = DataResult.Failure(AppError.NoConnection)
        network.setOnline(false)

        createViewModel().uiState.test {
            val state = awaitUntil { it.forecast != null && it.freshness.lastRefreshFailed }
            assertTrue(state.freshness.isOffline)
            assertEquals(100, state.forecast!!.nights.first().score)
            cancelAndIgnoreRemainingEvents()
        }
    }

    private suspend fun app.cash.turbine.ReceiveTurbine<StargazingUiState>.awaitUntil(
        predicate: (StargazingUiState) -> Boolean,
    ): StargazingUiState {
        while (true) {
            val item = awaitItem()
            if (predicate(item)) return item
        }
    }
}

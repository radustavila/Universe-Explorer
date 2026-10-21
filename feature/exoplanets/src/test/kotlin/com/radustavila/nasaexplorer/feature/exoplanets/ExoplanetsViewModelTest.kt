package com.radustavila.nasaexplorer.feature.exoplanets

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.radustavila.nasaexplorer.core.common.error.AppError
import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveConnectivityUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveExoplanetCatalogStatsUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveExoplanetSyncStatusUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveExoplanetsUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.RefreshSourceUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.RequestExoplanetSyncUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.SynchronizeExoplanetsUseCase
import com.radustavila.nasaexplorer.core.model.DataFreshness
import com.radustavila.nasaexplorer.core.model.ExoplanetCatalogStats
import com.radustavila.nasaexplorer.core.model.ExoplanetList
import com.radustavila.nasaexplorer.core.testing.data.TestExoplanets
import com.radustavila.nasaexplorer.core.testing.fake.FakeExoplanetRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeExoplanetSyncScheduler
import com.radustavila.nasaexplorer.core.testing.fake.FakeNetworkMonitor
import com.radustavila.nasaexplorer.core.testing.fake.FakeSyncMetadataRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeTimeProvider
import com.radustavila.nasaexplorer.core.testing.rule.MainDispatcherRule
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ExoplanetsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val newPlanet = TestExoplanets.exoplanet()
    private val repository = FakeExoplanetRepository(
        initialPlanets = listOf(newPlanet, TestExoplanets.hotJupiter, TestExoplanets.proximaB),
    )
    private val scheduler = FakeExoplanetSyncScheduler()
    private val metadata = FakeSyncMetadataRepository()
    private val networkMonitor = FakeNetworkMonitor()
    private val clock = FakeTimeProvider(currentDate = LocalDate.of(2026, 10, 19))
    private val savedStateHandle = SavedStateHandle()

    private fun createViewModel() = ExoplanetsViewModel(
        savedStateHandle = savedStateHandle,
        observeExoplanets = ObserveExoplanetsUseCase(repository),
        observeCatalogStats = ObserveExoplanetCatalogStatsUseCase(repository),
        observeSyncStatus = ObserveExoplanetSyncStatusUseCase(scheduler, metadata),
        observeConnectivity = ObserveConnectivityUseCase(networkMonitor),
        requestSync = RequestExoplanetSyncUseCase(scheduler),
        timeProvider = clock,
    )

    @Test
    fun `opening the screen requests a sync that may be skipped`() {
        createViewModel()

        assertEquals(listOf(false), scheduler.requests)
    }

    @Test
    fun `pull to refresh forces a sync`() {
        val viewModel = createViewModel()

        viewModel.onRefresh()

        assertEquals(listOf(false, true), scheduler.requests)
    }

    @Test
    fun `shows the newest list from the cache with the archive total`() = runTest {
        repository.setStats(ExoplanetCatalogStats(confirmedPlanets = 6451))

        createViewModel().uiState.test {
            var state = awaitItem()
            while (state.isLoading || state.confirmedPlanets == null) state = awaitItem()

            assertEquals(ExoplanetList.NEWEST, state.selectedList)
            assertEquals(listOf("TOI-2158 c", "TOI-789 c"), state.planets.map { it.name })
            assertEquals(6451, state.confirmedPlanets)
            assertEquals(2025, state.newestSinceYear)
        }
    }

    @Test
    fun `switching lists reads the other list and survives recreation`() = runTest {
        val viewModel = createViewModel()

        viewModel.uiState.test {
            awaitItem()
            viewModel.onListSelected(ExoplanetList.NEAREST)
            var state = awaitItem()
            while (state.isLoading || state.selectedList != ExoplanetList.NEAREST) state = awaitItem()

            assertEquals("Proxima Cen b", state.planets.first().name)
            cancelAndIgnoreRemainingEvents()
        }

        createViewModel().uiState.test {
            var state = awaitItem()
            while (state.isLoading) state = awaitItem()
            assertEquals(ExoplanetList.NEAREST, state.selectedList)
        }
    }

    @Test
    fun `offline before the first sync is distinguishable from an empty list`() = runTest {
        repository.setPlanets(emptyList())
        networkMonitor.setOnline(false)

        createViewModel().uiState.test {
            var state = awaitItem()
            while (state.isLoading || !state.isOffline) state = awaitItem()

            assertTrue(state.planets.isEmpty())
            assertTrue(state.neverSynced)
        }
    }

    @Test
    fun `a failed refresh is reported while the cached list stays`() = runTest {
        repository.refreshResult = DataResult.Failure(AppError.Timeout)
        SynchronizeExoplanetsUseCase(repository, RefreshSourceUseCase(metadata, clock)).invoke()

        createViewModel().uiState.test {
            var state = awaitItem()
            while (state.isLoading || !state.lastRefreshFailed) state = awaitItem()

            assertTrue(state.planets.isNotEmpty())
            assertFalse(state.isRefreshing)
        }
    }

    @Test
    fun `the catalogue's last sync feeds the freshness line`() = runTest {
        SynchronizeExoplanetsUseCase(repository, RefreshSourceUseCase(metadata, clock)).invoke()

        createViewModel().uiState.test {
            var state = awaitItem()
            while (state.freshness.lastUpdatedAt == null) state = awaitItem()

            assertEquals(DataFreshness(lastUpdatedAt = clock.currentInstant), state.freshness)
        }
    }

    @Test
    fun `a running sync shows as refreshing`() = runTest {
        scheduler.setSyncing(true)

        createViewModel().uiState.test {
            var state = awaitItem()
            while (!state.isRefreshing) state = awaitItem()
            assertTrue(state.isRefreshing)
        }
    }
}

package com.radustavila.nasaexplorer.feature.universe.gallery

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.radustavila.nasaexplorer.core.common.error.AppError
import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveConnectivityUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveDataFreshnessUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveGalleryRailsUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.RefreshGalleryUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.RefreshSourceUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.SearchNasaMediaUseCase
import com.radustavila.nasaexplorer.core.model.GalleryRail
import com.radustavila.nasaexplorer.core.model.NasaMediaPage
import com.radustavila.nasaexplorer.core.model.NasaMediaType
import com.radustavila.nasaexplorer.core.testing.data.TestNasaMedia
import com.radustavila.nasaexplorer.core.testing.fake.FakeNasaMediaRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeNetworkMonitor
import com.radustavila.nasaexplorer.core.testing.fake.FakeSyncMetadataRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeTimeProvider
import com.radustavila.nasaexplorer.core.testing.rule.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class GalleryViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val webb = TestNasaMedia.images(4, prefix = "webb")
    private val repository = FakeNasaMediaRepository()
    private val networkMonitor = FakeNetworkMonitor()
    private val metadata = FakeSyncMetadataRepository()
    private val clock = FakeTimeProvider()

    private fun createViewModel(savedStateHandle: SavedStateHandle = SavedStateHandle()) = GalleryViewModel(
        savedStateHandle = savedStateHandle,
        observeRails = ObserveGalleryRailsUseCase(repository),
        observeConnectivity = ObserveConnectivityUseCase(networkMonitor),
        observeDataFreshness = ObserveDataFreshnessUseCase(metadata, networkMonitor),
        refreshGallery = RefreshGalleryUseCase(repository, RefreshSourceUseCase(metadata, clock)),
        searchMedia = SearchNasaMediaUseCase(repository),
    )

    @Test
    fun `cached rails show without waiting for the network`() = runTest {
        repository.setRails(mapOf(GalleryRail.WEBB to webb))
        repository.refreshResult = DataResult.Failure(AppError.NoConnection)
        networkMonitor.setOnline(false)

        createViewModel().uiState.test {
            var state = awaitItem()
            while (state.rails.isEmpty()) state = awaitItem()

            assertEquals(listOf(RailUi(GalleryRail.WEBB, webb)), state.rails)
            assertEquals(RailsStatus.Content, state.railsStatus)
            assertTrue(state.isOffline)
        }
    }

    @Test
    fun `an empty cache is filled on arrival`() = runTest {
        repository.refreshedRails = mapOf(GalleryRail.NEBULAE to webb)

        createViewModel().uiState.test {
            var state = awaitItem()
            while (state.rails.isEmpty()) state = awaitItem()

            assertEquals(GalleryRail.NEBULAE, state.rails.single().rail)
            while (state.freshness.lastUpdatedAt == null) state = awaitItem()
            assertEquals(clock.currentInstant, state.freshness.lastUpdatedAt)
        }
        assertEquals(1, repository.refreshCount)
    }

    @Test
    fun `an empty cache offline says the gallery needs one connection`() = runTest {
        repository.refreshResult = DataResult.Failure(AppError.NoConnection)
        networkMonitor.setOnline(false)

        createViewModel().uiState.test {
            var state = awaitItem()
            while (state.isRefreshing || state.refreshError == null) state = awaitItem()

            assertEquals(RailsStatus.Offline, state.railsStatus)
        }
    }

    @Test
    fun `a failed refresh is retried when the connection returns`() = runTest {
        repository.refreshResult = DataResult.Failure(AppError.NoConnection)
        networkMonitor.setOnline(false)
        val viewModel = createViewModel()

        viewModel.uiState.test {
            var state = awaitItem()
            while (state.refreshError == null) state = awaitItem()

            repository.refreshResult = DataResult.Success(Unit)
            repository.refreshedRails = mapOf(GalleryRail.WEBB to webb)
            networkMonitor.setOnline(true)

            while (state.rails.isEmpty()) state = awaitItem()
            assertEquals(webb, state.rails.single().items)
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(2, repository.refreshCount)
    }

    @Test
    fun `typing searches the library with the selected filters`() = runTest {
        repository.searchPages = mapOf(1 to NasaMediaPage(webb, totalHits = 4, nextPage = null))
        val viewModel = createViewModel()

        viewModel.uiState.test {
            viewModel.onToggleMediaType(NasaMediaType.AUDIO)
            viewModel.onYearRangeChange(2000, 2020)
            viewModel.onQueryChange("webb")

            var state = awaitItem()
            while (state.search !is SearchUiState.Results) state = awaitItem()

            assertEquals(webb, (state.search as SearchUiState.Results).items)
        }
        val (query, page) = repository.searchCalls.last()
        assertEquals("webb", query.text)
        assertEquals(setOf(NasaMediaType.IMAGE, NasaMediaType.VIDEO), query.mediaTypes)
        assertEquals(2000, query.yearStart)
        assertEquals(2020, query.yearEnd)
        assertEquals(1, page)
    }

    @Test
    fun `typing quickly searches once, for the last text`() = runTest {
        repository.searchPages = mapOf(1 to NasaMediaPage(webb, totalHits = 4, nextPage = null))
        val viewModel = createViewModel()

        viewModel.uiState.test {
            viewModel.onQueryChange("w")
            viewModel.onQueryChange("we")
            viewModel.onQueryChange("webb")

            var state = awaitItem()
            while (state.search !is SearchUiState.Results) state = awaitItem()
        }
        assertEquals(listOf("webb"), repository.searchCalls.map { it.first.text })
    }

    @Test
    fun `the last media type cannot be deselected`() = runTest {
        val viewModel = createViewModel()

        viewModel.uiState.test {
            viewModel.onToggleMediaType(NasaMediaType.VIDEO)
            viewModel.onToggleMediaType(NasaMediaType.AUDIO)
            viewModel.onToggleMediaType(NasaMediaType.IMAGE)

            var state = awaitItem()
            while (state.filters.mediaTypes.size != 1) state = awaitItem()
            expectNoEvents()
            assertEquals(setOf(NasaMediaType.IMAGE), state.filters.mediaTypes)
        }
    }

    @Test
    fun `offline search asks for a connection and recovers by itself`() = runTest {
        networkMonitor.setOnline(false)
        repository.searchError = AppError.NoConnection
        val viewModel = createViewModel()

        viewModel.uiState.test {
            viewModel.onQueryChange("nebula")

            var state = awaitItem()
            while (state.search != SearchUiState.NeedsConnection) state = awaitItem()

            repository.searchPages = mapOf(1 to NasaMediaPage(webb, totalHits = 4, nextPage = null))
            networkMonitor.setOnline(true)

            while (state.search !is SearchUiState.Results) state = awaitItem()
        }
    }

    @Test
    fun `a search with no hits is empty, a server error is a failure`() = runTest {
        repository.searchPages = mapOf(1 to NasaMediaPage(emptyList(), totalHits = 0, nextPage = null))
        val viewModel = createViewModel()

        viewModel.uiState.test {
            viewModel.onQueryChange("zzz")
            var state = awaitItem()
            while (state.search != SearchUiState.Empty) state = awaitItem()

            repository.searchPages = emptyMap()
            repository.searchError = AppError.Http(code = 500)
            viewModel.onRetrySearch()
            while (state.search !is SearchUiState.Failed) state = awaitItem()
        }
    }

    @Test
    fun `scrolling to the end appends the next page`() = runTest {
        val more = TestNasaMedia.images(3, prefix = "more")
        repository.searchPages = mapOf(
            1 to NasaMediaPage(webb, totalHits = 7, nextPage = 2),
            2 to NasaMediaPage(more, totalHits = 7, nextPage = null),
        )
        val viewModel = createViewModel()

        viewModel.uiState.test {
            viewModel.onQueryChange("webb")
            var state = awaitItem()
            while (state.search !is SearchUiState.Results) state = awaitItem()
            assertTrue((state.search as SearchUiState.Results).canLoadMore)

            viewModel.onLoadMore()

            while ((state.search as? SearchUiState.Results)?.items?.size != 7) state = awaitItem()
            val results = state.search as SearchUiState.Results
            assertEquals(webb + more, results.items)
            assertEquals(false, results.canLoadMore)
        }
        assertEquals(listOf(1, 2), repository.searchCalls.map { it.second })
    }

    @Test
    fun `a failed next page keeps the results and offers a retry`() = runTest {
        repository.searchPages = mapOf(1 to NasaMediaPage(webb, totalHits = 7, nextPage = 2))
        repository.searchError = AppError.Timeout
        val viewModel = createViewModel()

        viewModel.uiState.test {
            viewModel.onQueryChange("webb")
            var state = awaitItem()
            while (state.search !is SearchUiState.Results) state = awaitItem()

            viewModel.onLoadMore()

            while ((state.search as? SearchUiState.Results)?.appendError == null) state = awaitItem()
            val results = state.search as SearchUiState.Results
            assertEquals(webb, results.items)
            assertNotNull(results.appendError)
        }
    }

    @Test
    fun `clearing the query returns to the rails`() = runTest {
        repository.searchPages = mapOf(1 to NasaMediaPage(webb, totalHits = 4, nextPage = null))
        val viewModel = createViewModel()

        viewModel.uiState.test {
            viewModel.onQueryChange("webb")
            var state = awaitItem()
            while (state.search !is SearchUiState.Results) state = awaitItem()

            viewModel.onClearQuery()

            while (state.isSearching) state = awaitItem()
            assertEquals(SearchUiState.Idle, state.search)
        }
    }

    @Test
    fun `the query and filters survive a recreated screen`() = runTest {
        val savedState = SavedStateHandle()
        createViewModel(savedState).apply {
            onQueryChange("black hole")
            onToggleMediaType(NasaMediaType.VIDEO)
        }

        createViewModel(savedState).uiState.test {
            var state = awaitItem()
            while (state.filters.mediaTypes.size != 2) state = awaitItem()

            assertEquals("black hole", state.query)
            assertEquals(setOf(NasaMediaType.IMAGE, NasaMediaType.AUDIO), state.filters.mediaTypes)
        }
    }
}

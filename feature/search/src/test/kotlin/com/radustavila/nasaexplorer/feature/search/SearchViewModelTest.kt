package com.radustavila.nasaexplorer.feature.search

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.radustavila.nasaexplorer.core.common.error.AppError
import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.domain.search.UniversalSearchUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveConnectivityUseCase
import com.radustavila.nasaexplorer.core.model.SearchSource
import com.radustavila.nasaexplorer.core.testing.data.TestSearchHits
import com.radustavila.nasaexplorer.core.testing.fake.FakeNetworkMonitor
import com.radustavila.nasaexplorer.core.testing.fake.FakeSearchableSource
import com.radustavila.nasaexplorer.core.testing.rule.MainDispatcherRule
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SearchViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val apod = FakeSearchableSource(
        SearchSource.APOD,
        hits = listOf(
            TestSearchHits.apod("The Supernova of Kepler"),
            TestSearchHits.apod("Saturn at Night"),
        ),
    )
    private val exoplanets = FakeSearchableSource(
        SearchSource.EXOPLANETS,
        hits = (1..5).map { TestSearchHits.exoplanet("Kepler-$it b") },
    )
    private val networkMonitor = FakeNetworkMonitor()

    private fun createViewModel(query: String? = null) = SearchViewModel(
        savedStateHandle = SavedStateHandle(listOfNotNull(query?.let { "query" to it }).toMap()),
        universalSearch = UniversalSearchUseCase(setOf(apod, exoplanets)),
        observeConnectivity = ObserveConnectivityUseCase(networkMonitor),
    )

    @Test
    fun `waits for a pause in typing and searches only the last query`() = runTest {
        val viewModel = createViewModel()
        viewModel.uiState.test {
            assertFalse(awaitItem().hasQuery)

            viewModel.onQueryChange("ke")
            advanceTimeBy(100)
            viewModel.onQueryChange("kep")
            advanceTimeBy(100)
            viewModel.onQueryChange("kepler")
            val typedAt = currentTime

            val state = awaitResults()
            assertEquals(SearchViewModel.DEBOUNCE_MILLIS, currentTime - typedAt)
            assertEquals(listOf("kepler"), apod.queries)
            assertFalse(state.isSearching)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `results are grouped by source, best group first, and pending while typing`() = runTest {
        val viewModel = createViewModel()
        viewModel.uiState.test {
            awaitItem()
            viewModel.onQueryChange("kepler")
            assertTrue(expectMostRecentItem().isSearching)

            val state = awaitResults()
            // "Kepler-1 b" starts with the query; "The Supernova of Kepler" only has it as a word.
            assertEquals(listOf(SearchSource.EXOPLANETS, SearchSource.APOD), state.visibleGroups.map { it.source })
            assertEquals(5, state.visibleGroups.first().hits.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `see all shows one group until the query changes`() = runTest {
        val viewModel = createViewModel(query = "kepler")
        viewModel.uiState.test {
            awaitResults()

            viewModel.onSeeAll(SearchSource.EXOPLANETS)
            var state = awaitItem()
            assertEquals(SearchSource.EXOPLANETS, state.expandedSource)
            assertEquals(listOf(SearchSource.EXOPLANETS), state.visibleGroups.map { it.source })

            viewModel.onShowAllGroups()
            assertNull(awaitItem().expandedSource)

            viewModel.onSeeAll(SearchSource.EXOPLANETS)
            awaitItem()
            viewModel.onQueryChange("kepler-2")
            state = awaitResults()
            assertNull(state.expandedSource)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a query handed in by the route is searched straight away`() = runTest {
        createViewModel(query = "saturn").uiState.test {
            val state = awaitResults()
            assertEquals("saturn", state.query)
            assertEquals(listOf("Saturn at Night"), state.visibleGroups.single().hits.map { it.title })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `clearing the query drops the results at once`() = runTest {
        val viewModel = createViewModel(query = "kepler")
        viewModel.uiState.test {
            awaitResults()
            val clearedAt = currentTime

            viewModel.onClearQuery()
            var state = awaitItem()
            while (state.results != null) state = awaitItem()

            assertEquals(clearedAt, currentTime)
            assertFalse(state.hasQuery)
            assertFalse(state.isSearching)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `nothing found is a no-results state, and a failed source is named`() = runTest {
        exoplanets.result = DataResult.Failure(AppError.Database())
        createViewModel(query = "jupiter").uiState.test {
            val state = awaitResults()
            assertTrue(state.hasNoResults)
            assertEquals(setOf(SearchSource.EXOPLANETS), state.results?.failedSources)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `search stays local while offline and says so`() = runTest {
        networkMonitor.setOnline(false)
        createViewModel(query = "saturn").uiState.test {
            val state = awaitResults()
            assertTrue(state.isOffline)
            assertEquals(1, state.visibleGroups.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    private suspend fun ReceiveTurbine<SearchUiState>.awaitResults(): SearchUiState {
        var state = awaitItem()
        while (state.isSearching || state.results == null) state = awaitItem()
        return state
    }
}

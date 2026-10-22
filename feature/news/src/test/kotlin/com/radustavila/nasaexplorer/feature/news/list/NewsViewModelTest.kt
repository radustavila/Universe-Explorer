package com.radustavila.nasaexplorer.feature.news.list

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.radustavila.nasaexplorer.core.common.error.AppError
import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveDataFreshnessUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveNewsUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.RefreshNewsUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.RefreshSourceUseCase
import com.radustavila.nasaexplorer.core.model.NewsFeed
import com.radustavila.nasaexplorer.core.model.NewsKind
import com.radustavila.nasaexplorer.core.testing.data.TestNews
import com.radustavila.nasaexplorer.core.testing.fake.FakeNetworkMonitor
import com.radustavila.nasaexplorer.core.testing.fake.FakeNewsRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeSyncMetadataRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeTimeProvider
import com.radustavila.nasaexplorer.core.testing.rule.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class NewsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeNewsRepository(TestNews.ALL)
    private val metadata = FakeSyncMetadataRepository()
    private val clock = FakeTimeProvider(currentInstant = TestNews.NOW)
    private val savedState = SavedStateHandle()

    private fun viewModel() = NewsViewModel(
        savedStateHandle = savedState,
        observeNews = ObserveNewsUseCase(repository),
        refreshNews = RefreshNewsUseCase(repository, RefreshSourceUseCase(metadata, clock)),
        observeDataFreshness = ObserveDataFreshnessUseCase(metadata, FakeNetworkMonitor()),
        timeProvider = clock,
    )

    @Test
    fun `opening the list refreshes every feed and shows the cache newest first`() = runTest {
        viewModel().uiState.test {
            val state = awaitLoaded()
            assertEquals(TestNews.ALL, state.items)
            assertEquals(TestNews.video(), state.liveItem)
            assertEquals(TestNews.NOW, state.now)
        }
        assertEquals(NewsFeed.entries.toSet(), repository.refreshedFeeds.toSet())
    }

    @Test
    fun `a kind filters the list, and choosing it again shows everything`() = runTest {
        val viewModel = viewModel()
        viewModel.uiState.test {
            awaitLoaded()

            viewModel.onKindSelected(NewsKind.IMAGE_OF_THE_DAY)
            assertEquals(listOf(TestNews.imageOfTheDay()), awaitItem().items)
            assertEquals(NewsKind.IMAGE_OF_THE_DAY, savedState.get<String>("kind")?.let(NewsKind::valueOf))

            viewModel.onKindSelected(NewsKind.IMAGE_OF_THE_DAY)
            val all = awaitItem()
            assertNull(all.selectedKind)
            assertEquals(TestNews.ALL.size, all.items.size)
        }
    }

    @Test
    fun `pull-to-refresh asks again even when fresh, and reports a total failure`() = runTest {
        val viewModel = viewModel()
        viewModel.uiState.test {
            awaitLoaded()
            repository.refreshResults = NewsFeed.entries.associateWith { DataResult.Failure(AppError.NoConnection) }

            viewModel.onRefresh()

            var state = awaitItem()
            while (state.isRefreshing || state.error == null) state = awaitItem()
            assertNotNull(state.error)
            viewModel.onErrorShown()
            assertNull(awaitItem().error)
        }
        assertEquals(2 * NewsFeed.entries.size, repository.refreshedFeeds.size)
    }
}

private suspend fun ReceiveTurbine<NewsUiState>.awaitLoaded(): NewsUiState {
    var state = awaitItem()
    while (!state.isLoaded || state.isRefreshing) state = awaitItem()
    return state
}

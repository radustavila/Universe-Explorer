package com.radustavila.nasaexplorer.feature.news.podcast

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.radustavila.nasaexplorer.core.common.error.AppError
import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveDataFreshnessUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ObservePodcastEpisodesUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.RefreshPodcastsUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.RefreshSourceUseCase
import com.radustavila.nasaexplorer.core.model.PodcastShow
import com.radustavila.nasaexplorer.core.testing.data.TestPodcasts
import com.radustavila.nasaexplorer.core.testing.fake.FakeNetworkMonitor
import com.radustavila.nasaexplorer.core.testing.fake.FakePodcastPlayer
import com.radustavila.nasaexplorer.core.testing.fake.FakePodcastRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeSyncMetadataRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeTimeProvider
import com.radustavila.nasaexplorer.core.testing.rule.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class PodcastsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val episodes = listOf(TestPodcasts.curiousUniverse(), TestPodcasts.houston(), TestPodcasts.smallSteps())
    private val repository = FakePodcastRepository(episodes)
    private val metadata = FakeSyncMetadataRepository()
    private val clock = FakeTimeProvider(currentInstant = TestPodcasts.NOW)
    private val player = FakePodcastPlayer()
    private val savedState = SavedStateHandle()

    private fun viewModel() = PodcastsViewModel(
        savedStateHandle = savedState,
        observeEpisodes = ObservePodcastEpisodesUseCase(repository),
        refreshPodcasts = RefreshPodcastsUseCase(repository, RefreshSourceUseCase(metadata, clock)),
        observeDataFreshness = ObserveDataFreshnessUseCase(metadata, FakeNetworkMonitor()),
        player = player,
        timeProvider = clock,
    )

    @Test
    fun `opening the tab refreshes every show and lists the cache newest first`() = runTest {
        viewModel().uiState.test {
            val state = awaitLoaded()
            assertEquals(episodes, state.episodes)
            assertTrue(state.hasEpisodes)
            assertEquals(TestPodcasts.NOW, state.now)
        }
        assertEquals(PodcastShow.entries.toSet(), repository.refreshedShows.toSet())
    }

    @Test
    fun `a show filters the list, and choosing it again shows every show`() = runTest {
        val viewModel = viewModel()
        viewModel.uiState.test {
            awaitLoaded()

            viewModel.onShowSelected(PodcastShow.HOUSTON_WE_HAVE_A_PODCAST)
            assertEquals(listOf(TestPodcasts.houston()), awaitItem().episodes)

            viewModel.onShowSelected(PodcastShow.HOUSTON_WE_HAVE_A_PODCAST)
            val all = awaitItem()
            assertNull(all.selectedShow)
            assertEquals(episodes.size, all.episodes.size)
        }
    }

    @Test
    fun `the play button plays an episode and pauses the one playing`() = runTest {
        val viewModel = viewModel()
        viewModel.uiState.test {
            awaitLoaded()
            val houston = TestPodcasts.houston()

            viewModel.onPlayClick(houston)
            var state = awaitItem()
            assertTrue(state.isPlaying(houston))
            assertFalse(state.isPlaying(TestPodcasts.curiousUniverse()))

            viewModel.onPlayClick(houston)
            state = awaitItem()
            assertFalse(state.isPlaying(houston))
            // Still loaded: the row keeps showing where the player is.
            assertNotNull(state.positionIn(houston))
        }
        assertEquals(listOf("play:${TestPodcasts.houston().id}", "pause"), player.calls)
    }

    @Test
    fun `pull-to-refresh asks again even when fresh, and reports a total failure`() = runTest {
        val viewModel = viewModel()
        viewModel.uiState.test {
            awaitLoaded()
            repository.refreshResults = PodcastShow.entries.associateWith { DataResult.Failure(AppError.NoConnection) }

            viewModel.onRefresh()

            var state = awaitItem()
            while (state.isRefreshing || state.error == null) state = awaitItem()
            viewModel.onErrorShown()
            assertNull(awaitItem().error)
        }
        assertEquals(2 * PodcastShow.entries.size, repository.refreshedShows.size)
    }
}

private suspend fun ReceiveTurbine<PodcastsUiState>.awaitLoaded(): PodcastsUiState {
    var state = awaitItem()
    while (!state.isLoaded || state.isRefreshing) state = awaitItem()
    return state
}

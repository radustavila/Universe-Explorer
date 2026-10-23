package com.radustavila.nasaexplorer.feature.news.podcast.episode

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveIsFavoriteUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ObservePodcastEpisodeUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.RemoveFavoriteUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.SaveFavoriteUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.SavePodcastEpisodeFavoriteUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.TogglePodcastDownloadUseCase
import com.radustavila.nasaexplorer.core.model.FavoriteSource
import com.radustavila.nasaexplorer.core.model.PodcastDownloadState
import com.radustavila.nasaexplorer.core.model.PodcastPlayback
import com.radustavila.nasaexplorer.core.model.PodcastPlaybackError
import com.radustavila.nasaexplorer.core.testing.data.TestPodcasts
import com.radustavila.nasaexplorer.core.testing.fake.FakeFavoritesRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakePodcastPlayer
import com.radustavila.nasaexplorer.core.testing.fake.FakePodcastRepository
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

/** Robolectric only because `SavedStateHandle.toRoute()` decodes the route through a Bundle. */
@RunWith(RobolectricTestRunner::class)
class PodcastEpisodeViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val episode = TestPodcasts.houston()
    private val repository = FakePodcastRepository(listOf(episode))
    private val favorites = FakeFavoritesRepository()
    private val player = FakePodcastPlayer()
    private val clock = FakeTimeProvider(currentInstant = TestPodcasts.NOW)

    private fun viewModel(id: String = episode.id) = PodcastEpisodeViewModel(
        savedStateHandle = SavedStateHandle(mapOf("id" to id)),
        observeEpisode = ObservePodcastEpisodeUseCase(repository),
        observeIsFavorite = ObserveIsFavoriteUseCase(favorites),
        saveFavorite = SavePodcastEpisodeFavoriteUseCase(SaveFavoriteUseCase(favorites, clock)),
        removeFavorite = RemoveFavoriteUseCase(favorites),
        toggleDownload = TogglePodcastDownloadUseCase(repository),
        player = player,
        timeProvider = clock,
    )

    @Test
    fun `an episode plays, pauses, downloads and is saved`() = runTest {
        val viewModel = viewModel()
        viewModel.uiState.test {
            assertFalse(awaitContent().isLoaded)

            viewModel.onPlayClick()
            assertTrue(awaitContent().isPlaying)
            viewModel.onPlayClick()
            assertFalse(awaitContent().isPlaying)

            viewModel.onToggleDownload()
            assertEquals(PodcastDownloadState.DOWNLOADED, awaitContent().episode.download?.state)

            viewModel.onToggleFavorite()
            assertTrue(awaitContent().isFavorite)
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(FavoriteSource.NASA_PODCAST_EPISODE, favorites.current.single().source)
        assertEquals(listOf("play:${episode.id}", "pause"), player.calls)
    }

    @Test
    fun `the player's error shows here and is dismissed through the player`() = runTest {
        player.setPlayback(PodcastPlayback(error = PodcastPlaybackError.OFFLINE_NOT_DOWNLOADED))
        val viewModel = viewModel()
        viewModel.uiState.test {
            assertEquals(PodcastPlaybackError.OFFLINE_NOT_DOWNLOADED, awaitContent().playback.error)

            viewModel.onPlaybackErrorDismissed()
            assertNull(awaitContent().playback.error)
        }
    }

    @Test
    fun `an episode no longer cached is unavailable`() = runTest {
        viewModel(id = "gone").uiState.test {
            var state = awaitItem()
            while (state == PodcastEpisodeUiState.Loading) state = awaitItem()
            assertEquals(PodcastEpisodeUiState.Unavailable, state)
        }
    }
}

private suspend fun ReceiveTurbine<PodcastEpisodeUiState>.awaitContent(): PodcastEpisodeUiState.Content {
    var state = awaitItem()
    while (state !is PodcastEpisodeUiState.Content) state = awaitItem()
    return state
}

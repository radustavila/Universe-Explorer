package com.radustavila.nasaexplorer.feature.universe.details

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.radustavila.nasaexplorer.core.common.error.AppError
import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.domain.usecase.FetchNasaMediaUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveConnectivityUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveIsFavoriteUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveNasaMediaUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.RemoveFavoriteUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ResolveNasaMediaPlaybackUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.SaveFavoriteUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.SaveNasaMediaFavoriteUseCase
import com.radustavila.nasaexplorer.core.model.FavoriteSource
import com.radustavila.nasaexplorer.core.model.GalleryRail
import com.radustavila.nasaexplorer.core.model.NasaMedia
import com.radustavila.nasaexplorer.core.testing.data.TestNasaMedia
import com.radustavila.nasaexplorer.core.testing.fake.FakeFavoritesRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeNasaMediaRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeNetworkMonitor
import com.radustavila.nasaexplorer.core.testing.fake.FakeTimeProvider
import com.radustavila.nasaexplorer.core.testing.rule.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Robolectric only because `SavedStateHandle.toRoute()` decodes the route through a Bundle. */
@RunWith(RobolectricTestRunner::class)
class NasaMediaDetailsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeNasaMediaRepository()
    private val favorites = FakeFavoritesRepository()
    private val networkMonitor = FakeNetworkMonitor()
    private val clock = FakeTimeProvider()

    private val image = TestNasaMedia.image()
    private val video = TestNasaMedia.video()

    private fun createViewModel(nasaId: String) = NasaMediaDetailsViewModel(
        savedStateHandle = SavedStateHandle(mapOf("nasaId" to nasaId)),
        observeMedia = ObserveNasaMediaUseCase(repository),
        observeIsFavorite = ObserveIsFavoriteUseCase(favorites),
        observeConnectivity = ObserveConnectivityUseCase(networkMonitor),
        fetchMedia = FetchNasaMediaUseCase(repository),
        resolvePlayback = ResolveNasaMediaPlaybackUseCase(repository),
        saveFavorite = SaveNasaMediaFavoriteUseCase(repository, SaveFavoriteUseCase(favorites, clock)),
        removeFavorite = RemoveFavoriteUseCase(favorites),
    )

    private suspend fun app.cash.turbine.ReceiveTurbine<NasaMediaDetailsUiState>.awaitContent():
        NasaMediaDetailsUiState.Content {
        var state = awaitItem()
        while (state !is NasaMediaDetailsUiState.Content) state = awaitItem()
        return state
    }

    @Test
    fun `a rail item opens from the cache without a download`() = runTest {
        repository.setRails(mapOf(GalleryRail.WEBB to listOf(image)))

        createViewModel(image.nasaId).uiState.test {
            val state = awaitContent()
            assertEquals(image, state.media)
            assertEquals(false, state.isFavorite)
            assertEquals(PlaybackState.Idle, state.playback)
        }
    }

    @Test
    fun `an item nobody has is downloaded by id`() = runTest {
        repository.fetchResult = DataResult.Success(image)

        createViewModel(image.nasaId).uiState.test {
            assertEquals(image, awaitContent().media)
        }
    }

    @Test
    fun `an item that is not kept is unavailable offline, and arrives when back online`() = runTest {
        networkMonitor.setOnline(false)

        createViewModel(image.nasaId).uiState.test {
            var state = awaitItem()
            while (state !is NasaMediaDetailsUiState.Unavailable) state = awaitItem()
            assertTrue(state.isOffline)

            repository.fetchResult = DataResult.Success(image)
            networkMonitor.setOnline(true)

            assertEquals(image, awaitContent().media)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `saving keeps the item and marks it, removing unmarks it`() = runTest {
        repository.setRails(mapOf(GalleryRail.WEBB to listOf(image)))
        val viewModel = createViewModel(image.nasaId)

        viewModel.uiState.test {
            awaitContent()

            viewModel.onToggleFavorite()
            var state = awaitContent()
            while (!state.isFavorite) state = awaitContent()
            assertEquals(image, repository.kept[image.nasaId])
            assertEquals(FavoriteSource.NASA_LIBRARY, favorites.current.single().source)

            viewModel.onToggleFavorite()
            while (state.isFavorite) state = awaitContent()
            assertTrue(favorites.current.isEmpty())
        }
    }

    @Test
    fun `a failed save surfaces an error`() = runTest {
        repository.setRails(mapOf(GalleryRail.WEBB to listOf(image)))
        repository.keepResult = DataResult.Failure(AppError.Database("full"))
        val viewModel = createViewModel(image.nasaId)

        viewModel.uiState.test {
            awaitContent()
            viewModel.onToggleFavorite()

            var state = awaitContent()
            while (state.error == null) state = awaitContent()
            assertEquals(false, state.isFavorite)
        }
    }

    @Test
    fun `a video resolves its stream when it opens`() = runTest {
        repository.store(video)
        repository.playbackResult = DataResult.Success("https://example.org/video~medium.mp4")

        createViewModel(video.nasaId).uiState.test {
            var state = awaitContent()
            while (state.playback !is PlaybackState.Ready) state = awaitContent()

            assertEquals(PlaybackState.Ready("https://example.org/video~medium.mp4"), state.playback)
        }
    }

    @Test
    fun `offline playback waits for the connection`() = runTest {
        repository.store(video)
        networkMonitor.setOnline(false)
        repository.playbackResult = DataResult.Failure(AppError.NoConnection)

        createViewModel(video.nasaId).uiState.test {
            var state = awaitContent()
            while (state.playback != PlaybackState.NeedsConnection) state = awaitContent()

            repository.playbackResult = DataResult.Success("https://example.org/v.mp4")
            networkMonitor.setOnline(true)

            while (state.playback !is PlaybackState.Ready) state = awaitContent()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `an item with nothing to play fails without a retry loop`() = runTest {
        val audio: NasaMedia = TestNasaMedia.audio()
        repository.store(audio)
        repository.playbackResult = DataResult.Failure(AppError.EmptyResponse)

        createViewModel(audio.nasaId).uiState.test {
            var state = awaitContent()
            while (state.playback !is PlaybackState.Failed) state = awaitContent()

            assertEquals(false, (state.playback as PlaybackState.Failed).error.isRetryable)
        }
    }
}

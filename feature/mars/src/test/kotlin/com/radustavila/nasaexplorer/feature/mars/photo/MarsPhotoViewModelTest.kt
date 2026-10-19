package com.radustavila.nasaexplorer.feature.mars.photo

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveIsFavoriteUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveMarsPhotoUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.RemoveFavoriteUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.SaveFavoriteUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.SaveMarsPhotoFavoriteUseCase
import com.radustavila.nasaexplorer.core.model.FavoriteSource
import com.radustavila.nasaexplorer.core.testing.data.TestMarsPhotos
import com.radustavila.nasaexplorer.core.testing.fake.FakeFavoritesRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeMarsPhotoRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeTimeProvider
import com.radustavila.nasaexplorer.core.testing.rule.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Robolectric only because `SavedStateHandle.toRoute()` decodes the route through a Bundle. */
@RunWith(RobolectricTestRunner::class)
class MarsPhotoViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val photo = TestMarsPhotos.curiosity()
    private val repository = FakeMarsPhotoRepository(listOf(photo))
    private val favorites = FakeFavoritesRepository()

    private fun createViewModel(id: String = photo.id) = MarsPhotoViewModel(
        savedStateHandle = SavedStateHandle(mapOf("id" to id)),
        observePhoto = ObserveMarsPhotoUseCase(repository),
        observeIsFavorite = ObserveIsFavoriteUseCase(favorites),
        saveFavorite = SaveMarsPhotoFavoriteUseCase(SaveFavoriteUseCase(favorites, FakeTimeProvider())),
        removeFavorite = RemoveFavoriteUseCase(favorites),
    )

    private suspend fun ReceiveTurbine<MarsPhotoUiState>.awaitSettled(): MarsPhotoUiState {
        var state = awaitItem()
        while (state == MarsPhotoUiState.Loading) state = awaitItem()
        return state
    }

    @Test
    fun `the heart saves and removes the photo under the Mars source`() = runTest {
        val viewModel = createViewModel()

        viewModel.uiState.test {
            assertFalse((awaitSettled() as MarsPhotoUiState.Content).isFavorite)

            viewModel.onToggleFavorite()
            assertTrue((awaitItem() as MarsPhotoUiState.Content).isFavorite)
            assertEquals(FavoriteSource.MARS, favorites.current.single().source)
            assertEquals(photo.id, favorites.current.single().itemId)

            viewModel.onToggleFavorite()
            assertFalse((awaitItem() as MarsPhotoUiState.Content).isFavorite)
            assertTrue(favorites.current.isEmpty())
        }
    }

    @Test
    fun `a photo that is not cached is unavailable`() = runTest {
        createViewModel(id = "gone").uiState.test {
            assertEquals(MarsPhotoUiState.Unavailable, awaitSettled())
        }
    }
}

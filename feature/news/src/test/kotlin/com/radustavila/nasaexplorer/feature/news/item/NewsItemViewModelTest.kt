package com.radustavila.nasaexplorer.feature.news.item

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveIsFavoriteUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveNewsItemUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.RemoveFavoriteUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.SaveFavoriteUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.SaveNewsImageFavoriteUseCase
import com.radustavila.nasaexplorer.core.model.FavoriteSource
import com.radustavila.nasaexplorer.core.testing.data.TestNews
import com.radustavila.nasaexplorer.core.testing.fake.FakeFavoritesRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeNewsRepository
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
class NewsItemViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val favorites = FakeFavoritesRepository()
    private val clock = FakeTimeProvider(currentInstant = TestNews.NOW)

    private fun viewModel(id: String) = NewsItemViewModel(
        savedStateHandle = SavedStateHandle(mapOf("id" to id)),
        observeItem = ObserveNewsItemUseCase(FakeNewsRepository(TestNews.ALL)),
        observeIsFavorite = ObserveIsFavoriteUseCase(favorites),
        saveFavorite = SaveNewsImageFavoriteUseCase(SaveFavoriteUseCase(favorites, clock)),
        removeFavorite = RemoveFavoriteUseCase(favorites),
        timeProvider = clock,
    )

    @Test
    fun `an Image of the Day can be saved and removed`() = runTest {
        val image = TestNews.imageOfTheDay()
        val viewModel = viewModel(image.id)
        viewModel.uiState.test {
            var state = awaitItem()
            while (state !is NewsItemUiState.Content) state = awaitItem()
            assertEquals(image, state.item)
            assertTrue(state.canBeSaved)
            assertFalse(state.isFavorite)

            viewModel.onToggleFavorite()
            assertTrue((awaitItem() as NewsItemUiState.Content).isFavorite)
            assertEquals(image.fullImageUrl, favorites.current.single().imageUrl)
            assertEquals(FavoriteSource.NASA_IMAGE_OF_THE_DAY, favorites.current.single().source)

            viewModel.onToggleFavorite()
            assertFalse((awaitItem() as NewsItemUiState.Content).isFavorite)
        }
    }

    @Test
    fun `a story cannot be saved`() = runTest {
        val viewModel = viewModel(TestNews.story().id)
        viewModel.uiState.test {
            var state = awaitItem()
            while (state !is NewsItemUiState.Content) state = awaitItem()
            assertFalse(state.canBeSaved)
            viewModel.onToggleFavorite()
            expectNoEvents()
        }
        assertTrue(favorites.current.isEmpty())
    }

    @Test
    fun `an item no longer cached is unavailable`() = runTest {
        viewModel("https://www.nasa.gov/gone/").uiState.test {
            var state = awaitItem()
            while (state == NewsItemUiState.Loading) state = awaitItem()
            assertEquals(NewsItemUiState.Unavailable, state)
        }
    }
}

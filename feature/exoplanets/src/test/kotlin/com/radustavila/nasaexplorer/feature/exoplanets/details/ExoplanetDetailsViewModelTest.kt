package com.radustavila.nasaexplorer.feature.exoplanets.details

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveConnectivityUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveExoplanetUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveIsFavoriteUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.RemoveFavoriteUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.SaveFavoriteUseCase
import com.radustavila.nasaexplorer.core.model.FavoriteItem
import com.radustavila.nasaexplorer.core.model.FavoriteSource
import com.radustavila.nasaexplorer.core.testing.data.TestExoplanets
import com.radustavila.nasaexplorer.core.testing.fake.FakeExoplanetRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeFavoritesRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeNetworkMonitor
import com.radustavila.nasaexplorer.core.testing.fake.FakeTimeProvider
import com.radustavila.nasaexplorer.core.testing.rule.MainDispatcherRule
import kotlinx.coroutines.flow.first
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
class ExoplanetDetailsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val planet = TestExoplanets.proximaB
    private val repository = FakeExoplanetRepository(initialPlanets = listOf(planet))
    private val favorites = FakeFavoritesRepository()
    private val networkMonitor = FakeNetworkMonitor()
    private val clock = FakeTimeProvider()

    private fun createViewModel(name: String = planet.name) = ExoplanetDetailsViewModel(
        savedStateHandle = SavedStateHandle(mapOf("name" to name)),
        observeExoplanet = ObserveExoplanetUseCase(repository),
        observeIsFavorite = ObserveIsFavoriteUseCase(favorites),
        observeConnectivity = ObserveConnectivityUseCase(networkMonitor),
        saveFavorite = SaveFavoriteUseCase(favorites, clock),
        removeFavorite = RemoveFavoriteUseCase(favorites),
    )

    @Test
    fun `renders the cached planet`() = runTest {
        createViewModel().uiState.test {
            var state = awaitItem()
            while (state !is ExoplanetDetailsUiState.Content) state = awaitItem()

            assertEquals(planet, state.planet)
            assertFalse(state.isFavorite)
        }
    }

    @Test
    fun `a planet missing from the cache is not found`() = runTest {
        createViewModel(name = "Vulcan").uiState.test {
            var state = awaitItem()
            while (state == ExoplanetDetailsUiState.Loading) state = awaitItem()

            assertEquals(ExoplanetDetailsUiState.NotFound("Vulcan"), state)
        }
    }

    @Test
    fun `toggling saves an exoplanet favourite with its name, then removes it`() = runTest {
        val viewModel = createViewModel()

        viewModel.uiState.test {
            var state = awaitItem()
            while (state !is ExoplanetDetailsUiState.Content) state = awaitItem()

            viewModel.onToggleFavorite()
            while (!(state as ExoplanetDetailsUiState.Content).isFavorite) state = awaitItem()
            assertEquals(
                listOf(FavoriteItem(FavoriteSource.EXOPLANET, planet.name, planet.name, null, clock.now())),
                favorites.observeFavorites().first(),
            )

            viewModel.onToggleFavorite()
            while ((state as ExoplanetDetailsUiState.Content).isFavorite) state = awaitItem()
            assertTrue(favorites.observeFavorites().first().isEmpty())
        }
    }
}

package com.radustavila.nasaexplorer.feature.favorites

import app.cash.turbine.test
import com.radustavila.nasaexplorer.core.common.error.AppError
import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.designsystem.R
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveFavoritesUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.RemoveFavoriteUseCase
import com.radustavila.nasaexplorer.core.model.FavoriteItem
import com.radustavila.nasaexplorer.core.model.FavoriteSource
import com.radustavila.nasaexplorer.core.testing.fake.FakeFavoritesRepository
import com.radustavila.nasaexplorer.core.testing.rule.MainDispatcherRule
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class FavoritesViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val apod = FavoriteItem(
        source = FavoriteSource.APOD,
        itemId = "2026-09-10",
        title = "Spiral Galaxy NGC 1300",
        imageUrl = "https://apod.nasa.gov/apod/image/ngc1300.jpg",
        savedAt = Instant.parse("2026-09-10T10:00:00Z"),
    )
    private val earth = FavoriteItem(
        source = FavoriteSource.EPIC,
        itemId = "epic_1b_20261018003633",
        title = "Earth, 18 Oct 2026",
        imageUrl = null,
        savedAt = Instant.parse("2026-10-18T10:00:00Z"),
    )

    private val repository = FakeFavoritesRepository(listOf(apod, earth))

    @Test
    fun `shows favourites of every source, most recently saved first`() = runTest {
        createViewModel().uiState.test {
            var state = awaitItem()
            while (state.isLoading) state = awaitItem()

            assertEquals(listOf(earth, apod), state.items)
        }
    }

    @Test
    fun `removing a favourite takes it off the list`() = runTest {
        val viewModel = createViewModel()

        viewModel.uiState.test {
            var state = awaitItem()
            while (state.items.size != 2) state = awaitItem()

            viewModel.onRemoveFavorite(earth)

            state = awaitItem()
            while (state.items.size != 1) state = awaitItem()
            assertEquals(listOf(apod), state.items)
        }
    }

    @Test
    fun `a failed removal surfaces an error`() = runTest {
        repository.writeResult = DataResult.Failure(AppError.Database("locked"))
        val viewModel = createViewModel()

        viewModel.uiState.test {
            var state = awaitItem()
            while (state.items.isEmpty()) state = awaitItem()

            viewModel.onRemoveFavorite(apod)

            state = awaitItem()
            while (state.error == null) state = awaitItem()
            assertEquals(R.string.error_database, state.error?.messageRes)
            assertTrue(state.items.contains(apod))
        }
    }

    private fun createViewModel() = FavoritesViewModel(
        observeFavorites = ObserveFavoritesUseCase(repository),
        removeFavorite = RemoveFavoriteUseCase(repository),
    )
}

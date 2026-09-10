package com.radustavila.nasaexplorer.feature.favorites

import app.cash.turbine.test
import com.radustavila.nasaexplorer.core.common.error.AppError
import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.designsystem.R
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveFavoriteApodsUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ToggleFavoriteUseCase
import com.radustavila.nasaexplorer.core.testing.data.TestApods
import com.radustavila.nasaexplorer.core.testing.fake.FakeApodRepository
import com.radustavila.nasaexplorer.core.testing.rule.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class FavoritesViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeApodRepository()

    @Test
    fun `shows only favorited entries and works without any network involvement`() = runTest {
        repository.setEntries(
            listOf(
                TestApods.image(isFavorite = true),
                TestApods.video(isFavorite = false),
            ),
        )

        createViewModel().uiState.test {
            var state = awaitItem()
            while (state.isLoading) state = awaitItem()

            assertEquals(1, state.items.size)
            assertTrue(state.items.single().isFavorite)
            assertEquals(0, repository.refreshCallCount)
        }
    }

    @Test
    fun `un-favoriting removes the entry from the list`() = runTest {
        val apod = TestApods.image(isFavorite = true)
        repository.setEntries(listOf(apod))
        val viewModel = createViewModel()

        viewModel.uiState.test {
            var state = awaitItem()
            while (state.items.isEmpty()) state = awaitItem()

            viewModel.onToggleFavorite(state.items.single())

            state = awaitItem()
            while (state.items.isNotEmpty()) state = awaitItem()
            assertTrue(state.isEmpty)
        }
    }

    @Test
    fun `a failed toggle surfaces an error`() = runTest {
        val apod = TestApods.image(isFavorite = true)
        repository.setEntries(listOf(apod))
        repository.setFavoriteResult = DataResult.Failure(AppError.Database("locked"))
        val viewModel = createViewModel()

        viewModel.uiState.test {
            var state = awaitItem()
            while (state.items.isEmpty()) state = awaitItem()

            viewModel.onToggleFavorite(apod)

            state = awaitItem()
            while (state.error == null) state = awaitItem()
            assertEquals(R.string.error_database, state.error?.messageRes)
        }
    }

    private fun createViewModel() = FavoritesViewModel(
        observeFavorites = ObserveFavoriteApodsUseCase(repository),
        toggleFavorite = ToggleFavoriteUseCase(repository),
    )
}

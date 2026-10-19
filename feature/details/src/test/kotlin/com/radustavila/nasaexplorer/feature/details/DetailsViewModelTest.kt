package com.radustavila.nasaexplorer.feature.details

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.radustavila.nasaexplorer.core.common.error.AppError
import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.domain.usecase.FetchApodUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveApodUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveConnectivityUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ToggleFavoriteUseCase
import com.radustavila.nasaexplorer.core.testing.data.TestApods
import com.radustavila.nasaexplorer.core.testing.fake.FakeApodRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeTimeProvider
import com.radustavila.nasaexplorer.core.testing.fake.FakeNetworkMonitor
import com.radustavila.nasaexplorer.core.testing.rule.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Robolectric is required here only because `SavedStateHandle.toRoute()` decodes a navigation route
 * through Android's Bundle machinery.
 */
@RunWith(RobolectricTestRunner::class)
class DetailsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeApodRepository()
    private val networkMonitor = FakeNetworkMonitor()

    private val apod = TestApods.image()

    @Test
    fun `renders the cached entry for the requested date`() = runTest {
        repository.setEntries(listOf(apod))

        createViewModel().uiState.test {
            var state = awaitItem()
            while (state !is DetailsUiState.Content) state = awaitItem()

            assertEquals(apod.date, state.apod.date)
            assertEquals(false, state.isOffline)
        }
    }

    @Test
    fun `a cached entry is not downloaded again`() = runTest {
        repository.setEntries(listOf(apod))

        createViewModel().uiState.test {
            var state = awaitItem()
            while (state !is DetailsUiState.Content) state = awaitItem()
        }

        assertEquals(emptyList<Any>(), repository.fetchedDates)
    }

    @Test
    fun `an entry the feed has not cached is downloaded on arrival`() = runTest {
        repository.fetchApodEntry = apod

        createViewModel().uiState.test {
            var state = awaitItem()
            while (state !is DetailsUiState.Content) state = awaitItem()

            assertEquals(apod.date, state.apod.date)
        }
        assertEquals(listOf(apod.date), repository.fetchedDates)
    }

    @Test
    fun `an entry that is not cached while offline is reported as unavailable offline`() = runTest {
        networkMonitor.setOnline(false)

        createViewModel().uiState.test {
            var state = awaitItem()
            while (state !is DetailsUiState.Unavailable) state = awaitItem()

            assertTrue(state.isOffline)
        }
    }

    @Test
    fun `a failed download is reported as unavailable with the error`() = runTest {
        repository.fetchApodResult = DataResult.Failure(AppError.Http(code = 404))

        createViewModel().uiState.test {
            var state = awaitItem()
            while (state !is DetailsUiState.Unavailable) state = awaitItem()

            assertTrue(state.error != null)
        }
    }

    @Test
    fun `toggling the favorite is reflected by the database flow`() = runTest {
        repository.setEntries(listOf(apod))
        val viewModel = createViewModel()

        viewModel.uiState.test {
            var state = awaitItem()
            while (state !is DetailsUiState.Content) state = awaitItem()

            viewModel.onToggleFavorite()

            var updated = awaitItem()
            while ((updated as? DetailsUiState.Content)?.apod?.isFavorite != true) {
                updated = awaitItem()
            }
            assertTrue((updated as DetailsUiState.Content).apod.isFavorite)
        }
    }

    @Test
    fun `retry downloads the requested day again`() = runTest {
        repository.fetchApodResult = DataResult.Failure(AppError.Http(code = 500))
        val viewModel = createViewModel()

        viewModel.uiState.test {
            var state = awaitItem()
            while (state !is DetailsUiState.Unavailable) state = awaitItem()
        }

        viewModel.onRetry()

        assertEquals(listOf(apod.date, apod.date), repository.fetchedDates)
    }

    @Test
    fun `a malformed date from a deep link is reported as unavailable without a download`() = runTest {
        val viewModel = createViewModel(date = "not-a-date")

        viewModel.uiState.test {
            val state = awaitItem()
            assertTrue(state is DetailsUiState.Unavailable)
            assertEquals(false, (state as DetailsUiState.Unavailable).isOffline)
        }

        viewModel.onRetry()

        assertEquals(emptyList<Any>(), repository.fetchedDates)
    }

    private fun createViewModel(date: String = apod.date.toString()) = DetailsViewModel(
        savedStateHandle = SavedStateHandle(mapOf("date" to date)),
        observeApod = ObserveApodUseCase(repository),
        observeConnectivity = ObserveConnectivityUseCase(networkMonitor),
        toggleFavorite = ToggleFavoriteUseCase(repository.favorites, FakeTimeProvider()),
        fetchApod = FetchApodUseCase(repository, networkMonitor),
    )
}

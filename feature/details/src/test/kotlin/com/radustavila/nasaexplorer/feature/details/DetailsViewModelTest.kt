package com.radustavila.nasaexplorer.feature.details

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveApodUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveConnectivityUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.RefreshFeedUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ToggleFavoriteUseCase
import com.radustavila.nasaexplorer.core.testing.data.TestApods
import com.radustavila.nasaexplorer.core.testing.fake.FakeApodRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeNetworkMonitor
import com.radustavila.nasaexplorer.core.testing.fake.FakeSyncScheduler
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
    private val syncScheduler = FakeSyncScheduler()

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
    fun `an entry that is not cached while offline is reported as unavailable offline`() = runTest {
        networkMonitor.setOnline(false)

        createViewModel().uiState.test {
            var state = awaitItem()
            while (state !is DetailsUiState.Unavailable) state = awaitItem()

            assertTrue(state.isOffline)
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
    fun `retry goes through the sync pipeline`() = runTest {
        createViewModel().onRetry()

        assertEquals(1, syncScheduler.requestSyncCallCount)
    }

    private fun createViewModel() = DetailsViewModel(
        savedStateHandle = SavedStateHandle(mapOf("date" to apod.date.toString())),
        observeApod = ObserveApodUseCase(repository),
        observeConnectivity = ObserveConnectivityUseCase(networkMonitor),
        toggleFavorite = ToggleFavoriteUseCase(repository),
        refreshFeed = RefreshFeedUseCase(syncScheduler),
    )
}

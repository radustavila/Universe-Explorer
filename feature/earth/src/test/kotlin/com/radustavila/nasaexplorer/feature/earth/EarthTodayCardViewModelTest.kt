package com.radustavila.nasaexplorer.feature.earth

import app.cash.turbine.test
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveEpicSyncStateUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveLatestEpicDayUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.RefreshEpicUseCase
import com.radustavila.nasaexplorer.core.model.EpicCollection
import com.radustavila.nasaexplorer.core.model.SyncState
import com.radustavila.nasaexplorer.core.testing.data.TestEpicImages
import com.radustavila.nasaexplorer.core.testing.fake.FakeEpicRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeEpicSyncScheduler
import com.radustavila.nasaexplorer.core.testing.rule.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class EarthTodayCardViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeEpicRepository()
    private val scheduler = FakeEpicSyncScheduler()

    @Test
    fun `shows the newest natural-colour day and asks for a sync`() = runTest {
        val latest = TestEpicImages.day(size = 5)
        repository.setImages(latest + TestEpicImages.day(collection = EpicCollection.ENHANCED))

        createViewModel().uiState.test {
            var state = awaitItem()
            while (state.images.isEmpty()) state = awaitItem()

            assertEquals(latest, state.images)
            assertEquals(latest[2], state.coverImage)
        }
        assertEquals(1, scheduler.requestSyncCallCount)
    }

    @Test
    fun `an empty cache is loading only while a sync is in flight`() = runTest {
        scheduler.setState(SyncState.Syncing)
        val viewModel = createViewModel()

        viewModel.uiState.test {
            var state = awaitItem()
            while (!state.isLoading) state = awaitItem()

            scheduler.setState(SyncState.Failed())
            state = awaitItem()
            assertTrue(!state.isLoading && state.coverImage == null)
        }
    }

    private fun createViewModel() = EarthTodayCardViewModel(
        observeLatestEpicDay = ObserveLatestEpicDayUseCase(repository),
        observeEpicSyncState = ObserveEpicSyncStateUseCase(scheduler),
        refreshEpic = RefreshEpicUseCase(scheduler),
    )
}

package com.radustavila.nasaexplorer.feature.mars.rail

import app.cash.turbine.test
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveLatestMarsPhotosUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveMarsSyncStatusUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.RequestMarsSyncUseCase
import com.radustavila.nasaexplorer.core.testing.data.TestMarsPhotos
import com.radustavila.nasaexplorer.core.testing.fake.FakeMarsPhotoRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeMarsSyncScheduler
import com.radustavila.nasaexplorer.core.testing.fake.FakeSyncMetadataRepository
import com.radustavila.nasaexplorer.core.testing.rule.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class MarsRailViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeMarsPhotoRepository()
    private val scheduler = FakeMarsSyncScheduler()
    private val metadata = FakeSyncMetadataRepository()

    private fun createViewModel() = MarsRailViewModel(
        observeLatest = ObserveLatestMarsPhotosUseCase(repository),
        observeSyncStatus = ObserveMarsSyncStatusUseCase(scheduler, metadata),
        requestSync = RequestMarsSyncUseCase(scheduler),
    )

    @Test
    fun `appearing asks the background job for an unforced refresh`() {
        createViewModel()

        assertEquals(listOf(false), scheduler.requests)
        assertEquals(1, scheduler.periodicSyncCallCount)
    }

    @Test
    fun `cached photos show without waiting for the network`() = runTest {
        repository.setPhotos(listOf(TestMarsPhotos.curiosity(), TestMarsPhotos.perseverance()))
        scheduler.setSyncing(true)

        createViewModel().uiState.test {
            var state = awaitItem()
            if (!state.isLoaded) state = awaitItem()
            assertEquals(2, state.photos.size)
            assertTrue(state.isSyncing)
        }
    }

    @Test
    fun `photos a sync brings in appear in the rail`() = runTest {
        createViewModel().uiState.test {
            var state = awaitItem()
            if (!state.isLoaded) state = awaitItem()
            assertTrue(state.photos.isEmpty())
            assertFalse(state.isSyncing)

            repository.setPhotos(listOf(TestMarsPhotos.curiosity()))
            assertEquals(1, awaitItem().photos.size)
        }
    }
}

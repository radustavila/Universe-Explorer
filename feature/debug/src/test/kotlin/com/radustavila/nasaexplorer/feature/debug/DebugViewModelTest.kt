package com.radustavila.nasaexplorer.feature.debug

import app.cash.turbine.test
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveDebugInfoUseCase
import com.radustavila.nasaexplorer.core.model.SyncState
import com.radustavila.nasaexplorer.core.testing.data.TestApods
import com.radustavila.nasaexplorer.core.testing.fake.FakeApodRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeNetworkMonitor
import com.radustavila.nasaexplorer.core.testing.fake.FakeSyncMetadataRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeSyncScheduler
import com.radustavila.nasaexplorer.core.testing.rule.MainDispatcherRule
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class DebugViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeApodRepository()
    private val networkMonitor = FakeNetworkMonitor()
    private val syncScheduler = FakeSyncScheduler()
    private val metadata = FakeSyncMetadataRepository(initialLastSync = LAST_SYNC)

    @Test
    fun `aggregates connectivity, cache size, favorites and sync state`() = runTest {
        repository.setEntries(
            TestApods.feed(size = 3) + TestApods.video(isFavorite = true),
        )
        syncScheduler.setState(SyncState.Syncing)

        createViewModel().uiState.test {
            var state = awaitItem()
            while (state.info == null) state = awaitItem()

            val info = requireNotNull(state.info)
            assertEquals(true, info.isOnline)
            assertEquals(4, info.cachedApodCount)
            assertEquals(1, info.favoriteCount)
            assertEquals(LAST_SYNC, info.lastSuccessfulSync)
            assertEquals(SyncState.Syncing, info.syncState)
        }
    }

    @Test
    fun `sync actions are delegated to the scheduler`() = runTest {
        val viewModel = createViewModel()

        viewModel.onSyncNow()
        viewModel.onEnsurePeriodicSync()

        assertEquals(1, syncScheduler.requestSyncCallCount)
        assertEquals(1, syncScheduler.periodicSyncCallCount)
    }

    private fun createViewModel() = DebugViewModel(
        observeDebugInfo = ObserveDebugInfoUseCase(
            apodRepository = repository,
            syncMetadataRepository = metadata,
            syncScheduler = syncScheduler,
            networkMonitor = networkMonitor,
        ),
        syncScheduler = syncScheduler,
    )

    private companion object {
        val LAST_SYNC: Instant = Instant.parse("2026-09-10T09:30:00Z")
    }
}

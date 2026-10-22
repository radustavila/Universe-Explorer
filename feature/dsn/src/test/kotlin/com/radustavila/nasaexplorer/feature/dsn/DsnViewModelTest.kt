package com.radustavila.nasaexplorer.feature.dsn

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.radustavila.nasaexplorer.core.common.error.AppError
import com.radustavila.nasaexplorer.core.common.time.TimeProvider
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveDataFreshnessUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveDsnSnapshotUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveFarthestProbesUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.PollDeepSpaceNetworkUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.RefreshSourceUseCase
import com.radustavila.nasaexplorer.core.model.SyncSource
import com.radustavila.nasaexplorer.core.testing.data.TestDeepSpaceNetwork
import com.radustavila.nasaexplorer.core.testing.fake.FakeDeepSpaceNetworkRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeNetworkMonitor
import com.radustavila.nasaexplorer.core.testing.fake.FakeSyncMetadataRepository
import com.radustavila.nasaexplorer.core.testing.rule.MainDispatcherRule
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class DsnViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeDeepSpaceNetworkRepository()
    private val metadata = FakeSyncMetadataRepository()
    private val network = FakeNetworkMonitor()

    private fun TestScope.createViewModel(savedStateHandle: SavedStateHandle = SavedStateHandle()): DsnViewModel {
        val clock = object : TimeProvider {
            override fun now(): Instant = START.plusMillis(testScheduler.currentTime)
            override fun today(): LocalDate = now().atZone(ZoneOffset.UTC).toLocalDate()
        }
        return DsnViewModel(
            savedStateHandle = savedStateHandle,
            observeSnapshot = ObserveDsnSnapshotUseCase(repository),
            observeFarthest = ObserveFarthestProbesUseCase(repository),
            observeFreshness = ObserveDataFreshnessUseCase(metadata, network),
            poll = PollDeepSpaceNetworkUseCase(repository, RefreshSourceUseCase(metadata, clock), network),
        )
    }

    @Test
    fun `reads the snapshot and the farthest list from the cache without polling`() = runTest {
        repository.snapshot.value = TestDeepSpaceNetwork.snapshot()
        repository.farthest.value = TestDeepSpaceNetwork.farthest

        createViewModel().uiState.test {
            var state = awaitItem()
            while (!state.isLoaded) state = awaitItem()
            assertEquals(listOf("Goldstone", "Madrid", "Canberra"), state.snapshot!!.sites.map { it.name })
            assertEquals(listOf("Voyager 1", "Voyager 2", "New Horizons"), state.farthest.map { it.name })
            assertEquals(setOf(-32, -170), state.talkingNaifIds)
        }
        // Reading the cache never polls: only the screen's lifecycle-bound call does.
        assertEquals(0, repository.liveRefreshCount)
    }

    @Test
    fun `polls while the screen's coroutine runs, and stops with it`() = runTest {
        val viewModel = createViewModel()

        val job = launch { viewModel.pollWhileVisible() }
        runCurrent()
        testScheduler.advanceTimeBy(PollDeepSpaceNetworkUseCase.POLL_INTERVAL.toMillis())
        runCurrent()
        assertEquals(2, repository.liveRefreshCount)

        job.cancel()
        testScheduler.advanceTimeBy(PollDeepSpaceNetworkUseCase.POLL_INTERVAL.toMillis() * 10)
        runCurrent()
        assertEquals(2, repository.liveRefreshCount)
    }

    @Test
    fun `a failing feed shows as unavailable over the last good state`() = runTest {
        repository.snapshot.value = TestDeepSpaceNetwork.snapshot()
        metadata.recordSyncFailure(SyncSource.DSN_NOW, START, AppError.Timeout, nextAllowedRefreshAt = null)

        createViewModel().uiState.test {
            var state = awaitItem()
            while (!state.isLoaded) state = awaitItem()
            assertTrue(state.freshness.lastRefreshFailed)
            assertEquals(TestDeepSpaceNetwork.snapshot(), state.snapshot)
        }
    }

    @Test
    fun `the open spacecraft follows new snapshots and closes when its dish moves on`() = runTest {
        repository.snapshot.value = TestDeepSpaceNetwork.snapshot()
        val savedStateHandle = SavedStateHandle()
        val viewModel = createViewModel(savedStateHandle)

        viewModel.uiState.test {
            var state = awaitItem()
            while (!state.isLoaded) state = awaitItem()
            viewModel.onSpacecraftClick("DSS34", "JWST")
            assertEquals("JWST", awaitItem().selected!!.link.spacecraft.code)

            val moved = TestDeepSpaceNetwork.canberra.copy(dishes = listOf(TestDeepSpaceNetwork.dish()))
            repository.snapshot.value = TestDeepSpaceNetwork.snapshot(sites = listOf(moved))
            assertNull(awaitItem().selected)

            viewModel.onSheetDismiss()
            cancelAndIgnoreRemainingEvents()
        }
        assertNull(savedStateHandle.get<String>("selected_link"))
    }

    private companion object {
        val START: Instant = Instant.parse("2026-10-22T16:00:00Z")
    }
}

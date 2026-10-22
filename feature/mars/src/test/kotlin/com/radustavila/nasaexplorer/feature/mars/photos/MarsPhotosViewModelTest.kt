package com.radustavila.nasaexplorer.feature.mars.photos

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.radustavila.nasaexplorer.core.common.error.AppError
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveConnectivityUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveMarsRoverPhotosUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveMarsSyncStatusUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.RequestMarsSyncUseCase
import com.radustavila.nasaexplorer.core.model.DataFreshness
import com.radustavila.nasaexplorer.core.model.MarsRover
import com.radustavila.nasaexplorer.core.model.SyncSource
import com.radustavila.nasaexplorer.core.testing.data.TestMarsPhotos
import com.radustavila.nasaexplorer.core.testing.fake.FakeMarsPhotoRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeMarsSyncScheduler
import com.radustavila.nasaexplorer.core.testing.fake.FakeNetworkMonitor
import com.radustavila.nasaexplorer.core.testing.fake.FakeSyncMetadataRepository
import com.radustavila.nasaexplorer.core.testing.rule.MainDispatcherRule
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class MarsPhotosViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeMarsPhotoRepository()
    private val scheduler = FakeMarsSyncScheduler()
    private val networkMonitor = FakeNetworkMonitor()
    private val savedStateHandle = SavedStateHandle()
    private val metadata = FakeSyncMetadataRepository()

    private fun createViewModel() = MarsPhotosViewModel(
        savedStateHandle = savedStateHandle,
        observeRoverPhotos = ObserveMarsRoverPhotosUseCase(repository),
        observeSyncStatus = ObserveMarsSyncStatusUseCase(scheduler, metadata),
        observeConnectivity = ObserveConnectivityUseCase(networkMonitor),
        requestSync = RequestMarsSyncUseCase(scheduler),
    )

    private fun shot(id: String, sol: Int, camera: String, takenAt: String) =
        TestMarsPhotos.curiosity(id = id, sol = sol, cameraName = camera, takenAt = Instant.parse(takenAt))

    private suspend fun ReceiveTurbine<MarsPhotosUiState>.awaitLoaded(): MarsPhotosUiState {
        var state = awaitItem()
        while (!state.isLoaded) state = awaitItem()
        return state
    }

    @Test
    fun `photos are grouped by sol, newest first, then by camera`() = runTest {
        repository.setPhotos(
            listOf(
                shot(id = "a", sol = 5047, camera = "Navcam", takenAt = "2026-10-17T10:00:00Z"),
                shot(id = "b", sol = 5048, camera = "MAHLI", takenAt = "2026-10-18T10:00:00Z"),
                shot(id = "c", sol = 5048, camera = "Navcam", takenAt = "2026-10-18T09:00:00Z"),
                shot(id = "d", sol = 5048, camera = "MAHLI", takenAt = "2026-10-18T08:00:00Z"),
                TestMarsPhotos.perseverance(id = "p"),
            ),
        )

        createViewModel().uiState.test {
            val state = awaitLoaded()

            assertEquals(MarsRover.CURIOSITY, state.rover)
            assertEquals(listOf(5048, 5047), state.sols.map { it.sol })
            val newest = state.sols.first()
            assertEquals(listOf("MAHLI", "Navcam"), newest.cameras.map { it.cameraName })
            assertEquals(listOf("b", "d"), newest.cameras.first().photos.map { it.id })
            assertEquals(Instant.parse("2026-10-18T10:00:00Z"), newest.takenAt)
        }
    }

    @Test
    fun `switching rover shows that rover's photos and survives recreation`() = runTest {
        repository.setPhotos(listOf(TestMarsPhotos.curiosity(), TestMarsPhotos.perseverance(id = "p")))
        val viewModel = createViewModel()

        viewModel.uiState.test {
            awaitLoaded()
            viewModel.onRoverSelected(MarsRover.PERSEVERANCE)
            var state = awaitItem()
            while (state.rover != MarsRover.PERSEVERANCE) state = awaitItem()
            assertEquals(listOf("p"), state.sols.flatMap { sol -> sol.cameras.flatMap { it.photos } }.map { it.id })
        }

        createViewModel().uiState.test {
            assertEquals(MarsRover.PERSEVERANCE, awaitLoaded().rover)
        }
    }

    @Test
    fun `pull to refresh forces a background refresh`() {
        val viewModel = createViewModel()

        viewModel.onRefresh()

        assertEquals(listOf(false, true), scheduler.requests)
    }

    @Test
    fun `the last sync and its outcome feed the freshness line`() = runTest {
        val syncedAt = Instant.parse("2026-10-21T09:00:00Z")
        metadata.recordSyncSuccess(SyncSource.MARS, at = syncedAt)
        metadata.recordSyncFailure(SyncSource.MARS, at = syncedAt.plusSeconds(3_600), error = AppError.Timeout)

        createViewModel().uiState.test {
            assertEquals(
                DataFreshness(lastUpdatedAt = syncedAt, lastRefreshFailed = true),
                awaitLoaded().freshness,
            )
        }
    }

    @Test
    fun `offline is shown over the cache`() = runTest {
        networkMonitor.setOnline(false)
        repository.setPhotos(listOf(TestMarsPhotos.curiosity()))

        createViewModel().uiState.test {
            val state = awaitLoaded()
            assertTrue(state.isOffline)
            assertEquals(1, state.sols.size)
        }
    }
}

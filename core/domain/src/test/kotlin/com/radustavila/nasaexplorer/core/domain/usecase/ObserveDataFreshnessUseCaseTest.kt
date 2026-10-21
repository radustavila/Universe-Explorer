package com.radustavila.nasaexplorer.core.domain.usecase

import app.cash.turbine.test
import com.radustavila.nasaexplorer.core.common.error.AppError
import com.radustavila.nasaexplorer.core.model.DataFreshness
import com.radustavila.nasaexplorer.core.model.SyncSource
import com.radustavila.nasaexplorer.core.testing.fake.FakeNetworkMonitor
import com.radustavila.nasaexplorer.core.testing.fake.FakeSyncMetadataRepository
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ObserveDataFreshnessUseCaseTest {

    private val metadata = FakeSyncMetadataRepository()
    private val network = FakeNetworkMonitor()
    private val observeFreshness = ObserveDataFreshnessUseCase(metadata, network)

    private val morning = Instant.parse("2026-10-21T08:00:00Z")
    private val noon = Instant.parse("2026-10-21T12:00:00Z")

    @Test
    fun `a source never synced has nothing to report`() = runTest {
        assertEquals(DataFreshness(), observeFreshness(SyncSource.EPIC).first())
    }

    @Test
    fun `reports the source's last success`() = runTest {
        metadata.recordSyncSuccess(SyncSource.EPIC, at = noon)

        assertEquals(DataFreshness(lastUpdatedAt = noon), observeFreshness(SyncSource.EPIC).first())
    }

    @Test
    fun `other sources do not count`() = runTest {
        metadata.recordSyncSuccess(SyncSource.APOD, at = noon)
        metadata.recordSyncFailure(SyncSource.APOD, at = noon.plusSeconds(60), error = AppError.Timeout)

        assertEquals(DataFreshness(), observeFreshness(SyncSource.EPIC).first())
    }

    @Test
    fun `a failure after the last success is reported, and a later success clears it`() = runTest {
        metadata.recordSyncSuccess(SyncSource.LAUNCHES, at = morning)

        observeFreshness(SyncSource.LAUNCHES).test {
            assertEquals(DataFreshness(lastUpdatedAt = morning), awaitItem())

            metadata.recordSyncFailure(SyncSource.LAUNCHES, at = noon, error = AppError.Timeout)
            assertEquals(DataFreshness(lastUpdatedAt = morning, lastRefreshFailed = true), awaitItem())

            metadata.recordSyncSuccess(SyncSource.LAUNCHES, at = noon.plusSeconds(60))
            assertEquals(DataFreshness(lastUpdatedAt = noon.plusSeconds(60)), awaitItem())
        }
    }

    @Test
    fun `follows connectivity`() = runTest {
        metadata.recordSyncSuccess(SyncSource.EPIC, at = noon)

        observeFreshness(SyncSource.EPIC).test {
            assertEquals(DataFreshness(lastUpdatedAt = noon), awaitItem())

            network.setOnline(false)
            assertEquals(DataFreshness(lastUpdatedAt = noon, isOffline = true), awaitItem())
        }
    }

    @Test
    fun `several sources are as fresh as the stalest one that synced`() = runTest {
        metadata.recordSyncSuccess(SyncSource.CELESTRAK, at = noon)
        metadata.recordSyncSuccess(SyncSource.PEOPLE_IN_SPACE, at = morning)

        assertEquals(
            DataFreshness(lastUpdatedAt = morning),
            observeFreshness(SyncSource.CELESTRAK, SyncSource.PEOPLE_IN_SPACE).first(),
        )
    }

    @Test
    fun `with several sources a source never synced does not hide the others`() = runTest {
        metadata.recordSyncSuccess(SyncSource.CELESTRAK, at = noon)
        metadata.recordSyncFailure(SyncSource.PEOPLE_IN_SPACE, at = noon, error = AppError.Timeout)

        assertEquals(
            DataFreshness(lastUpdatedAt = noon, lastRefreshFailed = true),
            observeFreshness(SyncSource.CELESTRAK, SyncSource.PEOPLE_IN_SPACE).first(),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `at least one source is required`() {
        observeFreshness()
    }
}

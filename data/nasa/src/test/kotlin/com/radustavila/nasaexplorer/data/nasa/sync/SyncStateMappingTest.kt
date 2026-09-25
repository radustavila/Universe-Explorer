package com.radustavila.nasaexplorer.data.nasa.sync

import androidx.work.Data
import androidx.work.WorkInfo
import com.radustavila.nasaexplorer.core.common.error.AppError
import com.radustavila.nasaexplorer.core.model.SyncState
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SyncStateMappingTest {

    @Test
    fun `no scheduled work means idle`() {
        assertEquals(SyncState.Idle, emptyList<WorkInfo>().toSyncState())
    }

    @Test
    fun `a running request wins over a finished one`() {
        val state = listOf(
            workInfo(WorkInfo.State.SUCCEEDED),
            workInfo(WorkInfo.State.RUNNING),
        ).toSyncState()

        assertEquals(SyncState.Syncing, state)
    }

    @Test
    fun `a first-time enqueued request is reported as waiting for its constraints`() {
        assertEquals(SyncState.Enqueued, listOf(workInfo(WorkInfo.State.ENQUEUED)).toSyncState())
    }

    @Test
    fun `an enqueued retry still counts as syncing`() {
        val state = listOf(workInfo(WorkInfo.State.ENQUEUED, runAttemptCount = 2)).toSyncState()

        assertEquals(SyncState.Syncing, state)
    }

    @Test
    fun `a failure carries the decoded reason to the UI`() {
        val state = listOf(
            workInfo(
                state = WorkInfo.State.FAILED,
                outputData = SyncErrorCodec.encode(AppError.RateLimited()),
            ),
        ).toSyncState()

        assertEquals(SyncState.Failed(AppError.RateLimited()), state)
    }

    @Test
    fun `a periodic request resting between runs is not reported as progress`() {
        val state = combineSyncStates(
            onDemand = listOf(workInfo(WorkInfo.State.SUCCEEDED)),
            periodic = listOf(workInfo(WorkInfo.State.ENQUEUED)),
        )

        assertEquals(SyncState.Succeeded, state)
    }

    @Test
    fun `a periodic request that is actually running is reported as syncing`() {
        val state = combineSyncStates(
            onDemand = emptyList(),
            periodic = listOf(workInfo(WorkInfo.State.RUNNING)),
        )

        assertEquals(SyncState.Syncing, state)
    }

    private fun workInfo(
        state: WorkInfo.State,
        runAttemptCount: Int = 0,
        outputData: Data = Data.EMPTY,
    ) = WorkInfo(
        id = UUID.randomUUID(),
        state = state,
        tags = emptySet(),
        outputData = outputData,
        runAttemptCount = runAttemptCount,
        generation = 0,
    )
}

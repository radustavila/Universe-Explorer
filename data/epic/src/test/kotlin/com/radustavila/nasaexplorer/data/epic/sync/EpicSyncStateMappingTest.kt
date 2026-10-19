package com.radustavila.nasaexplorer.data.epic.sync

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
class EpicSyncStateMappingTest {

    @Test
    fun `no scheduled work means idle`() {
        assertEquals(SyncState.Idle, combineEpicSyncStates(emptyList(), emptyList()))
    }

    @Test
    fun `a periodic request resting between runs is not reported as progress`() {
        val state = combineEpicSyncStates(
            onDemand = listOf(workInfo(WorkInfo.State.SUCCEEDED)),
            periodic = listOf(workInfo(WorkInfo.State.ENQUEUED)),
        )

        assertEquals(SyncState.Succeeded, state)
    }

    @Test
    fun `a running request wins and an enqueued retry still counts as syncing`() {
        assertEquals(
            SyncState.Syncing,
            combineEpicSyncStates(listOf(workInfo(WorkInfo.State.SUCCEEDED), workInfo(WorkInfo.State.RUNNING)), emptyList()),
        )
        assertEquals(
            SyncState.Syncing,
            combineEpicSyncStates(listOf(workInfo(WorkInfo.State.ENQUEUED, runAttemptCount = 1)), emptyList()),
        )
    }

    @Test
    fun `a failure carries every error type through WorkManager data`() {
        val errors = listOf(
            AppError.NoConnection,
            AppError.Timeout,
            AppError.Http(code = 503, message = "Service Unavailable"),
            AppError.RateLimited(),
            AppError.Serialization("bad"),
            AppError.Database("locked"),
            AppError.EmptyResponse,
            AppError.Unknown("?"),
        )

        errors.forEach { error ->
            val state = combineEpicSyncStates(
                onDemand = listOf(workInfo(WorkInfo.State.FAILED, outputData = EpicSyncErrorCodec.encode(error))),
                periodic = emptyList(),
            )
            assertEquals(SyncState.Failed(error), state)
        }
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

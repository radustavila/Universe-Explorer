package com.radustavila.nasaexplorer.core.testing.fake

import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.domain.repository.WebbScheduleRepository
import com.radustavila.nasaexplorer.core.model.WebbSchedule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** In-memory [WebbScheduleRepository] that counts refreshes; a success stores [scheduleOnRefresh] when set. */
class FakeWebbScheduleRepository(initial: WebbSchedule? = null) : WebbScheduleRepository {

    val schedule = MutableStateFlow(initial)

    var refreshResult: DataResult<Int> = DataResult.Success(initial?.visits?.size ?: 0)
    var scheduleOnRefresh: WebbSchedule? = null

    var refreshCount: Int = 0
        private set

    override fun observeSchedule(): Flow<WebbSchedule?> = schedule

    override suspend fun refresh(): DataResult<Int> {
        refreshCount++
        if (refreshResult is DataResult.Success) scheduleOnRefresh?.let { schedule.value = it }
        return refreshResult
    }
}

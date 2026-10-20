package com.radustavila.nasaexplorer.core.testing.fake

import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.domain.repository.LaunchRepository
import com.radustavila.nasaexplorer.core.model.Launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** In-memory [LaunchRepository]: the cache is whatever the test seeds, sorted soonest first. */
class FakeLaunchRepository : LaunchRepository {

    private val launches = MutableStateFlow<List<Launch>>(emptyList())

    var refreshResult: DataResult<Int> = DataResult.Success(0)

    var refreshCallCount: Int = 0
        private set

    fun setLaunches(entries: List<Launch>) {
        launches.value = entries.sortedBy(Launch::net)
    }

    override fun observeUpcomingLaunches(): Flow<List<Launch>> = launches

    /** How long a refresh takes, in virtual time. */
    var refreshDelayMillis: Long = 0

    override suspend fun refreshUpcomingLaunches(): DataResult<Int> {
        refreshCallCount++
        delay(refreshDelayMillis)
        return refreshResult
    }
}

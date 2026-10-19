package com.radustavila.nasaexplorer.core.testing.fake

import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.domain.repository.LaunchRefreshOutcome
import com.radustavila.nasaexplorer.core.domain.repository.LaunchRepository
import com.radustavila.nasaexplorer.core.model.Launch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** In-memory [LaunchRepository]: the cache is whatever the test seeds, sorted soonest first. */
class FakeLaunchRepository : LaunchRepository {

    private val launches = MutableStateFlow<List<Launch>>(emptyList())

    var refreshResult: DataResult<LaunchRefreshOutcome> =
        DataResult.Success(LaunchRefreshOutcome.REFRESHED)

    var refreshCallCount: Int = 0
        private set

    fun setLaunches(entries: List<Launch>) {
        launches.value = entries.sortedBy(Launch::net)
    }

    override fun observeUpcomingLaunches(): Flow<List<Launch>> = launches

    override suspend fun refreshUpcomingLaunches(): DataResult<LaunchRefreshOutcome> {
        refreshCallCount++
        return refreshResult
    }
}

package com.radustavila.nasaexplorer.core.testing.fake

import com.radustavila.nasaexplorer.core.common.error.AppError
import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.domain.repository.LaunchRepository
import com.radustavila.nasaexplorer.core.model.Launch
import com.radustavila.nasaexplorer.core.model.LaunchTimeline
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine

/**
 * In-memory [LaunchRepository]: the cache is whatever the test seeds, upcoming soonest first and
 * past most recent first. Every refresh counts as one Launch Library 2 call in [callCount].
 */
class FakeLaunchRepository : LaunchRepository {

    private val upcoming = MutableStateFlow<List<Launch>>(emptyList())
    private val past = MutableStateFlow<List<Launch>>(emptyList())

    var refreshResult: DataResult<Int> = DataResult.Success(0)
    var refreshPastResult: DataResult<Int> = DataResult.Success(0)

    /** What [refreshLaunch] stores and returns per id; a failure when absent. */
    val refreshedLaunches = mutableMapOf<String, Launch>()

    var refreshCallCount: Int = 0
        private set
    var refreshPastCallCount: Int = 0
        private set
    val refreshedLaunchIds = mutableListOf<String>()

    /** Every call any refresh made to Launch Library 2. */
    val callCount: Int get() = refreshCallCount + refreshPastCallCount + refreshedLaunchIds.size

    fun setLaunches(entries: List<Launch>) {
        upcoming.value = entries.map { it.copy(timeline = LaunchTimeline.UPCOMING) }.sortedBy(Launch::net)
    }

    fun setPastLaunches(entries: List<Launch>) {
        past.value = entries.map { it.copy(timeline = LaunchTimeline.PAST) }.sortedByDescending(Launch::net)
    }

    override fun observeUpcomingLaunches(): Flow<List<Launch>> = upcoming

    override fun observePastLaunches(): Flow<List<Launch>> = past

    override fun observeLaunch(id: String): Flow<Launch?> =
        combine(upcoming, past) { ahead, flown -> ahead.firstOrNull { it.id == id } ?: flown.firstOrNull { it.id == id } }

    /** How long a refresh takes, in virtual time. */
    var refreshDelayMillis: Long = 0

    override suspend fun refreshUpcomingLaunches(): DataResult<Int> {
        refreshCallCount++
        delay(refreshDelayMillis)
        return refreshResult
    }

    override suspend fun refreshPastLaunches(): DataResult<Int> {
        refreshPastCallCount++
        return refreshPastResult
    }

    override suspend fun refreshLaunch(id: String): DataResult<Launch> {
        refreshedLaunchIds += id
        val launch = refreshedLaunches[id] ?: return DataResult.Failure(AppError.Timeout)
        if (upcoming.value.any { it.id == id }) {
            setLaunches(upcoming.value.filterNot { it.id == id } + launch)
        } else {
            setPastLaunches(past.value.filterNot { it.id == id } + launch)
        }
        return DataResult.Success(launch)
    }
}

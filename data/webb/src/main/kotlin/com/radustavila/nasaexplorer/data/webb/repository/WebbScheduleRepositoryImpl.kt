package com.radustavila.nasaexplorer.data.webb.repository

import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.common.result.flatMap
import com.radustavila.nasaexplorer.core.common.result.map
import com.radustavila.nasaexplorer.core.common.time.TimeProvider
import com.radustavila.nasaexplorer.core.domain.repository.WebbScheduleRepository
import com.radustavila.nasaexplorer.core.model.WebbSchedule
import com.radustavila.nasaexplorer.data.webb.local.WebbLocalDataSource
import com.radustavila.nasaexplorer.data.webb.mapper.toEntity
import com.radustavila.nasaexplorer.data.webb.remote.ScheduledVisitDto
import com.radustavila.nasaexplorer.data.webb.remote.WebbRemoteDataSource
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import okhttp3.HttpUrl

/**
 * Offline-first Webb schedule: screens observe Room, and [refresh] is the only path from STScI into
 * it. A refresh reads the index page, then the newest week's file. STScI posts a week shortly before
 * it starts, so while the newest week is still ahead the week before is read too and its visits up
 * to the newest week's first are kept: the card always has something scheduled now. A failure keeps
 * the last good schedule.
 */
@Singleton
internal class WebbScheduleRepositoryImpl @Inject constructor(
    private val localDataSource: WebbLocalDataSource,
    private val remoteDataSource: WebbRemoteDataSource,
    private val timeProvider: TimeProvider,
) : WebbScheduleRepository {

    override fun observeSchedule(): Flow<WebbSchedule?> = localDataSource.observeSchedule()

    override suspend fun refresh(): DataResult<Int> = remoteDataSource.fetchScheduleLinks().flatMap { links ->
        remoteDataSource.fetchSchedule(links.first()).flatMap { newest ->
            val visits = withWeekBefore(newest, links.getOrNull(1))
                .distinctBy { it.visitId to it.start }
                .sortedBy { it.start }
            val fetchedAt = timeProvider.now().toEpochMilli()
            localDataSource.replaceSchedule(visits.map { it.toEntity(fetchedAt) }).map { visits.size }
        }
    }

    private suspend fun withWeekBefore(newest: List<ScheduledVisitDto>, weekBefore: HttpUrl?): List<ScheduledVisitDto> {
        val newestStart = newest.minOf { it.start }
        if (weekBefore == null || !newestStart.isAfter(timeProvider.now())) return newest
        return when (val before = remoteDataSource.fetchSchedule(weekBefore)) {
            is DataResult.Success -> before.data.filter { it.start.isBefore(newestStart) } + newest
            is DataResult.Failure -> newest
        }
    }
}

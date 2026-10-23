package com.radustavila.nasaexplorer.data.comets.repository

import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.common.result.flatMap
import com.radustavila.nasaexplorer.core.common.result.map
import com.radustavila.nasaexplorer.core.common.time.TimeProvider
import com.radustavila.nasaexplorer.core.domain.repository.CometRepository
import com.radustavila.nasaexplorer.core.model.Comet
import com.radustavila.nasaexplorer.data.comets.local.CometEphemerisEntity
import com.radustavila.nasaexplorer.data.comets.local.CometsLocalDataSource
import com.radustavila.nasaexplorer.data.comets.mapper.toEntity
import com.radustavila.nasaexplorer.data.comets.remote.CometsRemoteDataSource
import com.radustavila.nasaexplorer.data.comets.remote.dto.CometCandidateDto
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

/**
 * Offline-first comets: screens observe Room, and [refresh] is the only path from JPL into it.
 *
 * A refresh is one SBDB query, then one Horizons request per candidate, one after another (JPL asks
 * for no parallel load), at most [MAX_COMETS] of them. A failed SBDB query, or one where every
 * Horizons request failed, keeps the last good state.
 */
@Singleton
internal class CometRepositoryImpl @Inject constructor(
    private val localDataSource: CometsLocalDataSource,
    private val remoteDataSource: CometsRemoteDataSource,
    private val timeProvider: TimeProvider,
) : CometRepository {

    override fun observeComets(): Flow<List<Comet>> = localDataSource.observeComets()

    override fun observeComet(designation: String): Flow<Comet?> = localDataSource.observeComet(designation)

    override suspend fun refresh(): DataResult<Int> =
        remoteDataSource.fetchCandidates(timeProvider.now()).flatMap { candidates ->
            val chosen = candidates
                .distinctBy { it.designation }
                .sortedWith(compareBy(nullsLast()) { it.absoluteMagnitude })
                .take(MAX_COMETS)
            val fetchedAt = timeProvider.now().toEpochMilli()
            val ephemerides = mutableMapOf<String, List<CometEphemerisEntity>>()
            var firstFailure: DataResult.Failure? = null
            chosen.forEach { candidate ->
                when (val result = fetchEphemeris(candidate)) {
                    is DataResult.Success -> ephemerides[candidate.designation] =
                        result.data.map { it.toEntity(candidate.designation, fetchedAt) }

                    is DataResult.Failure -> firstFailure = firstFailure ?: result
                }
            }
            val allFailed = chosen.isNotEmpty() && ephemerides.isEmpty()
            if (allFailed) {
                checkNotNull(firstFailure)
            } else {
                localDataSource.storeRefresh(chosen.map { it.toEntity(fetchedAt) }, ephemerides).map { chosen.size }
            }
        }

    private suspend fun fetchEphemeris(candidate: CometCandidateDto) = timeProvider.today().let { today ->
        remoteDataSource.fetchEphemeris(
            designation = candidate.designation,
            from = today.minusDays(EPHEMERIS_DAYS_EACH_SIDE),
            until = today.plusDays(EPHEMERIS_DAYS_EACH_SIDE),
        )
    }

    companion object {
        /** A bright-comet year lists a dozen candidates; Horizons is asked about no more than this. */
        const val MAX_COMETS = 10

        /** The brightness curve's reach either side of today, which also covers offline days. */
        const val EPHEMERIS_DAYS_EACH_SIDE = 60L
    }
}

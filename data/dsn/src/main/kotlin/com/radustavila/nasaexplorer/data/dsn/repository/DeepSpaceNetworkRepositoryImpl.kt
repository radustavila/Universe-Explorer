package com.radustavila.nasaexplorer.data.dsn.repository

import com.radustavila.nasaexplorer.core.common.error.AppError
import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.common.result.flatMap
import com.radustavila.nasaexplorer.core.common.result.map
import com.radustavila.nasaexplorer.core.common.time.TimeProvider
import com.radustavila.nasaexplorer.core.domain.repository.DeepSpaceNetworkRepository
import com.radustavila.nasaexplorer.core.model.DeepSpaceProbe
import com.radustavila.nasaexplorer.core.model.DsnSnapshot
import com.radustavila.nasaexplorer.data.dsn.local.DsnLocalDataSource
import com.radustavila.nasaexplorer.data.dsn.mapper.toEntity
import com.radustavila.nasaexplorer.data.dsn.mapper.toRows
import com.radustavila.nasaexplorer.data.dsn.mapper.toRowsOrNull
import com.radustavila.nasaexplorer.data.dsn.remote.DsnRemoteDataSource
import com.radustavila.nasaexplorer.data.dsn.remote.dto.HorizonsRangeDto
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

/**
 * Offline-first DSN Now and Horizons: screens observe Room, and the refreshes are the only path
 * from the network into it. A failed, empty or unreadable download keeps the last good state, so a
 * change NASA makes to its undocumented feed shows as an old snapshot, not as a broken screen.
 *
 * How often each refresh runs is decided above it (`PollDeepSpaceNetworkUseCase`).
 */
@Singleton
internal class DeepSpaceNetworkRepositoryImpl @Inject constructor(
    private val localDataSource: DsnLocalDataSource,
    private val remoteDataSource: DsnRemoteDataSource,
    private val timeProvider: TimeProvider,
) : DeepSpaceNetworkRepository {

    override fun observeSnapshot(): Flow<DsnSnapshot?> = localDataSource.observeSnapshot()

    override fun observeFarthestProbes(): Flow<List<DeepSpaceProbe>> =
        localDataSource.observeProbes(HorizonsTargets.FARTHEST.toSet())

    override suspend fun refreshLive(): DataResult<Unit> = remoteDataSource.fetchLive().flatMap { feed ->
        val rows = feed.toRowsOrNull(fetchedAtMillis = timeProvider.now().toEpochMilli())
            ?: return@flatMap DataResult.Failure(AppError.EmptyResponse)
        localDataSource.storeLive(rows)
    }

    override suspend fun refreshConfig(): DataResult<Unit> = remoteDataSource.fetchConfig().flatMap { config ->
        val rows = config.toRows(fetchedAtMillis = timeProvider.now().toEpochMilli())
        if (rows.spacecraft.isEmpty() && rows.dishes.isEmpty()) {
            DataResult.Failure(AppError.EmptyResponse)
        } else {
            localDataSource.storeConfig(rows)
        }
    }

    /**
     * One request per spacecraft, one after another (Horizons asks for no parallel load). A
     * spacecraft that fails keeps its previous distance; the refresh fails only when none worked.
     */
    override suspend fun refreshDistances(): DataResult<Int> {
        val today = timeProvider.today()
        val ranges = mutableListOf<HorizonsRangeDto>()
        var firstFailure: DataResult.Failure? = null
        HorizonsTargets.ALL.forEach { id ->
            when (val result = remoteDataSource.fetchRange(id, today)) {
                is DataResult.Success -> ranges += result.data
                is DataResult.Failure -> firstFailure = firstFailure ?: result
            }
        }
        if (ranges.isEmpty()) return firstFailure ?: DataResult.Failure(AppError.EmptyResponse)
        val fetchedAt = timeProvider.now().toEpochMilli()
        return localDataSource.storeDistances(ranges.map { it.toEntity(fetchedAt) }).map { ranges.size }
    }
}

/** The spacecraft the app asks Horizons about, by Horizons/NAIF id. */
internal object HorizonsTargets {
    /** "Farthest from home": Voyager 1, Voyager 2, New Horizons. */
    val FARTHEST: List<Int> = listOf(-31, -32, -98)

    /**
     * Fill in when the DSN feed gives no range (-1), which it often does: Parker Solar Probe, JWST,
     * Juno, Europa Clipper.
     */
    val FALLBACK: List<Int> = listOf(-96, -170, -61, -159)

    val ALL: List<Int> = FARTHEST + FALLBACK
}

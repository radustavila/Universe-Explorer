package com.radustavila.nasaexplorer.data.smallbodies.repository

import com.radustavila.nasaexplorer.core.common.error.AppError
import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.common.time.TimeProvider
import com.radustavila.nasaexplorer.core.domain.repository.SmallBodiesRepository
import com.radustavila.nasaexplorer.core.model.CloseApproach
import com.radustavila.nasaexplorer.core.model.Fireball
import com.radustavila.nasaexplorer.core.model.SmallBodyDetails
import com.radustavila.nasaexplorer.data.smallbodies.local.SmallBodiesLocalDataSource
import com.radustavila.nasaexplorer.data.smallbodies.mapper.toCloseApproachEntities
import com.radustavila.nasaexplorer.data.smallbodies.mapper.toDomain
import com.radustavila.nasaexplorer.data.smallbodies.mapper.toEntityOrNull
import com.radustavila.nasaexplorer.data.smallbodies.mapper.toFireballEntities
import com.radustavila.nasaexplorer.data.smallbodies.mapper.toSentryEntities
import com.radustavila.nasaexplorer.data.smallbodies.remote.SmallBodiesRemoteDataSource
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Offline-first small bodies: screens observe Room, and [refresh] and [fetchDetails] are the only
 * paths from JPL into it. A refresh is all or nothing, so the lists never mix two days' answers.
 */
@Singleton
class SmallBodiesRepositoryImpl @Inject constructor(
    private val local: SmallBodiesLocalDataSource,
    private val remote: SmallBodiesRemoteDataSource,
    private val timeProvider: TimeProvider,
) : SmallBodiesRepository {

    override fun observeCloseApproaches(): Flow<List<CloseApproach>> =
        local.observeCloseApproaches().map { list -> list.map { it.toDomain() } }

    override fun observeCloseApproach(designation: String): Flow<CloseApproach?> =
        local.observeCloseApproach(designation).map { it?.toDomain() }

    override fun observeRecentFireballs(): Flow<List<Fireball>> =
        local.observeFireballs().map { list -> list.map { it.toDomain() } }

    override fun observeDetails(designation: String): Flow<SmallBodyDetails?> =
        local.observeDetails(designation).map { it?.toDomain() }

    override suspend fun refresh(): DataResult<Unit> {
        val approaches = when (val result = remote.fetchCloseApproaches()) {
            is DataResult.Success -> result.data.toCloseApproachEntities()
            is DataResult.Failure -> return result
        }
        val sentry = when (val result = remote.fetchSentryList()) {
            is DataResult.Success -> result.data.data.toSentryEntities()
            is DataResult.Failure -> return result
        }
        // An empty Sentry list means a broken answer, not a safe sky: keep the cached one.
        if (sentry.isEmpty()) return DataResult.Failure(AppError.EmptyResponse)
        val fireballs = when (val result = remote.fetchFireballs()) {
            is DataResult.Success -> result.data.toFireballEntities()
            is DataResult.Failure -> return result
        }
        // No close approach in 30 days is possible (if rare), so an empty CAD answer is stored as is.
        return local.replaceAll(approaches, sentry, fireballs)
    }

    override suspend fun fetchDetails(designation: String): DataResult<SmallBodyDetails> =
        when (val result = remote.fetchSmallBody(designation)) {
            is DataResult.Success -> {
                // Keyed by the designation asked for, which is what the screens observe.
                val entity = result.data.toEntityOrNull(fetchedAt = timeProvider.now())?.copy(designation = designation)
                    ?: return DataResult.Failure(AppError.EmptyResponse)
                when (val saved = local.saveDetails(entity)) {
                    is DataResult.Success -> DataResult.Success(entity.toDomain())
                    is DataResult.Failure -> saved
                }
            }

            is DataResult.Failure -> result
        }
}

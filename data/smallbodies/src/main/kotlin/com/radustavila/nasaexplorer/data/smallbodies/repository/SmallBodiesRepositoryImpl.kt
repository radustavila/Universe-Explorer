package com.radustavila.nasaexplorer.data.smallbodies.repository

import com.radustavila.nasaexplorer.core.common.error.AppError
import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.common.time.TimeProvider
import com.radustavila.nasaexplorer.core.domain.repository.SmallBodiesRepository
import com.radustavila.nasaexplorer.core.model.CloseApproach
import com.radustavila.nasaexplorer.core.model.Fireball
import com.radustavila.nasaexplorer.core.model.SmallBodyDetails
import com.radustavila.nasaexplorer.data.smallbodies.local.CloseApproachEntity
import com.radustavila.nasaexplorer.data.smallbodies.local.SmallBodiesLocalDataSource
import com.radustavila.nasaexplorer.data.smallbodies.mapper.toCloseApproachEntities
import com.radustavila.nasaexplorer.data.smallbodies.mapper.toDomain
import com.radustavila.nasaexplorer.data.smallbodies.mapper.toEntityOrNull
import com.radustavila.nasaexplorer.data.smallbodies.mapper.toFireballEntities
import com.radustavila.nasaexplorer.data.smallbodies.mapper.toSentryEntities
import com.radustavila.nasaexplorer.data.smallbodies.remote.SmallBodiesRemoteDataSource
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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
        // Three independent JPL APIs: asked at once, then checked in order.
        val (approachesResult, sentryResult, fireballsResult) = coroutineScope {
            val approaches = async { remote.fetchCloseApproaches() }
            val sentry = async { remote.fetchSentryList() }
            val fireballs = async { remote.fetchFireballs() }
            Triple(approaches.await(), sentry.await(), fireballs.await())
        }
        val approaches = when (approachesResult) {
            is DataResult.Success -> approachesResult.data.toCloseApproachEntities()
            is DataResult.Failure -> return approachesResult
        }
        val sentry = when (sentryResult) {
            is DataResult.Success -> sentryResult.data.data.toSentryEntities()
            is DataResult.Failure -> return sentryResult
        }
        // An empty Sentry list means a broken answer, not a safe sky: keep the cached one.
        if (sentry.isEmpty()) return DataResult.Failure(AppError.EmptyResponse)
        val fireballs = when (fireballsResult) {
            is DataResult.Success -> fireballsResult.data.toFireballEntities()
            is DataResult.Failure -> return fireballsResult
        }
        // Only the risk of an object passing by is ever shown, so the rest of Sentry's ~2,000
        // objects is not stored.
        val approaching = approaches.mapTo(mutableSetOf(), CloseApproachEntity::designation)
        // No close approach in 30 days is possible (if rare), so an empty CAD answer is stored as is.
        return local.replaceAll(approaches, sentry.filter { it.designation in approaching }, fireballs)
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

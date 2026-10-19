package com.radustavila.nasaexplorer.data.launches.repository

import com.radustavila.nasaexplorer.core.common.error.AppError
import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.common.result.map
import com.radustavila.nasaexplorer.core.domain.sky.PeopleInSpaceRepository
import com.radustavila.nasaexplorer.core.model.PersonInSpace
import com.radustavila.nasaexplorer.data.launches.local.PeopleInSpaceLocalDataSource
import com.radustavila.nasaexplorer.data.launches.mapper.toEntityOrNull
import com.radustavila.nasaexplorer.data.launches.remote.LaunchRemoteDataSource
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

/**
 * Offline-first crew list on the same Launch Library 2 client and hourly budget as the launches
 * card; how often it refreshes is `RefreshPeopleInSpaceUseCase`'s decision.
 */
@Singleton
class PeopleInSpaceRepositoryImpl @Inject constructor(
    private val localDataSource: PeopleInSpaceLocalDataSource,
    private val remoteDataSource: LaunchRemoteDataSource,
) : PeopleInSpaceRepository {

    override fun observePeopleInSpace(): Flow<List<PersonInSpace>> = localDataSource.observeAll()

    override suspend fun refreshPeopleInSpace(): DataResult<Int> =
        when (val result = remoteDataSource.fetchAstronautsInSpace()) {
            is DataResult.Success -> {
                val entities = result.data.mapNotNull { dto -> dto.toEntityOrNull() }
                if (entities.isEmpty()) {
                    // Nobody in space has not happened since 2000: an empty list means a bad answer.
                    DataResult.Failure(AppError.EmptyResponse)
                } else {
                    localDataSource.replaceAll(entities).map { entities.size }
                }
            }

            is DataResult.Failure -> result
        }
}

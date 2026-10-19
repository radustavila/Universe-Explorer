package com.radustavila.nasaexplorer.core.domain.sky

import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.model.PersonInSpace
import kotlinx.coroutines.flow.Flow

/** Who is in orbit right now (Launch Library 2), read from the local cache. */
interface PeopleInSpaceRepository {

    fun observePeopleInSpace(): Flow<List<PersonInSpace>>

    /**
     * Replaces the cache in one network call; on failure the cached crew is kept.
     *
     * @return how many people are now cached.
     */
    suspend fun refreshPeopleInSpace(): DataResult<Int>
}

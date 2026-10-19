package com.radustavila.nasaexplorer.core.testing.fake

import com.radustavila.nasaexplorer.core.common.error.AppError
import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.domain.repository.SmallBodiesRepository
import com.radustavila.nasaexplorer.core.model.CloseApproach
import com.radustavila.nasaexplorer.core.model.Fireball
import com.radustavila.nasaexplorer.core.model.SmallBodyDetails
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** In-memory [SmallBodiesRepository]. [fetchDetails] answers from [detailsOnFetch] and caches it. */
class FakeSmallBodiesRepository : SmallBodiesRepository {

    val closeApproaches = MutableStateFlow<List<CloseApproach>>(emptyList())
    val fireballs = MutableStateFlow<List<Fireball>>(emptyList())
    private val details = MutableStateFlow<Map<String, SmallBodyDetails>>(emptyMap())

    var refreshResult: DataResult<Unit> = DataResult.Success(Unit)
    var detailsOnFetch: Map<String, SmallBodyDetails> = emptyMap()
    var fetchError: AppError? = null

    var refreshCallCount = 0
        private set
    var fetchedDesignations: List<String> = emptyList()
        private set

    fun setDetails(entry: SmallBodyDetails) {
        details.value = details.value + (entry.designation to entry)
    }

    override fun observeCloseApproaches(): Flow<List<CloseApproach>> = closeApproaches

    override fun observeCloseApproach(designation: String): Flow<CloseApproach?> =
        closeApproaches.map { list -> list.firstOrNull { it.designation == designation } }

    override fun observeRecentFireballs(): Flow<List<Fireball>> = fireballs

    override fun observeDetails(designation: String): Flow<SmallBodyDetails?> = details.map { it[designation] }

    override suspend fun refresh(): DataResult<Unit> {
        refreshCallCount++
        return refreshResult
    }

    override suspend fun fetchDetails(designation: String): DataResult<SmallBodyDetails> {
        fetchedDesignations = fetchedDesignations + designation
        fetchError?.let { return DataResult.Failure(it) }
        val found = detailsOnFetch[designation] ?: return DataResult.Failure(AppError.EmptyResponse)
        setDetails(found)
        return DataResult.Success(found)
    }
}

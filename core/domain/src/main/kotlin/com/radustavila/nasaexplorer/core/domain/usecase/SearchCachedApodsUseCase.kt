package com.radustavila.nasaexplorer.core.domain.usecase

import com.radustavila.nasaexplorer.core.domain.repository.ApodRepository
import com.radustavila.nasaexplorer.core.model.Apod
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * Full-text search over the local cache. The APOD service ignores search parameters, so this is
 * deliberately offline-only: it finds what has been downloaded, and nothing else.
 */
class SearchCachedApodsUseCase @Inject constructor(
    private val apodRepository: ApodRepository,
) {
    operator fun invoke(query: String): Flow<List<Apod>> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return flowOf(emptyList())
        return apodRepository.searchCached(trimmed)
    }
}

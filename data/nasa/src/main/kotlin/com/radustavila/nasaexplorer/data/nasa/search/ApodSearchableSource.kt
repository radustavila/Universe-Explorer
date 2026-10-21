package com.radustavila.nasaexplorer.data.nasa.search

import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.domain.repository.ApodRepository
import com.radustavila.nasaexplorer.core.domain.search.SearchText
import com.radustavila.nasaexplorer.core.domain.search.SearchableSource
import com.radustavila.nasaexplorer.core.domain.search.readLocally
import com.radustavila.nasaexplorer.core.model.SearchHit
import com.radustavila.nasaexplorer.core.model.SearchSource
import com.radustavila.nasaexplorer.core.model.SearchTarget
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/** The cached archive, through the same full-text index as the feed's own search. */
class ApodSearchableSource @Inject constructor(
    private val apodRepository: ApodRepository,
) : SearchableSource {

    override val source = SearchSource.APOD

    override suspend fun search(query: String, limit: Int): DataResult<List<SearchHit>> = readLocally {
        val terms = SearchText.terms(query)
        apodRepository.searchCached(query).first().take(limit).map { apod ->
            SearchHit(
                source = source,
                id = apod.date.toString(),
                title = apod.title,
                target = SearchTarget.ApodEntry(apod.date),
                snippet = SearchText.snippet(apod.explanation, terms),
                date = apod.date,
                imageUrl = apod.previewImageUrl,
            )
        }
    }
}

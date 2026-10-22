package com.radustavila.nasaexplorer.data.earthevents.search

import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.domain.repository.EarthEventRepository
import com.radustavila.nasaexplorer.core.domain.search.SearchText
import com.radustavila.nasaexplorer.core.domain.search.SearchableSource
import com.radustavila.nasaexplorer.core.domain.search.readLocally
import com.radustavila.nasaexplorer.core.model.SearchHit
import com.radustavila.nasaexplorer.core.model.SearchSource
import com.radustavila.nasaexplorer.core.model.SearchTarget
import java.time.ZoneOffset
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/** Cached open events by title ("Hurricane Milton", "Etna"), most recently updated first. */
class EarthEventSearchableSource @Inject constructor(
    private val repository: EarthEventRepository,
) : SearchableSource {

    override val source = SearchSource.EARTH_EVENTS

    override suspend fun search(query: String, limit: Int): DataResult<List<SearchHit>> = readLocally {
        val terms = SearchText.terms(query)
        repository.observeOpenEvents().first()
            .asSequence()
            .filter { SearchText.matchesAll(terms, it.title) }
            .take(limit)
            .map { event ->
                SearchHit(
                    source = source,
                    id = event.id,
                    title = event.title,
                    target = SearchTarget.EarthEventEntry(event.id),
                    date = event.lastUpdate.atOffset(ZoneOffset.UTC).toLocalDate(),
                )
            }
            .toList()
    }
}

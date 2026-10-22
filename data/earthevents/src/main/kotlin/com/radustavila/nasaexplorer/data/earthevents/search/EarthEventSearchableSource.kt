package com.radustavila.nasaexplorer.data.earthevents.search

import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.common.time.TimeProvider
import com.radustavila.nasaexplorer.core.domain.repository.EarthEventRepository
import com.radustavila.nasaexplorer.core.domain.search.SearchText
import com.radustavila.nasaexplorer.core.domain.search.SearchableSource
import com.radustavila.nasaexplorer.core.domain.search.readLocally
import com.radustavila.nasaexplorer.core.model.EventRecency
import com.radustavila.nasaexplorer.core.model.SearchHit
import com.radustavila.nasaexplorer.core.model.SearchSource
import com.radustavila.nasaexplorer.core.model.SearchTarget
import com.radustavila.nasaexplorer.core.model.recency
import java.time.ZoneOffset
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/**
 * Cached open events by title ("Hurricane Milton", "Etna"), most recently updated first. Wildfires
 * with no report for six weeks are left out, as on the events map.
 */
class EarthEventSearchableSource @Inject constructor(
    private val repository: EarthEventRepository,
    private val timeProvider: TimeProvider,
) : SearchableSource {

    override val source = SearchSource.EARTH_EVENTS

    override suspend fun search(query: String, limit: Int): DataResult<List<SearchHit>> = readLocally {
        val terms = SearchText.terms(query)
        val today = timeProvider.today()
        repository.observeOpenEvents().first()
            .asSequence()
            .filter { it.recency(today) != EventRecency.Expired }
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

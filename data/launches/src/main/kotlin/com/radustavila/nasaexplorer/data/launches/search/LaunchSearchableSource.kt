package com.radustavila.nasaexplorer.data.launches.search

import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.domain.repository.LaunchRepository
import com.radustavila.nasaexplorer.core.domain.search.SearchText
import com.radustavila.nasaexplorer.core.domain.search.SearchableSource
import com.radustavila.nasaexplorer.core.domain.search.readLocally
import com.radustavila.nasaexplorer.core.model.SearchHit
import com.radustavila.nasaexplorer.core.model.SearchSource
import com.radustavila.nasaexplorer.core.model.SearchTarget
import java.time.ZoneOffset
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/** Cached upcoming launches by mission, rocket or pad ("Starship", "Vandenberg"), soonest first. */
class LaunchSearchableSource @Inject constructor(
    private val repository: LaunchRepository,
) : SearchableSource {

    override val source = SearchSource.LAUNCHES

    override suspend fun search(query: String, limit: Int): DataResult<List<SearchHit>> = readLocally {
        val terms = SearchText.terms(query)
        repository.observeUpcomingLaunches().first()
            .asSequence()
            .filter { SearchText.matchesAll(terms, it.name, it.rocket, it.pad) }
            .take(limit)
            .map { launch ->
                SearchHit(
                    source = source,
                    id = launch.id,
                    title = launch.name,
                    target = SearchTarget.LaunchEntry(launch.id),
                    subtitle = launch.pad,
                    date = launch.net.atOffset(ZoneOffset.UTC).toLocalDate(),
                    imageUrl = launch.imageUrl,
                )
            }
            .toList()
    }
}

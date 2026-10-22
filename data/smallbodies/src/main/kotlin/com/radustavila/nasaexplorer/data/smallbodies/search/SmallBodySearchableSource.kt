package com.radustavila.nasaexplorer.data.smallbodies.search

import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.domain.repository.SmallBodiesRepository
import com.radustavila.nasaexplorer.core.domain.search.SearchText
import com.radustavila.nasaexplorer.core.domain.search.SearchableSource
import com.radustavila.nasaexplorer.core.domain.search.readLocally
import com.radustavila.nasaexplorer.core.model.SearchHit
import com.radustavila.nasaexplorer.core.model.SearchSource
import com.radustavila.nasaexplorer.core.model.SearchTarget
import java.time.ZoneOffset
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/**
 * Cached close approaches ("Apophis", "2026 UK"), soonest first. A month of approaches is a few
 * hundred rows, so they are filtered in memory.
 */
class SmallBodySearchableSource @Inject constructor(
    private val repository: SmallBodiesRepository,
) : SearchableSource {

    override val source = SearchSource.SMALL_BODIES

    override suspend fun search(query: String, limit: Int): DataResult<List<SearchHit>> = readLocally {
        val terms = SearchText.terms(query)
        repository.observeCloseApproaches().first()
            .asSequence()
            .filter { SearchText.matchesAll(terms, it.name, it.designation) }
            // An object passing twice is one result: its next approach.
            .distinctBy { it.designation }
            .take(limit)
            .map { approach ->
                SearchHit(
                    source = source,
                    id = approach.designation,
                    title = approach.name,
                    target = SearchTarget.SmallBody(approach.designation),
                    date = approach.time.atOffset(ZoneOffset.UTC).toLocalDate(),
                )
            }
            .toList()
    }
}

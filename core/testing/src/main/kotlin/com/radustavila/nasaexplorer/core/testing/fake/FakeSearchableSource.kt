package com.radustavila.nasaexplorer.core.testing.fake

import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.domain.search.SearchText
import com.radustavila.nasaexplorer.core.domain.search.SearchableSource
import com.radustavila.nasaexplorer.core.model.SearchHit
import com.radustavila.nasaexplorer.core.model.SearchSource
import kotlinx.coroutines.delay

/**
 * In-memory [SearchableSource]: answers with the seeded [hits] whose title, subtitle or snippet
 * contain every query term, in seeded order, after [delayMillis] of virtual time. Set [result] to
 * answer with something else (e.g. a failure), or [error] to throw.
 */
class FakeSearchableSource(
    override val source: SearchSource,
    var hits: List<SearchHit> = emptyList(),
) : SearchableSource {

    var result: DataResult<List<SearchHit>>? = null
    var error: Throwable? = null
    var delayMillis: Long = 0

    val queries = mutableListOf<String>()

    /** Most searches of this source in flight at once. */
    var maxInFlight: Int = 0
        private set

    private var inFlight: Int = 0

    override suspend fun search(query: String, limit: Int): DataResult<List<SearchHit>> {
        queries += query
        inFlight++
        maxInFlight = maxOf(maxInFlight, inFlight)
        try {
            delay(delayMillis)
            error?.let { throw it }
            result?.let { return it }
            val terms = SearchText.terms(query)
            return DataResult.Success(
                hits.filter { SearchText.matchesAll(terms, it.title, it.subtitle, it.snippet) }.take(limit),
            )
        } finally {
            inFlight--
        }
    }
}

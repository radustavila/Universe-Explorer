package com.radustavila.nasaexplorer.data.exoplanets.search

import com.radustavila.nasaexplorer.core.common.dispatchers.AppDispatcher
import com.radustavila.nasaexplorer.core.common.dispatchers.Dispatcher
import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.domain.search.SearchText
import com.radustavila.nasaexplorer.core.domain.search.SearchableSource
import com.radustavila.nasaexplorer.core.domain.search.readLocally
import com.radustavila.nasaexplorer.core.model.SearchHit
import com.radustavila.nasaexplorer.core.model.SearchSource
import com.radustavila.nasaexplorer.core.model.SearchTarget
import com.radustavila.nasaexplorer.data.exoplanets.local.ExoplanetDao
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/** Cached planets, by planet name or host star ("Kepler", "TRAPPIST-1 e"), by name. */
class ExoplanetSearchableSource @Inject constructor(
    private val dao: ExoplanetDao,
    @Dispatcher(AppDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
) : SearchableSource {

    override val source = SearchSource.EXOPLANETS

    override suspend fun search(query: String, limit: Int): DataResult<List<SearchHit>> = readLocally {
        val terms = SearchText.terms(query)
        val candidates = withContext(ioDispatcher) {
            dao.searchCandidates(term = terms.maxBy { it.length }, limit = CANDIDATE_LIMIT)
        }
        candidates.asSequence()
            .filter { SearchText.matchesAll(terms, it.name, it.hostName) }
            .take(limit)
            .map { planet ->
                SearchHit(
                    source = source,
                    id = planet.name,
                    title = planet.name,
                    target = SearchTarget.ExoplanetEntry(planet.name),
                    subtitle = planet.hostName,
                )
            }
            .toList()
    }

    private companion object {
        const val CANDIDATE_LIMIT = 200
    }
}

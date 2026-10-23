package com.radustavila.nasaexplorer.data.comets.search

import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.domain.repository.CometRepository
import com.radustavila.nasaexplorer.core.domain.search.SearchText
import com.radustavila.nasaexplorer.core.domain.search.SearchableSource
import com.radustavila.nasaexplorer.core.domain.search.readLocally
import com.radustavila.nasaexplorer.core.model.SearchHit
import com.radustavila.nasaexplorer.core.model.SearchSource
import com.radustavila.nasaexplorer.core.model.SearchTarget
import java.time.ZoneOffset
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/** Cached comets ("Bok", "2026 A2", "PANSTARRS"); a handful of rows, filtered in memory. */
internal class CometSearchableSource @Inject constructor(
    private val repository: CometRepository,
) : SearchableSource {

    override val source = SearchSource.COMETS

    override suspend fun search(query: String, limit: Int): DataResult<List<SearchHit>> = readLocally {
        val terms = SearchText.terms(query)
        repository.observeComets().first()
            .filter { SearchText.matchesAll(terms, it.name, it.designation) }
            .take(limit)
            .map { comet ->
                SearchHit(
                    source = source,
                    id = comet.designation,
                    title = comet.name,
                    target = SearchTarget.CometEntry(comet.designation),
                    date = comet.perihelionTime?.atOffset(ZoneOffset.UTC)?.toLocalDate(),
                )
            }
    }
}

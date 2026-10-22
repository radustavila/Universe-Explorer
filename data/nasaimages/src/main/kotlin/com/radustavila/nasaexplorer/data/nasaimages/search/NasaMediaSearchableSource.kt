package com.radustavila.nasaexplorer.data.nasaimages.search

import com.radustavila.nasaexplorer.core.common.dispatchers.AppDispatcher
import com.radustavila.nasaexplorer.core.common.dispatchers.Dispatcher
import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.domain.search.SearchText
import com.radustavila.nasaexplorer.core.domain.search.SearchableSource
import com.radustavila.nasaexplorer.core.domain.search.readLocally
import com.radustavila.nasaexplorer.core.model.SearchHit
import com.radustavila.nasaexplorer.core.model.SearchSource
import com.radustavila.nasaexplorer.core.model.SearchTarget
import com.radustavila.nasaexplorer.data.nasaimages.local.NasaMediaDao
import com.radustavila.nasaexplorer.data.nasaimages.mapper.toDomain
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * Library items on this device (the gallery rails and saved items), newest first. Searching the
 * whole library needs the network; the search screen offers that separately, through the gallery.
 */
class NasaMediaSearchableSource @Inject constructor(
    private val dao: NasaMediaDao,
    @Dispatcher(AppDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
) : SearchableSource {

    override val source = SearchSource.NASA_LIBRARY

    override suspend fun search(query: String, limit: Int): DataResult<List<SearchHit>> = readLocally {
        val terms = SearchText.terms(query)
        // SQLite narrows by the most selective term; the others are checked here.
        val candidates = withContext(ioDispatcher) {
            dao.searchCandidates(term = terms.maxBy { it.length }, limit = CANDIDATE_LIMIT)
        }
        candidates.asSequence()
            .map { it.toDomain() }
            .filter { SearchText.matchesAll(terms, it.title, it.description, it.keywords.joinToString(" ")) }
            .take(limit)
            .map { media ->
                SearchHit(
                    source = source,
                    id = media.nasaId,
                    title = media.title,
                    target = SearchTarget.NasaMediaItem(media.nasaId),
                    subtitle = media.center,
                    snippet = SearchText.snippet(media.description, terms),
                    date = media.dateCreated,
                    imageUrl = media.thumbnailUrl ?: media.previewUrl,
                )
            }
            .toList()
    }

    private companion object {
        const val CANDIDATE_LIMIT = 200
    }
}

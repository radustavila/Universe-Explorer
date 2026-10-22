package com.radustavila.nasaexplorer.data.news.search

import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.domain.repository.NewsRepository
import com.radustavila.nasaexplorer.core.domain.search.SearchText
import com.radustavila.nasaexplorer.core.domain.search.SearchableSource
import com.radustavila.nasaexplorer.core.domain.search.readLocally
import com.radustavila.nasaexplorer.core.model.SearchHit
import com.radustavila.nasaexplorer.core.model.SearchSource
import com.radustavila.nasaexplorer.core.model.SearchTarget
import java.time.ZoneOffset
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/** Cached news by title and summary ("Artemis", "Webb"), newest first. */
class NewsSearchableSource @Inject constructor(
    private val repository: NewsRepository,
) : SearchableSource {

    override val source = SearchSource.NEWS

    override suspend fun search(query: String, limit: Int): DataResult<List<SearchHit>> = readLocally {
        val terms = SearchText.terms(query)
        repository.observeNews().first()
            .asSequence()
            .filter { SearchText.matchesAll(terms, it.title, it.summary) }
            .take(limit)
            .map { item ->
                SearchHit(
                    source = source,
                    id = item.id,
                    title = item.title,
                    target = SearchTarget.NewsEntry(item.id),
                    snippet = SearchText.snippet(item.summary, terms),
                    date = item.publishedAt.atOffset(ZoneOffset.UTC).toLocalDate(),
                    imageUrl = item.imageUrl,
                )
            }
            .toList()
    }
}

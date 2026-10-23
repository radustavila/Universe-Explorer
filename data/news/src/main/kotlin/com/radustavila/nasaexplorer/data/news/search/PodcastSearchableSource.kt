package com.radustavila.nasaexplorer.data.news.search

import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.domain.repository.PodcastRepository
import com.radustavila.nasaexplorer.core.domain.search.SearchText
import com.radustavila.nasaexplorer.core.domain.search.SearchableSource
import com.radustavila.nasaexplorer.core.domain.search.readLocally
import com.radustavila.nasaexplorer.core.model.SearchHit
import com.radustavila.nasaexplorer.core.model.SearchSource
import com.radustavila.nasaexplorer.core.model.SearchTarget
import java.time.ZoneOffset
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/** Cached podcast episodes by title and show notes ("Artemis", "Roman"), newest first. */
class PodcastSearchableSource @Inject constructor(
    private val repository: PodcastRepository,
) : SearchableSource {

    override val source = SearchSource.PODCASTS

    override suspend fun search(query: String, limit: Int): DataResult<List<SearchHit>> = readLocally {
        val terms = SearchText.terms(query)
        repository.observeEpisodes().first()
            .asSequence()
            .filter { SearchText.matchesAll(terms, it.title, it.summary) }
            .take(limit)
            .map { episode ->
                SearchHit(
                    source = source,
                    id = episode.id,
                    title = episode.title,
                    target = SearchTarget.PodcastEpisodeEntry(episode.id),
                    snippet = SearchText.snippet(episode.summary, terms),
                    date = episode.publishedAt.atOffset(ZoneOffset.UTC).toLocalDate(),
                    imageUrl = episode.imageUrl,
                )
            }
            .toList()
    }
}

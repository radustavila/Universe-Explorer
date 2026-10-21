package com.radustavila.nasaexplorer.core.domain.search

import com.radustavila.nasaexplorer.core.common.error.AppError
import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.model.SearchHit
import com.radustavila.nasaexplorer.core.model.SearchSource
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull

/** One source's matches, best first. */
data class SearchResultGroup(
    val source: SearchSource,
    val hits: List<SearchHit>,
)

/**
 * Everything [UniversalSearchUseCase] found for [query]: the groups that matched, best group first,
 * and the sources that could not answer (their absence is not "no match").
 */
data class UniversalSearchResults(
    val query: String,
    val groups: List<SearchResultGroup> = emptyList(),
    val failedSources: Set<SearchSource> = emptySet(),
) {
    val isEmpty: Boolean get() = groups.isEmpty()
}

/**
 * Searches every [SearchableSource] on the device at once.
 *
 * Sources are asked concurrently, each with its own time limit, and a source that fails, throws or
 * runs out of time only lands in [UniversalSearchResults.failedSources]: the others still answer.
 * Hits are ranked by how well their title answers the query ([SearchText.score]), keeping each
 * source's own order (e.g. newest first) between equals; groups are ranked by their best hit, then
 * by [SearchSource] order.
 */
class UniversalSearchUseCase @Inject constructor(
    private val sources: Set<@JvmSuppressWildcards SearchableSource>,
) {

    suspend operator fun invoke(
        query: String,
        limitPerSource: Int = DEFAULT_LIMIT_PER_SOURCE,
    ): UniversalSearchResults {
        val trimmed = query.trim()
        if (SearchText.terms(trimmed).isEmpty()) return UniversalSearchResults(query = trimmed)

        val outcomes = coroutineScope {
            sources.sortedBy { it.source }.map { source ->
                async { source.source to source.searchSafely(trimmed, limitPerSource) }
            }.awaitAll()
        }

        val groups = outcomes.mapNotNull { (source, result) ->
            val hits = (result as? DataResult.Success)?.data
                ?.distinctBy { it.id }
                ?.map { hit -> hit to SearchText.score(hit.title, trimmed) }
                ?.sortedByDescending { (_, score) -> score }
                ?.takeIf { it.isNotEmpty() }
                ?: return@mapNotNull null
            RankedGroup(SearchResultGroup(source, hits.map { it.first }.take(limitPerSource)), hits.first().second)
        }
            .sortedWith(compareByDescending<RankedGroup> { it.bestScore }.thenBy { it.group.source })
            .map { it.group }

        return UniversalSearchResults(
            query = trimmed,
            groups = groups,
            failedSources = outcomes.filter { (_, result) -> result is DataResult.Failure }.map { it.first }.toSet(),
        )
    }

    private suspend fun SearchableSource.searchSafely(query: String, limit: Int): DataResult<List<SearchHit>> =
        withTimeoutOrNull(SOURCE_TIMEOUT_MILLIS) {
            try {
                search(query, limit)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (throwable: Throwable) {
                // A contract breach in one source must not take the whole search down.
                DataResult.Failure(AppError.Unknown(throwable.message))
            }
        } ?: DataResult.Failure(AppError.Timeout)

    private data class RankedGroup(val group: SearchResultGroup, val bestScore: Int)

    companion object {
        /** Enough for a group's "see all" list; the screen previews the first few. */
        const val DEFAULT_LIMIT_PER_SOURCE = 30

        /** Local reads answer in milliseconds; a source this slow is treated as failed. */
        const val SOURCE_TIMEOUT_MILLIS = 3_000L
    }
}

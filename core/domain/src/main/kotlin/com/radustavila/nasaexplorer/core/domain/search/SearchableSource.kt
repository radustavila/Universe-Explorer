package com.radustavila.nasaexplorer.core.domain.search

import com.radustavila.nasaexplorer.core.common.error.AppError
import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.model.SearchHit
import com.radustavila.nasaexplorer.core.model.SearchSource
import kotlinx.coroutines.CancellationException

/**
 * One local collection universal search can look in. Each data module that owns one contributes an
 * implementation to the Hilt set (`@Binds @IntoSet`), so [UniversalSearchUseCase] never names a
 * feature and a new source is one binding.
 *
 * Implementations read the device only: universal search is offline-first, and a source must
 * answer quickly whether or not there is a network.
 */
interface SearchableSource {

    val source: SearchSource

    /**
     * Up to [limit] hits for [query], in the source's own order (e.g. newest first); the use case
     * ranks them. A query matches when every one of its [SearchText.terms] appears somewhere in the
     * item. A failure is reported, never thrown: the other sources still answer.
     */
    suspend fun search(query: String, limit: Int): DataResult<List<SearchHit>>
}

/**
 * Runs a source's local read, reporting anything it throws as [AppError.Database] (cancellation
 * excepted), so implementations stay one expression.
 */
suspend fun readLocally(block: suspend () -> List<SearchHit>): DataResult<List<SearchHit>> = try {
    DataResult.Success(block())
} catch (cancellation: CancellationException) {
    throw cancellation
} catch (throwable: Throwable) {
    DataResult.Failure(AppError.Database(throwable.message))
}

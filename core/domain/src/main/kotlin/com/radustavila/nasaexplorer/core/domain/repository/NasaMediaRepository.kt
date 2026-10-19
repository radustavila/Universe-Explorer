package com.radustavila.nasaexplorer.core.domain.repository

import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.model.GalleryRail
import com.radustavila.nasaexplorer.core.model.NasaMedia
import com.radustavila.nasaexplorer.core.model.NasaMediaPage
import com.radustavila.nasaexplorer.core.model.NasaMediaQuery
import kotlinx.coroutines.flow.Flow

/**
 * The NASA Image and Video Library.
 *
 * - The curated rails are offline-first: observed from the local cache, which [refreshRails]
 *   replaces with fresh results.
 * - Search is online-first: [search] calls the library, keeping recent pages in memory only.
 * - Items the user saves are kept locally ([keep]), so they open offline.
 */
interface NasaMediaRepository {

    /** Cached items of every rail that has any, in rail order. */
    fun observeRails(): Flow<Map<GalleryRail, List<NasaMedia>>>

    /** Fetches every rail and replaces the cached ones; a failure leaves the cache untouched. */
    suspend fun refreshRails(): DataResult<Unit>

    suspend fun search(query: NasaMediaQuery, page: Int = 1): DataResult<NasaMediaPage>

    /**
     * One item, from the local cache or from a recent search; null while it is neither. Emits again
     * when [fetch] or [keep] brings it in.
     */
    fun observeMedia(nasaId: String): Flow<NasaMedia?>

    /** Downloads one item by id, e.g. a details screen opened after the search that found it is gone. */
    suspend fun fetch(nasaId: String): DataResult<NasaMedia>

    /** Stores [media] locally so it outlives the rails and the search that found it. */
    suspend fun keep(media: NasaMedia): DataResult<Unit>

    /** URL of the file to play for a video or audio item, picked from its renditions. */
    suspend fun resolvePlaybackUrl(media: NasaMedia): DataResult<String>
}

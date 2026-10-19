package com.radustavila.nasaexplorer.core.testing.fake

import com.radustavila.nasaexplorer.core.common.error.AppError
import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.domain.repository.NasaMediaRepository
import com.radustavila.nasaexplorer.core.model.GalleryRail
import com.radustavila.nasaexplorer.core.model.NasaMedia
import com.radustavila.nasaexplorer.core.model.NasaMediaPage
import com.radustavila.nasaexplorer.core.model.NasaMediaQuery
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * In-memory [NasaMediaRepository]: rails and kept items are a state the test seeds, and every remote
 * call answers with a scripted result.
 */
class FakeNasaMediaRepository(
    initialRails: Map<GalleryRail, List<NasaMedia>> = emptyMap(),
) : NasaMediaRepository {

    private val rails = MutableStateFlow(initialRails)
    private val stored = MutableStateFlow<Map<String, NasaMedia>>(emptyMap())

    /** Rails that [refreshRails] installs on success. */
    var refreshedRails: Map<GalleryRail, List<NasaMedia>>? = null
    var refreshResult: DataResult<Unit> = DataResult.Success(Unit)
    var refreshCount: Int = 0
        private set

    /** Pages [search] answers with, keyed by page number; a missing page fails with [searchError]. */
    var searchPages: Map<Int, NasaMediaPage> = emptyMap()
    var searchError: AppError = AppError.NoConnection
    val searchCalls: MutableList<Pair<NasaMediaQuery, Int>> = mutableListOf()

    var fetchResult: DataResult<NasaMedia>? = null
    var keepResult: DataResult<Unit> = DataResult.Success(Unit)
    var playbackResult: DataResult<String> = DataResult.Failure(AppError.NoConnection)

    val kept: Map<String, NasaMedia> get() = stored.value

    fun setRails(value: Map<GalleryRail, List<NasaMedia>>) {
        rails.value = value
    }

    fun store(media: NasaMedia) {
        stored.update { it + (media.nasaId to media) }
    }

    override fun observeRails(): Flow<Map<GalleryRail, List<NasaMedia>>> = rails

    override suspend fun refreshRails(): DataResult<Unit> {
        refreshCount++
        val result = refreshResult
        if (result is DataResult.Success) refreshedRails?.let { rails.value = it }
        return result
    }

    override suspend fun search(query: NasaMediaQuery, page: Int): DataResult<NasaMediaPage> {
        searchCalls += query to page
        val result = searchPages[page] ?: return DataResult.Failure(searchError)
        result.items.forEach(::store)
        return DataResult.Success(result)
    }

    override fun observeMedia(nasaId: String): Flow<NasaMedia?> = stored.map { items ->
        items[nasaId] ?: rails.value.values.flatten().firstOrNull { it.nasaId == nasaId }
    }

    override suspend fun fetch(nasaId: String): DataResult<NasaMedia> {
        val result = fetchResult ?: return DataResult.Failure(AppError.NoConnection)
        if (result is DataResult.Success) store(result.data)
        return result
    }

    override suspend fun keep(media: NasaMedia): DataResult<Unit> {
        val result = keepResult
        if (result is DataResult.Success) store(media)
        return result
    }

    override suspend fun resolvePlaybackUrl(media: NasaMedia): DataResult<String> = playbackResult
}

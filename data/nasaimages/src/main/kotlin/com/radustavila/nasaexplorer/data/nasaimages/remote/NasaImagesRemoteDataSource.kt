package com.radustavila.nasaexplorer.data.nasaimages.remote

import com.radustavila.nasaexplorer.core.common.dispatchers.AppDispatcher
import com.radustavila.nasaexplorer.core.common.dispatchers.Dispatcher
import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.model.GalleryRail
import com.radustavila.nasaexplorer.core.model.NasaMediaQuery
import com.radustavila.nasaexplorer.core.model.NasaMediaType
import com.radustavila.nasaexplorer.core.network.http.safeNetworkCall
import com.radustavila.nasaexplorer.data.nasaimages.remote.dto.AssetResponseDto
import com.radustavila.nasaexplorer.data.nasaimages.remote.dto.SearchResponseDto
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/** Remote boundary of the library module: every call returns a [DataResult], never throws. */
interface NasaImagesRemoteDataSource {
    suspend fun fetchRail(rail: GalleryRail): DataResult<SearchResponseDto>
    suspend fun search(query: NasaMediaQuery, page: Int): DataResult<SearchResponseDto>
    suspend fun findById(nasaId: String): DataResult<SearchResponseDto>
    suspend fun fetchAssets(nasaId: String): DataResult<AssetResponseDto>

    companion object {
        /** Items per rail: enough to scroll through, small enough to cache all six cheaply. */
        const val RAIL_SIZE = 24

        const val SEARCH_PAGE_SIZE = 30

        /** The library refuses to page past this many hits of one query. */
        const val MAX_RESULTS = 10_000
    }
}

class RetrofitNasaImagesRemoteDataSource @Inject constructor(
    private val service: NasaImagesService,
    @Dispatcher(AppDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
) : NasaImagesRemoteDataSource {

    override suspend fun fetchRail(rail: GalleryRail): DataResult<SearchResponseDto> = call {
        val query = rail.query
        service.search(
            query = query.text,
            keywords = query.keywords,
            mediaType = NasaMediaType.IMAGE.apiValue,
            pageSize = NasaImagesRemoteDataSource.RAIL_SIZE,
        )
    }

    override suspend fun search(query: NasaMediaQuery, page: Int): DataResult<SearchResponseDto> = call {
        service.search(
            query = query.text,
            mediaType = query.mediaTypes
                .sortedBy(NasaMediaType::ordinal)
                .joinToString(",") { it.apiValue }
                .ifEmpty { null },
            yearStart = query.yearStart,
            yearEnd = query.yearEnd,
            page = page,
            pageSize = NasaImagesRemoteDataSource.SEARCH_PAGE_SIZE,
        )
    }

    override suspend fun findById(nasaId: String): DataResult<SearchResponseDto> =
        call { service.findById(nasaId) }

    override suspend fun fetchAssets(nasaId: String): DataResult<AssetResponseDto> =
        call { service.asset(nasaId) }

    private suspend fun <T> call(block: suspend () -> T): DataResult<T> =
        withContext(ioDispatcher) { safeNetworkCall(block) }
}

/** The library query behind a rail; `keywords` narrows a broad term to one telescope's images. */
internal data class RailQuery(val text: String, val keywords: String? = null)

/**
 * Queries chosen against the live library (2026-10-19). Webb's own nebula hits are thin, so the
 * nebula rail draws on Hubble.
 */
internal val GalleryRail.query: RailQuery
    get() = when (this) {
        GalleryRail.WEBB_GALAXIES -> RailQuery("galaxy", keywords = "James Webb Space Telescope")
        GalleryRail.HUBBLE_GALAXIES -> RailQuery("galaxy", keywords = "Hubble Space Telescope")
        GalleryRail.NEBULAE -> RailQuery("nebula", keywords = "Hubble")
        GalleryRail.BLACK_HOLES -> RailQuery("black hole")
        GalleryRail.MILKY_WAY -> RailQuery("milky way")
        GalleryRail.WEBB -> RailQuery("webb")
    }

package com.radustavila.nasaexplorer.data.nasaimages.repository

import com.radustavila.nasaexplorer.core.common.error.AppError
import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.common.result.map
import com.radustavila.nasaexplorer.core.common.time.TimeProvider
import com.radustavila.nasaexplorer.core.domain.repository.NasaMediaRepository
import com.radustavila.nasaexplorer.core.model.GalleryRail
import com.radustavila.nasaexplorer.core.model.NasaMedia
import com.radustavila.nasaexplorer.core.model.NasaMediaPage
import com.radustavila.nasaexplorer.core.model.NasaMediaQuery
import com.radustavila.nasaexplorer.data.nasaimages.local.NasaImagesLocalDataSource
import com.radustavila.nasaexplorer.data.nasaimages.mapper.playbackUrlFor
import com.radustavila.nasaexplorer.data.nasaimages.mapper.toDomainOrNull
import com.radustavila.nasaexplorer.data.nasaimages.mapper.toPage
import com.radustavila.nasaexplorer.data.nasaimages.remote.NasaImagesRemoteDataSource
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * The library, offline-first where it matters:
 *
 * - Rails are read from Room; [refreshRails] fetches all of them and replaces the cached set in one
 *   transaction, or changes nothing at all when any rail fails.
 * - Search goes to the network, with recent pages held in memory for [SEARCH_PAGE_TTL] so paging
 *   back and forth, rotating or reopening a result does not repeat calls.
 * - Items found by a search are remembered in memory, so their details open without another call;
 *   only [keep] (saving a favourite) writes one to Room.
 */
@Singleton
class NasaMediaRepositoryImpl @Inject constructor(
    private val localDataSource: NasaImagesLocalDataSource,
    private val remoteDataSource: NasaImagesRemoteDataSource,
    private val timeProvider: TimeProvider,
) : NasaMediaRepository {

    private val searchPages = SearchPageCache(
        ttl = SEARCH_PAGE_TTL,
        maxPages = MAX_CACHED_PAGES,
        timeProvider = timeProvider,
    )
    private val recentItems = MutableStateFlow<Map<String, NasaMedia>>(emptyMap())
    private val playbackUrls = ConcurrentHashMap<String, String>()

    override fun observeRails(): Flow<Map<GalleryRail, List<NasaMedia>>> = localDataSource.observeRails()

    override suspend fun refreshRails(): DataResult<Unit> {
        val responses = coroutineScope {
            GalleryRail.entries.map { rail -> async { rail to remoteDataSource.fetchRail(rail) } }.awaitAll()
        }
        responses.firstNotNullOfOrNull { (_, result) -> result as? DataResult.Failure }?.let { return it }

        val rails = responses
            .associate { (rail, result) ->
                rail to (result as DataResult.Success).data.collection.items.mapNotNull { it.toDomainOrNull() }
            }
            .filterValues(List<NasaMedia>::isNotEmpty)
        // Nothing usable at all: keep showing the old rails rather than an empty tab.
        if (rails.isEmpty()) return DataResult.Failure(AppError.EmptyResponse)

        return localDataSource.replaceRails(rails, timeProvider.now())
    }

    override suspend fun search(query: NasaMediaQuery, page: Int): DataResult<NasaMediaPage> {
        searchPages.get(query, page)?.let { cached -> return DataResult.Success(cached) }

        return remoteDataSource.search(query, page)
            .map { response -> response.toPage(page) }
            .also { result ->
                if (result is DataResult.Success) {
                    searchPages.put(query, page, result.data)
                    remember(result.data.items)
                }
            }
    }

    override fun observeMedia(nasaId: String): Flow<NasaMedia?> = combine(
        localDataSource.observeMedia(nasaId),
        recentItems.map { it[nasaId] },
    ) { stored, found -> stored ?: found }
        .distinctUntilChanged()

    override suspend fun fetch(nasaId: String): DataResult<NasaMedia> =
        when (val result = remoteDataSource.findById(nasaId)) {
            is DataResult.Success -> {
                val media = result.data.collection.items
                    .mapNotNull { it.toDomainOrNull() }
                    .firstOrNull { it.nasaId == nasaId }
                if (media == null) {
                    DataResult.Failure(AppError.EmptyResponse)
                } else {
                    remember(listOf(media))
                    DataResult.Success(media)
                }
            }

            is DataResult.Failure -> result
        }

    override suspend fun keep(media: NasaMedia): DataResult<Unit> =
        localDataSource.store(media, timeProvider.now())

    override suspend fun resolvePlaybackUrl(media: NasaMedia): DataResult<String> {
        playbackUrls[media.nasaId]?.let { return DataResult.Success(it) }

        return when (val result = remoteDataSource.fetchAssets(media.nasaId)) {
            is DataResult.Success -> {
                val url = result.data.playbackUrlFor(media.mediaType)
                if (url == null) {
                    DataResult.Failure(AppError.EmptyResponse)
                } else {
                    playbackUrls[media.nasaId] = url
                    DataResult.Success(url)
                }
            }

            is DataResult.Failure -> result
        }
    }

    /** Bounded: the oldest entries go first once [MAX_RECENT_ITEMS] is exceeded. */
    private fun remember(items: List<NasaMedia>) {
        if (items.isEmpty()) return
        recentItems.update { current ->
            val merged = LinkedHashMap(current)
            items.forEach { item ->
                merged.remove(item.nasaId)
                merged[item.nasaId] = item
            }
            merged.entries.toList().takeLast(MAX_RECENT_ITEMS).associate { it.toPair() }
        }
    }

    private companion object {
        val SEARCH_PAGE_TTL: Duration = Duration.ofMinutes(10)
        const val MAX_CACHED_PAGES = 40
        const val MAX_RECENT_ITEMS = 600
    }
}

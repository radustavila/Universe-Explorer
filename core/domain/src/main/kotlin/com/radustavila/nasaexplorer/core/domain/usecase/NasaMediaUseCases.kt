package com.radustavila.nasaexplorer.core.domain.usecase

import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.common.result.flatMap
import com.radustavila.nasaexplorer.core.domain.repository.NasaMediaRepository
import com.radustavila.nasaexplorer.core.domain.sync.SourceRefreshResult
import com.radustavila.nasaexplorer.core.model.FavoriteSource
import com.radustavila.nasaexplorer.core.model.GalleryRail
import com.radustavila.nasaexplorer.core.model.NasaMedia
import com.radustavila.nasaexplorer.core.model.NasaMediaPage
import com.radustavila.nasaexplorer.core.model.NasaMediaQuery
import com.radustavila.nasaexplorer.core.model.SyncSource
import java.time.Duration
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

/** The Universe gallery's curated rails, straight from the local cache. */
class ObserveGalleryRailsUseCase @Inject constructor(
    private val repository: NasaMediaRepository,
) {
    operator fun invoke(): Flow<Map<GalleryRail, List<NasaMedia>>> = repository.observeRails()
}

/**
 * Refreshes the rails at most once per [RAIL_MAX_AGE] (they change slowly, and the cache is what
 * the tab opens on), unless [force]d by pull-to-refresh.
 */
class RefreshGalleryUseCase @Inject constructor(
    private val repository: NasaMediaRepository,
    private val refreshSource: RefreshSourceUseCase,
) {
    suspend operator fun invoke(force: Boolean = false): SourceRefreshResult<Unit> =
        refreshSource(SyncSource.NASA_LIBRARY, minInterval = RAIL_MAX_AGE, force = force) {
            repository.refreshRails()
        }

    companion object {
        val RAIL_MAX_AGE: Duration = Duration.ofHours(24)
    }
}

class SearchNasaMediaUseCase @Inject constructor(
    private val repository: NasaMediaRepository,
) {
    suspend operator fun invoke(query: NasaMediaQuery, page: Int = 1): DataResult<NasaMediaPage> =
        repository.search(query.copy(text = query.text.trim()), page)
}

class ObserveNasaMediaUseCase @Inject constructor(
    private val repository: NasaMediaRepository,
) {
    operator fun invoke(nasaId: String): Flow<NasaMedia?> = repository.observeMedia(nasaId)
}

class FetchNasaMediaUseCase @Inject constructor(
    private val repository: NasaMediaRepository,
) {
    suspend operator fun invoke(nasaId: String): DataResult<NasaMedia> = repository.fetch(nasaId)
}

class ResolveNasaMediaPlaybackUseCase @Inject constructor(
    private val repository: NasaMediaRepository,
) {
    suspend operator fun invoke(media: NasaMedia): DataResult<String> =
        repository.resolvePlaybackUrl(media)
}

/**
 * Saves a library item to Favourites. The item itself is kept locally first, so the saved entry
 * opens offline in full, not only as the Favourites tab's snapshot.
 */
class SaveNasaMediaFavoriteUseCase @Inject constructor(
    private val repository: NasaMediaRepository,
    private val saveFavorite: SaveFavoriteUseCase,
) {
    suspend operator fun invoke(media: NasaMedia): DataResult<Unit> =
        repository.keep(media).flatMap {
            saveFavorite(
                source = FavoriteSource.NASA_LIBRARY,
                itemId = media.nasaId,
                title = media.title,
                imageUrl = media.previewUrl,
            )
        }
}

package com.radustavila.nasaexplorer.core.domain.offline

import com.radustavila.nasaexplorer.core.model.FavoriteSource
import kotlinx.coroutines.flow.Flow

/**
 * Offline copies of favourite images, kept in app-private storage that no cache eviction touches.
 *
 * The image loader's disk cache is an LRU that a busy afternoon of browsing can trim, so a favourite
 * would otherwise lose its picture offline. Copies are downloaded in the background (network
 * constrained, retried) and deleted with the favourite; screens never wait for them, and keep using
 * the favourite's remote URL until one exists.
 */
interface OfflineFavoriteImages {

    /** Queues a background download of the image of the favourite `(source, itemId)`. */
    fun requestDownload(source: FavoriteSource, itemId: String)

    /** Cancels a pending download and deletes the offline copy of an item that is no longer saved. */
    suspend fun discard(source: FavoriteSource, itemId: String)

    /**
     * Queues a download for every favourite saved before offline copies existed. Does the work once
     * per install; later calls return straight away.
     */
    suspend fun backfillOnce()
}

/** How much space the offline copies of favourite images take, for a storage overview in settings. */
interface OfflineFavoritesStorage {

    /** Total size of every offline copy, in bytes; updates as copies are added and deleted. */
    fun observeSizeBytes(): Flow<Long>
}

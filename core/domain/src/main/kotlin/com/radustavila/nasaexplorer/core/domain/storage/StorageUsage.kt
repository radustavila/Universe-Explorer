package com.radustavila.nasaexplorer.core.domain.storage

/**
 * What one part of the app's on-disk storage holds. The Settings screen names each kind; a new
 * kind of storage adds an entry here and a [StorageUsageSource] that measures it.
 */
enum class StorageKind {
    /** Downloaded images (the image loader's disk cache). Cleared by "Clear cache". */
    IMAGE_CACHE,

    /** API responses kept by the HTTP client per the servers' caching headers. Cleared by "Clear cache". */
    HTTP_CACHE,

    /**
     * Local copies of the remote feeds (launches, Earth events, exoplanets, Mars, the NASA library,
     * asteroids, space weather, orbital elements). "Clear cache" empties them down to what their
     * evictors always keep; favourites are never deleted.
     */
    CACHED_DATA,

    /** Pictures of the day, Earth photos and the favourites themselves: the user's library. */
    LIBRARY,

    /** Offline copies of favourite images. Never cleared by "Clear cache"; deleted with the favourite. */
    OFFLINE_FAVORITES,

    /**
     * Podcast episodes downloaded for offline listening (F21). "Clear cache" deletes them, except
     * those of favourite episodes.
     */
    PODCAST_DOWNLOADS,

    /**
     * The Moon map's deeper tiles (zoom 5–6, F25), an on-demand Play asset pack the user asked for.
     * Not cleared by "Clear cache"; the Moon map removes them.
     */
    MOON_TILES,
}

/** One measured entry, in bytes. */
data class StorageEntry(val kind: StorageKind, val bytes: Long)

/**
 * Measures one or more kinds of storage. Implementations live next to what they measure and are
 * contributed into a set:
 *
 * ```
 * @Binds @IntoSet
 * fun bindHttpCacheStorage(impl: HttpCacheStorage): StorageUsageSource
 * ```
 */
interface StorageUsageSource {
    /** Sizes as they are now; may read the disk, so call off the main thread. */
    suspend fun measure(): List<StorageEntry>
}

/**
 * Empties one cache the app can rebuild from the network, for "Clear cache". Contributed into a set
 * like [StorageUsageSource]. Must never delete a favourite or an offline copy of one.
 */
interface CacheCleaner {
    suspend fun clear()
}

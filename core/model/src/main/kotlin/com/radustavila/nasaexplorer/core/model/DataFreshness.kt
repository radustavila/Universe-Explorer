package com.radustavila.nasaexplorer.core.model

import java.time.Instant

/**
 * How current the cached data on a screen is, for the "Updated 5 min ago" line every data screen
 * shows.
 *
 * @property lastUpdatedAt when the screen's source last synced successfully; `null` before it ever
 * did, when there is nothing to say yet.
 * @property isOffline the device has no connection, so what is shown is all there is.
 * @property lastRefreshFailed the most recent attempt failed, so the data may be older than usual.
 */
data class DataFreshness(
    val lastUpdatedAt: Instant? = null,
    val isOffline: Boolean = false,
    val lastRefreshFailed: Boolean = false,
)

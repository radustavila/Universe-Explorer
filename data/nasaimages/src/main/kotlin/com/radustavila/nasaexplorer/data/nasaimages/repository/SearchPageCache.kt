package com.radustavila.nasaexplorer.data.nasaimages.repository

import com.radustavila.nasaexplorer.core.common.time.TimeProvider
import com.radustavila.nasaexplorer.core.model.NasaMediaPage
import com.radustavila.nasaexplorer.core.model.NasaMediaQuery
import java.time.Duration
import java.time.Instant

/**
 * Short-lived, in-memory cache of search result pages: a page is served for [ttl] after it was
 * fetched, and only the [maxPages] most recently used are kept. Nothing is written to disk.
 */
internal class SearchPageCache(
    private val ttl: Duration,
    private val maxPages: Int,
    private val timeProvider: TimeProvider,
) {
    private data class Key(val query: NasaMediaQuery, val page: Int)
    private class Entry(val page: NasaMediaPage, val fetchedAt: Instant)

    // Access order, so iteration starts at the least recently used page.
    private val entries = LinkedHashMap<Key, Entry>(maxPages, LOAD_FACTOR, true)

    @Synchronized
    fun get(query: NasaMediaQuery, page: Int): NasaMediaPage? {
        val key = Key(query, page)
        val entry = entries[key] ?: return null
        if (timeProvider.now().isAfter(entry.fetchedAt.plus(ttl))) {
            entries.remove(key)
            return null
        }
        return entry.page
    }

    @Synchronized
    fun put(query: NasaMediaQuery, page: Int, value: NasaMediaPage) {
        entries[Key(query, page)] = Entry(value, timeProvider.now())
        while (entries.size > maxPages) {
            entries.remove(entries.keys.first())
        }
    }

    private companion object {
        const val LOAD_FACTOR = 0.75f
    }
}

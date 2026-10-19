package com.radustavila.nasaexplorer.core.domain.repository

import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.model.FavoriteItem
import com.radustavila.nasaexplorer.core.model.FavoriteSource
import kotlinx.coroutines.flow.Flow

/**
 * Favourites of every source, keyed by `(source, itemId)`. Purely local: nothing here touches the
 * network, and a favourite outlives the cache entry it was saved from.
 */
interface FavoritesRepository {

    /** Every favourite, most recently saved first. */
    fun observeFavorites(): Flow<List<FavoriteItem>>

    /** Ids of the favourites of one source, for marking items in a list. */
    fun observeFavoriteIds(source: FavoriteSource): Flow<Set<String>>

    fun observeIsFavorite(source: FavoriteSource, itemId: String): Flow<Boolean>

    fun observeCount(): Flow<Int>

    /** Saves [item], or refreshes its snapshot when it is already saved (keeping its save time). */
    suspend fun save(item: FavoriteItem): DataResult<Unit>

    suspend fun remove(source: FavoriteSource, itemId: String): DataResult<Unit>
}

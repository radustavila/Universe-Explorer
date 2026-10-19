package com.radustavila.nasaexplorer.core.testing.fake

import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.domain.repository.FavoritesRepository
import com.radustavila.nasaexplorer.core.model.FavoriteItem
import com.radustavila.nasaexplorer.core.model.FavoriteSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** In-memory [FavoritesRepository] keyed by `(source, itemId)`, like the `favorite` table. */
class FakeFavoritesRepository(initial: List<FavoriteItem> = emptyList()) : FavoritesRepository {

    private val items = MutableStateFlow(initial.associateBy { it.source to it.itemId })

    /** When set, every write fails with this result instead of changing anything. */
    var writeResult: DataResult<Unit>? = null

    val current: List<FavoriteItem> get() = items.value.values.sortedByDescending(FavoriteItem::savedAt)

    /** Puts [item] in place directly, bypassing [writeResult]. */
    fun seed(item: FavoriteItem) {
        items.update { map -> map + ((item.source to item.itemId) to item) }
    }

    override fun observeFavorites(): Flow<List<FavoriteItem>> =
        items.map { map -> map.values.sortedByDescending(FavoriteItem::savedAt) }

    override fun observeFavoriteIds(source: FavoriteSource): Flow<Set<String>> =
        items.map { map -> map.keys.filter { it.first == source }.map { it.second }.toSet() }

    override fun observeIsFavorite(source: FavoriteSource, itemId: String): Flow<Boolean> =
        items.map { map -> (source to itemId) in map }

    override fun observeCount(): Flow<Int> = items.map { it.size }

    override suspend fun save(item: FavoriteItem): DataResult<Unit> {
        writeResult?.let { forced -> return forced }
        items.update { map ->
            val key = item.source to item.itemId
            // Like the real table: a re-save refreshes the snapshot but keeps the original save time.
            map + (key to (map[key]?.let { item.copy(savedAt = it.savedAt) } ?: item))
        }
        return DataResult.Success(Unit)
    }

    override suspend fun remove(source: FavoriteSource, itemId: String): DataResult<Unit> {
        writeResult?.let { forced -> return forced }
        items.update { map -> map - (source to itemId) }
        return DataResult.Success(Unit)
    }
}

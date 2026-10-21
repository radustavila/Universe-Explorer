package com.radustavila.nasaexplorer.core.testing.fake

import com.radustavila.nasaexplorer.core.domain.offline.OfflineFavoriteImages
import com.radustavila.nasaexplorer.core.domain.offline.OfflineFavoritesStorage
import com.radustavila.nasaexplorer.core.model.FavoriteSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** Records what was asked of [OfflineFavoriteImages] instead of downloading anything. */
class FakeOfflineFavoriteImages : OfflineFavoriteImages {

    val requestedDownloads = mutableListOf<Pair<FavoriteSource, String>>()
    val discarded = mutableListOf<Pair<FavoriteSource, String>>()
    var backfillCount = 0
        private set

    override fun requestDownload(source: FavoriteSource, itemId: String) {
        requestedDownloads += source to itemId
    }

    override suspend fun discard(source: FavoriteSource, itemId: String) {
        discarded += source to itemId
    }

    override suspend fun backfillOnce() {
        backfillCount++
    }
}

/** [OfflineFavoritesStorage] whose size a test sets directly. */
class FakeOfflineFavoritesStorage(initialBytes: Long = 0) : OfflineFavoritesStorage {

    val sizeBytes = MutableStateFlow(initialBytes)

    override fun observeSizeBytes(): Flow<Long> = sizeBytes
}

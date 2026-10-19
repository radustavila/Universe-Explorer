package com.radustavila.nasaexplorer.core.model

import java.time.Instant

/**
 * A saved item from any [FavoriteSource].
 *
 * [title] and [imageUrl] are a snapshot taken when the item was saved, so the Favourites tab renders
 * offline even after the source's own cache has been trimmed.
 */
data class FavoriteItem(
    val source: FavoriteSource,
    val itemId: String,
    val title: String,
    val imageUrl: String?,
    val savedAt: Instant = Instant.EPOCH,
)

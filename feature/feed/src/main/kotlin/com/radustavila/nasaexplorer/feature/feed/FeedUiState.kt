package com.radustavila.nasaexplorer.feature.feed

import com.radustavila.nasaexplorer.core.designsystem.error.UiError
import com.radustavila.nasaexplorer.core.model.Apod
import java.time.LocalDate

/**
 * Complete, immutable description of what the feed screen shows. The screen renders this and
 * nothing else.
 */
data class FeedUiState(
    val items: List<Apod> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val isOffline: Boolean = false,
    val hasReachedArchiveStart: Boolean = false,
    val error: UiError? = null,
    /** Newest date the archive can hold; the date picker refuses anything after it. */
    val latestSelectableDate: LocalDate = Apod.FIRST_PUBLICATION_DATE,
    /** The user's choice only; whether the system currently allows notifications is checked by the screen. */
    val areNewApodNotificationsEnabled: Boolean = false,
    val isDailyWallpaperEnabled: Boolean = false,
    /** Today's month and day in earlier years, newest first, as far as the cache holds them. */
    val onThisDay: List<Apod> = emptyList(),
    /** `null` while the feed is shown; the search over the local cache otherwise. */
    val search: FeedSearchState? = null,
) {
    val hasContent: Boolean get() = items.isNotEmpty()

    /** Nothing cached and nothing in flight: the screen must offer a way forward. */
    val isEmpty: Boolean get() = items.isEmpty() && !isLoading

    /** Empty *because* there is no connection - a different message and a different affordance. */
    val isOfflineEmpty: Boolean get() = isEmpty && isOffline
}

/**
 * Search over the entries cached on the device. The APOD service cannot search, so [results] are
 * only ever drawn from the cache, and the screen says so.
 */
data class FeedSearchState(
    val query: String = "",
    val results: List<Apod> = emptyList(),
) {
    /** Nothing typed yet: the screen explains what can be searched rather than "no results". */
    val isBlank: Boolean get() = query.isBlank()

    val hasNoResults: Boolean get() = !isBlank && results.isEmpty()
}

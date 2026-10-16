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
) {
    val hasContent: Boolean get() = items.isNotEmpty()

    /** Nothing cached and nothing in flight: the screen must offer a way forward. */
    val isEmpty: Boolean get() = items.isEmpty() && !isLoading

    /** Empty *because* there is no connection - a different message and a different affordance. */
    val isOfflineEmpty: Boolean get() = isEmpty && isOffline
}

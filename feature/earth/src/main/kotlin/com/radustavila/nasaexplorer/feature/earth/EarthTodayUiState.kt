package com.radustavila.nasaexplorer.feature.earth

import com.radustavila.nasaexplorer.core.designsystem.error.UiError
import com.radustavila.nasaexplorer.core.model.EpicCollection
import com.radustavila.nasaexplorer.core.model.EpicImage
import java.time.LocalDate

/** Everything the Earth today screen shows, and nothing else. */
data class EarthTodayUiState(
    /** One day's photos, in capture order: the frames of the rotation. */
    val images: List<EpicImage> = emptyList(),
    val collection: EpicCollection = EpicCollection.NATURAL,
    /** The day the user picked, or `null` for "the newest day there is". */
    val selectedDate: LocalDate? = null,
    /** Days the date picker may offer, as published by EPIC. Empty until the first sync. */
    val availableDates: Set<LocalDate> = emptySet(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isOffline: Boolean = false,
    val error: UiError? = null,
) {
    val hasContent: Boolean get() = images.isNotEmpty()

    /** The day on screen: the photos' own day once they are there, the requested one before. */
    val displayedDate: LocalDate? get() = images.firstOrNull()?.date ?: selectedDate

    val isEmpty: Boolean get() = images.isEmpty() && !isLoading

    val isOfflineEmpty: Boolean get() = isEmpty && isOffline

    val canPickDate: Boolean get() = availableDates.isNotEmpty()
}

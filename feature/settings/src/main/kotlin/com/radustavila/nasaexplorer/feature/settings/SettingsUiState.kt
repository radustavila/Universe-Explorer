package com.radustavila.nasaexplorer.feature.settings

import androidx.compose.runtime.Immutable
import com.radustavila.nasaexplorer.core.domain.storage.StorageEntry
import com.radustavila.nasaexplorer.core.model.AuroraAlertSettings
import com.radustavila.nasaexplorer.core.model.ObserverLocation
import com.radustavila.nasaexplorer.core.model.UnitPreferences

@Immutable
data class SettingsUiState(
    val units: UnitPreferences = UnitPreferences(),
    val newApodNotificationsEnabled: Boolean = false,
    val auroraAlerts: AuroraAlertSettings = AuroraAlertSettings(),
    val issRemindersEnabled: Boolean = false,
    /** The saved observer location, which ISS reminders need and aurora alerts can use. */
    val observerLocation: ObserverLocation? = null,
    val dailyWallpaperEnabled: Boolean = false,
    val storage: StorageUiState = StorageUiState(),
    val message: SettingsMessage? = null,
) {
    /** The aurora place can be set from the saved observer location, which differs from it. */
    val canUseSavedPlaceForAurora: Boolean
        get() = observerLocation != null && (
            auroraAlerts.latitude != observerLocation.coordinate.latitude ||
                auroraAlerts.longitude != observerLocation.coordinate.longitude
            )
}

@Immutable
data class StorageUiState(
    /** Null until first measured. */
    val entries: List<StorageEntry>? = null,
    val isClearing: Boolean = false,
) {
    val totalBytes: Long? get() = entries?.sumOf(StorageEntry::bytes)
}

/** One-off feedback, shown as a snackbar and then cleared with `onMessageShown`. */
enum class SettingsMessage {
    CACHE_CLEARED,
    CACHE_CLEAR_FAILED,

    /** Aurora alerts were turned on without any place to compute them for. */
    AURORA_NEEDS_PLACE,
}

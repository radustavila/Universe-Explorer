package com.radustavila.nasaexplorer.feature.sky

import androidx.compose.runtime.Immutable
import com.radustavila.nasaexplorer.core.designsystem.error.UiError
import com.radustavila.nasaexplorer.core.domain.sky.IssPassForecast
import com.radustavila.nasaexplorer.core.domain.sky.IssTrack
import com.radustavila.nasaexplorer.core.model.City
import com.radustavila.nasaexplorer.core.model.DataFreshness
import com.radustavila.nasaexplorer.core.model.ObserverLocation
import com.radustavila.nasaexplorer.core.model.PersonInSpace
import com.radustavila.nasaexplorer.core.model.SkyTonight

@Immutable
data class SkyTonightUiState(
    /** False until the saved location has been read, so the prompt does not flash. */
    val isLocationLoaded: Boolean = false,
    val location: ObserverLocation? = null,
    /** Null while being computed (a fraction of a second) or without a location. */
    val sky: SkyTonight? = null,
    val passes: IssPassForecast? = null,
    val track: IssTrack? = null,
    val people: List<PersonInSpace> = emptyList(),
    val remindersEnabled: Boolean = false,
    val isLocating: Boolean = false,
    val isRefreshing: Boolean = false,
    val isOffline: Boolean = false,
    val citySearch: CitySearchUiState = CitySearchUiState(),
    val message: SkyMessage? = null,
    /** When the ISS orbit and the crew list last synced; the rest is computed on the device. */
    val freshness: DataFreshness = DataFreshness(),
)

@Immutable
data class CitySearchUiState(
    val query: String = "",
    val results: List<City> = emptyList(),
)

/** One-off feedback, shown as a snackbar and then cleared with `onMessageShown`. */
sealed interface SkyMessage {
    /** Location access was refused; a city can still be picked. */
    data object LocationPermissionDenied : SkyMessage

    /** Location is off, or no fix arrived. */
    data object LocationUnavailable : SkyMessage

    data class RefreshFailed(val error: UiError) : SkyMessage
}

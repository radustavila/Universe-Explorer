package com.radustavila.nasaexplorer.feature.spaceweather

import androidx.compose.runtime.Immutable
import com.radustavila.nasaexplorer.core.map.MapCamera
import com.radustavila.nasaexplorer.core.map.overlay.GeoPoint
import com.radustavila.nasaexplorer.core.map.overlay.MapOverlay
import com.radustavila.nasaexplorer.core.model.AuroraAlertSettings
import com.radustavila.nasaexplorer.core.model.CoronalMassEjection
import com.radustavila.nasaexplorer.core.model.DataFreshness
import com.radustavila.nasaexplorer.core.model.KpReading
import com.radustavila.nasaexplorer.core.model.SolarFlare
import java.time.Instant

@Immutable
data class SpaceWeatherUiState(
    /** The gauge's value: the 1-minute estimate, or the current 3-hour slot without one. */
    val currentKp: Double? = null,
    val currentKpTime: Instant? = null,
    /** 3-hour slots from the current one onwards. */
    val forecast: List<KpReading> = emptyList(),
    val aurora: AuroraMapUi? = null,
    val hemisphere: Hemisphere = Hemisphere.North,
    val flare: SolarFlare? = null,
    val cme: CoronalMassEjection? = null,
    val alerts: AuroraAlertSettings = AuroraAlertSettings(),
    val isRefreshing: Boolean = false,
    val isOffline: Boolean = false,
    val lastUpdatedAt: Instant? = null,
    val lastRefreshFailed: Boolean = false,
) {
    val hasData: Boolean get() = currentKp != null || forecast.isNotEmpty()

    val freshness: DataFreshness
        get() = DataFreshness(lastUpdatedAt = lastUpdatedAt, isOffline = isOffline, lastRefreshFailed = lastRefreshFailed)
}

/** The aurora grid as map overlays, plus when NOAA observed and forecast it. */
@Immutable
data class AuroraMapUi(
    val overlays: List<MapOverlay>,
    val observedAt: Instant,
    val forecastFor: Instant,
)

enum class Hemisphere {
    North,
    South,
    ;

    /** Looking at the oval's side of the globe, centred on the user's longitude when known. */
    fun camera(longitude: Double?): MapCamera = MapCamera(
        center = GeoPoint(
            latitude = if (this == North) OVAL_LATITUDE else -OVAL_LATITUDE,
            longitude = longitude ?: if (this == North) NORTH_DEFAULT_LONGITUDE else SOUTH_DEFAULT_LONGITUDE,
        ),
        zoom = OVAL_ZOOM,
    )

    private companion object {
        const val OVAL_LATITUDE = 58.0
        const val OVAL_ZOOM = 0.6

        /** Over the North Atlantic, between the American and European ovals. */
        const val NORTH_DEFAULT_LONGITUDE = -30.0

        /** South of Australia and New Zealand, where most people see the aurora australis. */
        const val SOUTH_DEFAULT_LONGITUDE = 145.0
    }
}

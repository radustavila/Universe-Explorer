package com.radustavila.nasaexplorer.core.domain.location

import com.radustavila.nasaexplorer.core.model.GeoCoordinate

/** One-shot coarse position of the device. Asking for the permission is the UI's job. */
interface DeviceLocationProvider {

    /** Whether the user has granted coarse (or fine) location access. */
    fun hasPermission(): Boolean

    suspend fun currentLocation(): DeviceLocationResult
}

sealed interface DeviceLocationResult {
    data class Located(val coordinate: GeoCoordinate) : DeviceLocationResult

    /** The permission is not granted (any more). */
    data object PermissionDenied : DeviceLocationResult

    /** Location is switched off, or no fix arrived in time. */
    data object Unavailable : DeviceLocationResult
}

package com.radustavila.nasaexplorer.core.model

/** How the app learned where the user is. */
enum class LocationSource {
    /** The device's coarse location, after the user granted the permission. */
    DEVICE,

    /** A city or coordinates the user typed in. */
    MANUAL,
}

/**
 * Where the user watches the sky from. Sky tonight computes rise and set times and satellite passes
 * for it; aurora alerts compare its latitude with the forecast oval.
 *
 * Only a coarse position is ever kept: the sky looks the same across a city.
 */
data class ObserverLocation(
    val coordinate: GeoCoordinate,
    /** A place name to show ("Bucharest"), or null for raw coordinates or a device fix. */
    val name: String? = null,
    val source: LocationSource,
)

/** A named place the user can pick instead of sharing the device's location. */
data class City(
    val name: String,
    val country: String,
    val coordinate: GeoCoordinate,
)

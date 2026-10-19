package com.radustavila.nasaexplorer.data.exoplanets.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** One `pscomppars` row as the TAP service returns it with `format=json`. */
@Serializable
data class ExoplanetDto(
    @SerialName("pl_name") val name: String? = null,
    @SerialName("hostname") val hostName: String? = null,
    @SerialName("disc_year") val discoveryYear: Int? = null,
    /** `yyyy-MM`. */
    @SerialName("disc_pubdate") val discoveryPublished: String? = null,
    /** Short code: `tran`, `rv`, `micro`, `ima`, … */
    @SerialName("disc_method") val discoveryMethod: String? = null,
    @SerialName("disc_facility") val discoveryFacility: String? = null,
    /** Earth radii. */
    @SerialName("pl_rade") val radiusEarth: Double? = null,
    /** Earth masses (mass or minimum mass). */
    @SerialName("pl_bmasse") val massEarth: Double? = null,
    @SerialName("pl_orbper") val orbitalPeriodDays: Double? = null,
    /** Kelvin. */
    @SerialName("pl_eqt") val equilibriumTemperatureKelvin: Double? = null,
    /** Parsecs. */
    @SerialName("sy_dist") val distanceParsecs: Double? = null,
)

/** Result row of `select count(*)`. */
@Serializable
data class ExoplanetCountDto(
    @SerialName("count(*)") val count: Int? = null,
)

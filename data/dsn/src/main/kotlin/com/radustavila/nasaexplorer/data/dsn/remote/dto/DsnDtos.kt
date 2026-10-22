package com.radustavila.nasaexplorer.data.dsn.remote.dto

import kotlinx.serialization.Serializable

/**
 * `dsn.xml` as parsed: every attribute the app uses, still as the feed spells it (nullable where
 * the feed may leave it out or leave it empty). Mapping decides what is usable.
 */
data class DsnLiveDto(
    /** The feed's `<timestamp>`, epoch milliseconds. */
    val timestampMillis: Long?,
    val stations: List<DsnStationDto>,
)

/**
 * A `<station>` and the `<dish>` elements that follow it: the feed lists dishes as the station's
 * siblings, not its children.
 */
data class DsnStationDto(
    val name: String,
    val friendlyName: String?,
    val timeUtcMillis: Long?,
    val dishes: List<DsnDishDto>,
)

data class DsnDishDto(
    val name: String,
    val azimuth: Double?,
    val elevation: Double?,
    val activity: String?,
    val upSignals: List<DsnSignalDto>,
    val downSignals: List<DsnSignalDto>,
    val targets: List<DsnTargetDto>,
)

data class DsnSignalDto(
    val spacecraft: String?,
    val spacecraftId: Int?,
    val active: Boolean,
    val band: String?,
    val dataRate: Double?,
)

data class DsnTargetDto(
    val name: String,
    val id: Int?,
    /** km; -1 when unknown. */
    val uplegRange: Double?,
    val downlegRange: Double?,
)

/** `config.xml`: the sites with their dishes, and the spacecraft codes' names. */
data class DsnConfigDto(
    val sites: List<DsnConfigSiteDto>,
    val spacecraft: List<DsnConfigSpacecraftDto>,
)

data class DsnConfigSiteDto(
    val name: String,
    val friendlyName: String?,
    val dishes: List<DsnConfigDishDto>,
)

/** [type] is e.g. `70M`, `34M` or `34MHEF`. */
data class DsnConfigDishDto(val name: String, val type: String?)

data class DsnConfigSpacecraftDto(val name: String, val friendlyName: String?)

/** The Horizons API's JSON envelope: the ephemeris is plain text inside [result]. */
@Serializable
data class HorizonsResponseDto(
    val result: String? = null,
    val error: String? = null,
)

/** The first row of a Horizons observer table with quantity 20 (range and range rate). */
data class HorizonsRangeDto(
    val horizonsId: Int,
    val name: String,
    val epochMillis: Long,
    val deltaAu: Double,
    val rangeRateKmPerSecond: Double?,
)

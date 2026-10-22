package com.radustavila.nasaexplorer.core.model

import java.time.Instant

/**
 * The views of the Sun the app offers, each one of NASA's Solar Dynamics Observatory channels. The
 * UI names and explains them; the data layer knows which SDO file each one is.
 */
enum class SunChannel {
    /** HMI continuum: the surface as the eye would see it, with sunspots. */
    VISIBLE_LIGHT,

    /** HMI magnetogram: the magnetic polarity of the surface. */
    MAGNETIC_FIELD,

    /** AIA 304 Å: the chromosphere, filaments and prominences. */
    CHROMOSPHERE,

    /** AIA 171 Å: the quiet corona and its loops. */
    CORONA,

    /** AIA 193 Å: the hotter corona, where coronal holes show dark. */
    HOT_CORONA,

    /** AIA 131 Å: the hottest material, brightest in flares. */
    FLARES,
}

/**
 * The newest SDO picture of one [channel] that the device holds.
 *
 * @property localUrl the downloaded copy, readable offline; a new picture gets a new URL.
 * @property fullSizeUrl the same picture at full resolution, downloaded on demand.
 * @property updatedAt when SDO published the picture (its `Last-Modified`).
 */
data class SunImage(
    val channel: SunChannel,
    val localUrl: String,
    val fullSizeUrl: String,
    val updatedAt: Instant,
)

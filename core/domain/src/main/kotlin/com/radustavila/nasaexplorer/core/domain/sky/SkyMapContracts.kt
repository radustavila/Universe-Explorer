package com.radustavila.nasaexplorer.core.domain.sky

import com.radustavila.nasaexplorer.core.model.EquatorialCoordinate
import com.radustavila.nasaexplorer.core.model.GeoCoordinate
import com.radustavila.nasaexplorer.core.model.HorizontalCoordinate
import com.radustavila.nasaexplorer.core.model.OrbitalElements
import com.radustavila.nasaexplorer.core.model.SkyCatalog
import com.radustavila.nasaexplorer.core.model.SolarSystemPosition
import java.time.Instant
import kotlinx.coroutines.flow.Flow

/** The star catalogue and constellation figures bundled with the app: no network, read once. */
interface SkyCatalogRepository {

    /** Reads the bundled files on the first call and keeps them; later calls return the same catalogue. */
    suspend fun catalog(): SkyCatalog
}

/** Positions of what moves against the stars, computed on the device. */
interface SkyMapEphemeris {

    /** The Sun, Moon and planets above (or below) [observer] at [at]. */
    fun solarSystem(observer: GeoCoordinate, at: Instant): List<SolarSystemPosition>

    /** Where a satellite is in [observer]'s sky at [at], or null when its elements cannot be propagated there. */
    fun satellite(elements: OrbitalElements, observer: GeoCoordinate, at: Instant): HorizontalCoordinate?

    /** IAU abbreviation ("Ori") of the constellation that contains [position]. */
    fun constellationAt(position: EquatorialCoordinate): String
}

/** The sky map's own choices, kept across launches. */
interface SkyMapPreferencesRepository {

    /** Red night mode, which keeps eyes adapted to the dark. Off until switched on. */
    fun observeNightMode(): Flow<Boolean>

    suspend fun setNightMode(enabled: Boolean)

    /** Galaxies, clusters and nebulae on the map (F23). On until switched off. */
    fun observeDeepSkyLayer(): Flow<Boolean>

    suspend fun setDeepSkyLayer(enabled: Boolean)
}

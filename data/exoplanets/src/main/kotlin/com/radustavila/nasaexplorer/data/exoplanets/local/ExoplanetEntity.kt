package com.radustavila.nasaexplorer.data.exoplanets.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A cached `pscomppars` row, keyed by the archive's planet name.
 *
 * [fetchedAtMillis] is the time of the refresh that last returned the planet. Every planet of one
 * refresh carries the same value, which is how [ExoplanetDao.deleteFetchedBefore] tells planets
 * that have dropped out of the catalogue's filters from current ones.
 */
@Entity(
    tableName = "exoplanets",
    indices = [Index("disc_year"), Index("distance_pc"), Index("radius_earth")],
)
data class ExoplanetEntity(
    @PrimaryKey @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "host_name") val hostName: String,
    @ColumnInfo(name = "disc_year") val discoveryYear: Int,
    /** `yyyy-MM`, so it sorts chronologically as text. */
    @ColumnInfo(name = "disc_pubdate") val discoveryPublished: String?,
    /** [com.radustavila.nasaexplorer.core.model.DiscoveryMethod] name. */
    @ColumnInfo(name = "disc_method") val discoveryMethod: String,
    @ColumnInfo(name = "disc_facility") val discoveryFacility: String?,
    @ColumnInfo(name = "radius_earth") val radiusEarth: Double?,
    @ColumnInfo(name = "mass_earth") val massEarth: Double?,
    @ColumnInfo(name = "orbital_period_days") val orbitalPeriodDays: Double?,
    @ColumnInfo(name = "equilibrium_temperature_k") val equilibriumTemperatureKelvin: Double?,
    @ColumnInfo(name = "distance_pc") val distanceParsecs: Double?,
    @ColumnInfo(name = "fetched_at") val fetchedAtMillis: Long,
)

/** Archive-wide figures; a single row. */
@Entity(tableName = "exoplanet_catalog_stats")
data class ExoplanetCatalogStatsEntity(
    @PrimaryKey val id: Int = SINGLE_ROW_ID,
    @ColumnInfo(name = "confirmed_planets") val confirmedPlanets: Int,
    @ColumnInfo(name = "fetched_at") val fetchedAtMillis: Long,
) {
    companion object {
        const val SINGLE_ROW_ID = 0
    }
}

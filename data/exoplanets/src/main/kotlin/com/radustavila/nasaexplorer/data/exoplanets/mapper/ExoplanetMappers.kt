package com.radustavila.nasaexplorer.data.exoplanets.mapper

import com.radustavila.nasaexplorer.core.model.DiscoveryMethod
import com.radustavila.nasaexplorer.core.model.Exoplanet
import com.radustavila.nasaexplorer.data.exoplanets.local.ExoplanetEntity
import com.radustavila.nasaexplorer.data.exoplanets.remote.dto.ExoplanetDto
import java.time.YearMonth
import java.time.format.DateTimeParseException

/**
 * Maps an archive row to a cache row, or `null` when the row cannot identify a planet (no name, no
 * host, no discovery year). Measurements outside physical range (zero or negative radius, mass or
 * distance) are dropped rather than drawn.
 */
fun ExoplanetDto.toEntityOrNull(fetchedAtMillis: Long): ExoplanetEntity? {
    val name = name?.trim()?.takeIf(String::isNotEmpty) ?: return null
    val hostName = hostName?.trim()?.takeIf(String::isNotEmpty) ?: return null
    val year = discoveryYear ?: return null
    return ExoplanetEntity(
        name = name,
        hostName = hostName,
        discoveryYear = year,
        discoveryPublished = discoveryPublished?.toYearMonthOrNull()?.toString(),
        discoveryMethod = DiscoveryMethod.fromArchive(discoveryMethod).name,
        discoveryFacility = discoveryFacility?.trim()?.takeIf(String::isNotEmpty),
        radiusEarth = radiusEarth.positiveOrNull(),
        massEarth = massEarth.positiveOrNull(),
        orbitalPeriodDays = orbitalPeriodDays.positiveOrNull(),
        equilibriumTemperatureKelvin = equilibriumTemperatureKelvin.positiveOrNull(),
        distanceParsecs = distanceParsecs.positiveOrNull(),
        fetchedAtMillis = fetchedAtMillis,
    )
}

fun ExoplanetEntity.toDomain(): Exoplanet = Exoplanet(
    name = name,
    hostName = hostName,
    discoveryYear = discoveryYear,
    discoveryPublished = discoveryPublished?.toYearMonthOrNull(),
    discoveryMethod = DiscoveryMethod.entries.firstOrNull { it.name == discoveryMethod } ?: DiscoveryMethod.OTHER,
    discoveryFacility = discoveryFacility,
    radiusEarth = radiusEarth,
    massEarth = massEarth,
    orbitalPeriodDays = orbitalPeriodDays,
    equilibriumTemperatureKelvin = equilibriumTemperatureKelvin,
    distanceParsecs = distanceParsecs,
)

/** `disc_pubdate` is `yyyy-MM`; a full date is cut to its month. */
private fun String.toYearMonthOrNull(): YearMonth? = try {
    YearMonth.parse(trim().take(YEAR_MONTH_LENGTH))
} catch (_: DateTimeParseException) {
    null
}

private fun Double?.positiveOrNull(): Double? = this?.takeIf { it.isFinite() && it > 0.0 }

private const val YEAR_MONTH_LENGTH = 7

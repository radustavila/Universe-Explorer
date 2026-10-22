package com.radustavila.nasaexplorer.data.forecast.mapper

import com.radustavila.nasaexplorer.core.model.CloudForecast
import com.radustavila.nasaexplorer.core.model.CloudForecastStep
import com.radustavila.nasaexplorer.data.forecast.local.ForecastPlaceEntity
import com.radustavila.nasaexplorer.data.forecast.local.ForecastStepEntity
import com.radustavila.nasaexplorer.data.forecast.remote.ForecastPlace
import com.radustavila.nasaexplorer.data.forecast.remote.dto.ForecastTimeStepDto
import com.radustavila.nasaexplorer.data.forecast.remote.dto.LocationForecastDto
import java.time.Instant
import java.time.format.DateTimeParseException

/** Steps without a readable time or a total cloud cover are dropped; the rest keep what they have. */
internal fun LocationForecastDto.toStepEntities(place: ForecastPlace): List<ForecastStepEntity> =
    properties.timeseries.mapNotNull { it.toEntityOrNull(place) }.distinctBy { it.time }

internal fun LocationForecastDto.updatedAtMillis(): Long? = properties.meta?.updatedAt?.parseInstantOrNull()?.toEpochMilli()

private fun ForecastTimeStepDto.toEntityOrNull(place: ForecastPlace): ForecastStepEntity? {
    val time = time.parseInstantOrNull() ?: return null
    val details = data.instant.details
    val cloudCover = details.cloudAreaFraction?.takeIf { it in PERCENT_RANGE } ?: return null
    return ForecastStepEntity(
        latitudeE2 = place.latitudeE2,
        longitudeE2 = place.longitudeE2,
        time = time.toEpochMilli(),
        cloudCover = cloudCover,
        lowCloudCover = details.cloudAreaFractionLow?.takeIf { it in PERCENT_RANGE },
        mediumCloudCover = details.cloudAreaFractionMedium?.takeIf { it in PERCENT_RANGE },
        highCloudCover = details.cloudAreaFractionHigh?.takeIf { it in PERCENT_RANGE },
        fogCover = details.fogAreaFraction?.takeIf { it in PERCENT_RANGE },
        relativeHumidity = details.relativeHumidity?.takeIf { it in PERCENT_RANGE },
        windSpeed = details.windSpeed,
        airTemperature = details.airTemperature,
    )
}

internal fun ForecastPlaceEntity.toDomain(steps: List<ForecastStepEntity>, place: ForecastPlace): CloudForecast =
    CloudForecast(
        coordinate = place.coordinate,
        steps = steps.map { it.toDomain() },
        fetchedAt = Instant.ofEpochMilli(fetchedAt),
        updatedAt = updatedAt?.let(Instant::ofEpochMilli),
    )

private fun ForecastStepEntity.toDomain() = CloudForecastStep(
    time = Instant.ofEpochMilli(time),
    cloudCover = cloudCover,
    lowCloudCover = lowCloudCover,
    mediumCloudCover = mediumCloudCover,
    highCloudCover = highCloudCover,
    fogCover = fogCover,
    relativeHumidity = relativeHumidity,
    windSpeed = windSpeed,
    airTemperatureCelsius = airTemperature,
)

private fun String.parseInstantOrNull(): Instant? = try {
    Instant.parse(this)
} catch (_: DateTimeParseException) {
    null
}

private val PERCENT_RANGE = 0.0..100.0

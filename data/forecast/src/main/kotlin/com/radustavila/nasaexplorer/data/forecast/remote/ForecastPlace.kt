package com.radustavila.nasaexplorer.data.forecast.remote

import com.radustavila.nasaexplorer.core.model.GeoCoordinate
import java.math.BigDecimal
import kotlin.math.roundToInt

/**
 * A place as the forecast is requested and cached: latitude and longitude in hundredths of a
 * degree (about 1 km). MET Norway allows at most 4 decimals; 2 are finer than its model grid, keep
 * a device fix that moves a few metres on the same cached forecast, and send less of the user's
 * position.
 */
data class ForecastPlace(val latitudeE2: Int, val longitudeE2: Int) {

    val latitude: String get() = BigDecimal.valueOf(latitudeE2.toLong(), DECIMALS).stripTrailingZeros().toPlainString()
    val longitude: String get() = BigDecimal.valueOf(longitudeE2.toLong(), DECIMALS).stripTrailingZeros().toPlainString()

    val coordinate: GeoCoordinate get() = GeoCoordinate(latitude.toDouble(), longitude.toDouble())

    companion object {
        private const val DECIMALS = 2
        private const val SCALE = 100.0

        fun of(coordinate: GeoCoordinate): ForecastPlace = ForecastPlace(
            latitudeE2 = (coordinate.latitude * SCALE).roundToInt(),
            longitudeE2 = (coordinate.longitude * SCALE).roundToInt(),
        )
    }
}

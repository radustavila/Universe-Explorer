package com.radustavila.nasaexplorer.data.spaceweather.remote

import com.radustavila.nasaexplorer.data.spaceweather.remote.dto.KpForecastDto
import com.radustavila.nasaexplorer.data.spaceweather.remote.dto.KpOneMinuteDto
import com.radustavila.nasaexplorer.data.spaceweather.remote.dto.OvationDto
import retrofit2.http.GET

/** NOAA Space Weather Prediction Center's public JSON products (no key, no published quota). */
interface SwpcService {

    /** About a week of observed 3-hour Kp plus three days of forecast, ~7 KB. */
    @GET("products/noaa-planetary-k-index-forecast.json")
    suspend fun getKpForecast(): List<KpForecastDto>

    /** The last few hours of the 1-minute Kp estimate, ~30 KB. */
    @GET("json/planetary_k_index_1m.json")
    suspend fun getKpOneMinute(): List<KpOneMinuteDto>

    /** The OVATION aurora grid, 65,160 points: ~900 KB, ~150 KB gzipped. */
    @GET("json/ovation_aurora_latest.json")
    suspend fun getAuroraForecast(): OvationDto
}

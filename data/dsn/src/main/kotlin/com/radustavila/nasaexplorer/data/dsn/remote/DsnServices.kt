package com.radustavila.nasaexplorer.data.dsn.remote

import com.radustavila.nasaexplorer.data.dsn.remote.dto.HorizonsResponseDto
import okhttp3.ResponseBody
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Query

/** NASA's "DSN Now" feeds (eyes.nasa.gov/dsn). XML, so the bodies are parsed by [DsnXmlParser]. */
interface DsnNowService {

    /**
     * The live state. The server sends `max-age=3600`, which would let the HTTP cache serve an hour
     * old state, so the request opts out of the cache; [cacheBuster] is the 5-second bucket NASA's
     * own page appends to get past the CDN's copy.
     */
    @Headers("Cache-Control: no-store")
    @GET("data/dsn.xml")
    suspend fun live(@Query("r") cacheBuster: Long): ResponseBody

    @GET("config.xml")
    suspend fun config(): ResponseBody
}

/** JPL Horizons' API (ssd.jpl.nasa.gov/api/horizons.api). Values are quoted as its docs show. */
interface HorizonsService {

    @GET("api/horizons.api")
    suspend fun observerRange(
        @Query("COMMAND") command: String,
        @Query("START_TIME") startTime: String,
        @Query("STOP_TIME") stopTime: String,
        @Query("format") format: String = "json",
        @Query("OBJ_DATA") objectData: String = "'NO'",
        @Query("MAKE_EPHEM") makeEphemeris: String = "'YES'",
        @Query("EPHEM_TYPE") ephemerisType: String = "'OBSERVER'",
        @Query("CENTER") center: String = "'500@399'",
        @Query("QUANTITIES") quantities: String = "'20'",
        @Query("STEP_SIZE") stepSize: String = "'1d'",
    ): HorizonsResponseDto
}

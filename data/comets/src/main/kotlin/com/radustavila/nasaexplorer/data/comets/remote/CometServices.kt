package com.radustavila.nasaexplorer.data.comets.remote

import com.radustavila.nasaexplorer.data.comets.remote.dto.HorizonsResponseDto
import com.radustavila.nasaexplorer.data.comets.remote.dto.SbdbQueryDto
import retrofit2.http.GET
import retrofit2.http.Query

/** JPL's Small-Body Database query API (ssd-api.jpl.nasa.gov, no key, no published quota). */
interface SbdbQueryService {

    /** Comets ([kind] `c`) matching [constraints], a JSON filter built by [SbdbCometQuery]. */
    @GET("sbdb_query.api")
    suspend fun comets(
        @Query("sb-cdata") constraints: String,
        @Query("fields") fields: String = SbdbCometQuery.FIELDS.joinToString(","),
        @Query("sb-kind") kind: String = "c",
    ): SbdbQueryDto
}

/** JPL Horizons' API (ssd.jpl.nasa.gov/api/horizons.api). Values are quoted as its docs show. */
interface HorizonsCometService {

    /**
     * A geocentric observer table: astrometric RA/Dec in degrees (1), comet magnitudes (9), distance
     * from the Sun (19) and from Earth (20), as CSV.
     */
    @GET("api/horizons.api")
    suspend fun ephemeris(
        @Query("COMMAND") command: String,
        @Query("START_TIME") startTime: String,
        @Query("STOP_TIME") stopTime: String,
        @Query("format") format: String = "json",
        @Query("OBJ_DATA") objectData: String = "'NO'",
        @Query("MAKE_EPHEM") makeEphemeris: String = "'YES'",
        @Query("EPHEM_TYPE") ephemerisType: String = "'OBSERVER'",
        @Query("CENTER") center: String = "'500@399'",
        @Query("QUANTITIES") quantities: String = "'1,9,19,20'",
        @Query("ANG_FORMAT") angleFormat: String = "'DEG'",
        @Query("CSV_FORMAT") csvFormat: String = "'YES'",
        @Query("STEP_SIZE") stepSize: String = "'1d'",
    ): HorizonsResponseDto
}

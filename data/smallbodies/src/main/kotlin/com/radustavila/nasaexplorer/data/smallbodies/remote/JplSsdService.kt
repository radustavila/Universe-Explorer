package com.radustavila.nasaexplorer.data.smallbodies.remote

import com.radustavila.nasaexplorer.data.smallbodies.remote.dto.JplTableDto
import com.radustavila.nasaexplorer.data.smallbodies.remote.dto.SbdbDto
import com.radustavila.nasaexplorer.data.smallbodies.remote.dto.SentryListDto
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * JPL Solar System Dynamics APIs (no key, no published quota). Preferred over NeoWs: the same data
 * without api.nasa.gov's key and its DEMO_KEY limit.
 */
interface JplSsdService {

    /**
     * Close approaches within 0.05 au (~19.5 lunar distances) in the next 30 days. `date-max` is
     * "+30", whose plus sign must reach the server encoded.
     */
    @GET("cad.api")
    suspend fun getCloseApproaches(
        @Query("dist-max") maxDistanceAu: String = "0.05",
        @Query("date-min") from: String = "now",
        @Query(value = "date-max", encoded = true) to: String = "%2B30",
        @Query("diameter") withDiameter: Boolean = true,
        @Query("fullname") withFullName: Boolean = true,
    ): JplTableDto

    /** The whole Sentry list, ~2,200 objects (~600 KB): read once a day to badge approaches. */
    @GET("sentry.api")
    suspend fun getSentryList(): SentryListDto

    @GET("fireball.api")
    suspend fun getFireballs(@Query("limit") limit: Int = FIREBALL_LIMIT): JplTableDto

    @GET("sbdb.api")
    suspend fun getSmallBody(
        @Query("sstr") designation: String,
        @Query("phys-par") withPhysicalParameters: Boolean = true,
    ): SbdbDto

    companion object {
        const val FIREBALL_LIMIT = 20
    }
}

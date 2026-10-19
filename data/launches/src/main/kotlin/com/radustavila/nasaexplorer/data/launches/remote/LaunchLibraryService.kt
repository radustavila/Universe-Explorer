package com.radustavila.nasaexplorer.data.launches.remote

import com.radustavila.nasaexplorer.data.launches.remote.dto.AstronautListDto
import com.radustavila.nasaexplorer.data.launches.remote.dto.LaunchListDto
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Launch Library 2 by The Space Devs (no key). Anonymous clients get 15 calls an hour per IP, so the
 * app keeps a single endpoint call per refresh. The crew list in Sky tonight uses the
 * same service and budget.
 */
interface LaunchLibraryService {

    /**
     * `detailed` mode is the only one that carries `vid_urls` (the livestream links); `normal` mode
     * omits them. `hide_recent_previous` drops launches that already flew in the last 24 hours, which
     * the upcoming list otherwise keeps.
     */
    @GET("2.3.0/launches/upcoming/")
    suspend fun getUpcomingLaunches(
        @Query("limit") limit: Int = UPCOMING_LIMIT,
        @Query("mode") mode: String = "detailed",
        @Query("hide_recent_previous") hideRecentPrevious: Boolean = true,
    ): LaunchListDto

    /** Everyone in orbit right now, one page (about a dozen people; the limit leaves room). */
    @GET("2.3.0/astronauts/")
    suspend fun getAstronautsInSpace(
        @Query("in_space") inSpace: Boolean = true,
        @Query("mode") mode: String = "list",
        @Query("limit") limit: Int = IN_SPACE_LIMIT,
    ): AstronautListDto

    companion object {
        const val IN_SPACE_LIMIT = 50

        /**
         * The card shows three; two spare launches keep it full when one lifts off between two
         * refreshes, which the hourly budget spaces out.
         */
        const val UPCOMING_LIMIT = 5
    }
}

package com.radustavila.nasaexplorer.data.launches.remote

import com.radustavila.nasaexplorer.data.launches.remote.dto.AstronautListDto
import com.radustavila.nasaexplorer.data.launches.remote.dto.LaunchDto
import com.radustavila.nasaexplorer.data.launches.remote.dto.LaunchListDto
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Launch Library 2 by The Space Devs (no key). Anonymous clients get 15 calls an hour per IP, so the
 * app keeps a single endpoint call per refresh. The card, the Launches screen and the crew list in
 * Sky tonight use the same service and budget.
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

    /** The latest launches that flew (or failed), most recent first, with outcome and booster landings. */
    @GET("2.3.0/launches/previous/")
    suspend fun getPreviousLaunches(
        @Query("limit") limit: Int = PREVIOUS_LIMIT,
        @Query("mode") mode: String = "detailed",
    ): LaunchListDto

    /** One launch in detail; only to re-check a launch shortly before its reminder fires. */
    @GET("2.3.0/launches/{id}/")
    suspend fun getLaunch(@Path("id") id: String): LaunchDto

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
         * The Launches screen's Upcoming tab and every detail page; the card shows the first three
         * of the same rows, so neither needs a call of its own.
         */
        const val UPCOMING_LIMIT = 30

        /** The Past tab. */
        const val PREVIOUS_LIMIT = 20
    }
}

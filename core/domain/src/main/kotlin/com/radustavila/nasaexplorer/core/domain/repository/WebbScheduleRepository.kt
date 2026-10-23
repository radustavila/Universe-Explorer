package com.radustavila.nasaexplorer.core.domain.repository

import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.model.WebbSchedule
import kotlinx.coroutines.flow.Flow

/**
 * Offline-first JWST observing schedule (F24): STScI's weekly plan of Webb's visits. Reads observe
 * the local copy; only [refresh] talks to the network.
 */
interface WebbScheduleRepository {

    /** The last downloaded schedule, or null before the first refresh. */
    fun observeSchedule(): Flow<WebbSchedule?>

    /**
     * Reads STScI's index of weekly schedules and downloads the newest week (and the one before it
     * while the newest has not started yet), replacing the stored schedule.
     *
     * @return how many visits are stored afterwards.
     */
    suspend fun refresh(): DataResult<Int>
}

package com.radustavila.nasaexplorer.core.domain.repository

import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.model.MarsPhoto
import com.radustavila.nasaexplorer.core.model.MarsRover
import kotlinx.coroutines.flow.Flow

/**
 * The rovers' newest raw images, offline-first: everything is observed from the local cache, and
 * [refresh] (run by the background sync only, since one rover's feed is slow) writes into it.
 */
interface MarsPhotoRepository {

    /** Cached photos of [rover], newest sol first, then newest shot first. */
    fun observeRoverPhotos(rover: MarsRover): Flow<List<MarsPhoto>>

    /** The [perRover] newest cached photos of each rover, newest shot first. */
    fun observeLatest(perRover: Int): Flow<List<MarsPhoto>>

    fun observePhoto(id: String): Flow<MarsPhoto?>

    /**
     * Downloads every rover's newest photos into the cache. Rovers are independent: one that
     * answers is stored even when another fails, and the result is then that failure.
     *
     * @return how many photos were stored.
     */
    suspend fun refresh(): DataResult<Int>
}

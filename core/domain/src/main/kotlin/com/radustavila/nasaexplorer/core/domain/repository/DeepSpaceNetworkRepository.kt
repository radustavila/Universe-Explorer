package com.radustavila.nasaexplorer.core.domain.repository

import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.model.DeepSpaceProbe
import com.radustavila.nasaexplorer.core.model.DsnSnapshot
import kotlinx.coroutines.flow.Flow

/**
 * Offline-first access to NASA's "DSN Now" feed and to JPL Horizons' distances of far spacecraft.
 * Reads observe the local cache; only the refreshes talk to the network, and a failed or unusable
 * download keeps the last good state.
 */
interface DeepSpaceNetworkRepository {

    /**
     * The last stored snapshot, with spacecraft names from the network's list and Horizons
     * distances where the feed gives none. `null` before the first successful refresh.
     */
    fun observeSnapshot(): Flow<DsnSnapshot?>

    /** The "Farthest from home" spacecraft, farthest first; empty before the first refresh. */
    fun observeFarthestProbes(): Flow<List<DeepSpaceProbe>>

    /** Downloads the live antenna state. */
    suspend fun refreshLive(): DataResult<Unit>

    /** Downloads the site and spacecraft names. */
    suspend fun refreshConfig(): DataResult<Unit>

    /**
     * Asks Horizons for today's distance of each tracked spacecraft.
     *
     * @return how many distances were stored; a failure only when none could be.
     */
    suspend fun refreshDistances(): DataResult<Int>
}

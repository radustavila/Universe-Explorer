package com.radustavila.nasaexplorer.core.domain.repository

import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.model.Comet
import kotlinx.coroutines.flow.Flow

/**
 * Offline-first comets near perihelion (F18): JPL's Small-Body Database picks the candidates and
 * Horizons gives each one's daily path and brightness around today. Reads observe the local cache;
 * only [refresh] talks to the network.
 */
interface CometRepository {

    /** Every cached candidate with its ephemeris; empty before the first refresh. */
    fun observeComets(): Flow<List<Comet>>

    /** One cached comet by its designation, or null when it is not (or no longer) cached. */
    fun observeComet(designation: String): Flow<Comet?>

    /**
     * Asks SBDB for the candidates, then Horizons for each one's ephemeris (one request each, in
     * turn). A comet whose ephemeris fails keeps the one it had.
     *
     * @return how many comets are cached afterwards.
     */
    suspend fun refresh(): DataResult<Int>
}

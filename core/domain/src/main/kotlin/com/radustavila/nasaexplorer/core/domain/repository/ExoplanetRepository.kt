package com.radustavila.nasaexplorer.core.domain.repository

import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.model.Exoplanet
import com.radustavila.nasaexplorer.core.model.ExoplanetCatalogStats
import com.radustavila.nasaexplorer.core.model.ExoplanetList
import kotlinx.coroutines.flow.Flow

/**
 * Offline-first access to the NASA Exoplanet Archive. Every read observes the local cache; only
 * [refresh] talks to the archive, and a failed refresh leaves the cache as it was.
 */
interface ExoplanetRepository {

    fun observeList(list: ExoplanetList): Flow<List<Exoplanet>>

    /** `null` while the planet is not cached. */
    fun observeExoplanet(name: String): Flow<Exoplanet?>

    /** `null` until the first successful refresh. */
    fun observeCatalogStats(): Flow<ExoplanetCatalogStats?>

    /**
     * Downloads the planets every list needs (recent discoveries, nearby systems, Earth-sized
     * worlds) into the cache.
     *
     * @return how many planets were stored.
     */
    suspend fun refresh(): DataResult<Int>
}

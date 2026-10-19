package com.radustavila.nasaexplorer.core.domain.sky

import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.model.OrbitalElements
import com.radustavila.nasaexplorer.core.model.SatellitePosition
import kotlinx.coroutines.flow.Flow

/**
 * Orbital elements, cached locally (predictions run offline from them), and the live position of a
 * satellite, which is only ever fetched, never cached.
 */
interface SatelliteRepository {

    /** Cached elements of [noradId], or null until a first refresh succeeds. */
    fun observeElements(noradId: Int): Flow<OrbitalElements?>

    /** Replaces the cached elements with CelesTrak's current set, in one call. */
    suspend fun refreshElements(): DataResult<Unit>

    /** Where the satellite is right now, according to a live tracking service. */
    suspend fun fetchLivePosition(noradId: Int): DataResult<SatellitePosition>
}

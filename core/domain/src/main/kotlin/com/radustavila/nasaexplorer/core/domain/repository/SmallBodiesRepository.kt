package com.radustavila.nasaexplorer.core.domain.repository

import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.model.CloseApproach
import com.radustavila.nasaexplorer.core.model.Fireball
import com.radustavila.nasaexplorer.core.model.SmallBodyDetails
import kotlinx.coroutines.flow.Flow

/**
 * Earth's neighbourhood from JPL's Solar System Dynamics APIs: close approaches with their Sentry
 * risk, recent fireballs, and per-object details. Offline-first like every other source.
 */
interface SmallBodiesRepository {

    /** Cached close approaches, soonest first, each with its Sentry entry when it has one. */
    fun observeCloseApproaches(): Flow<List<CloseApproach>>

    fun observeCloseApproach(designation: String): Flow<CloseApproach?>

    /** Cached fireballs, newest first. */
    fun observeRecentFireballs(): Flow<List<Fireball>>

    /** Cached details of one object, null until [fetchDetails] has stored them. */
    fun observeDetails(designation: String): Flow<SmallBodyDetails?>

    /** Close approaches for the next 30 days, the Sentry list and recent fireballs. */
    suspend fun refresh(): DataResult<Unit>

    /** Looks one object up in the Small-Body Database and caches the answer. */
    suspend fun fetchDetails(designation: String): DataResult<SmallBodyDetails>
}

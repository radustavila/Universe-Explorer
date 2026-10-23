package com.radustavila.nasaexplorer.core.domain.repository

import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.model.ClimateIndicator
import com.radustavila.nasaexplorer.core.model.ClimateSeries
import kotlinx.coroutines.flow.Flow

/**
 * Offline-first climate vital signs (F26): CO₂, global temperature, sea level and Arctic sea ice,
 * each from one public file. Reads observe the local copy; only [refresh] talks to the network.
 */
interface ClimateRepository {

    /** Every indicator with stored values, in [ClimateIndicator] order; empty before the first refresh. */
    fun observeSeries(): Flow<List<ClimateSeries>>

    /**
     * Downloads [indicator]'s file and replaces its stored values.
     *
     * @return how many values are stored afterwards.
     */
    suspend fun refresh(indicator: ClimateIndicator): DataResult<Int>
}

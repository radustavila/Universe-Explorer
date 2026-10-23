package com.radustavila.nasaexplorer.core.testing.fake

import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.domain.repository.ClimateRepository
import com.radustavila.nasaexplorer.core.model.ClimateIndicator
import com.radustavila.nasaexplorer.core.model.ClimateSeries
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** In-memory [ClimateRepository] that counts refreshes per indicator. */
class FakeClimateRepository(initial: List<ClimateSeries> = emptyList()) : ClimateRepository {

    val series = MutableStateFlow(initial)

    var refreshResult: DataResult<Int> = DataResult.Success(0)

    val refreshed = mutableListOf<ClimateIndicator>()

    override fun observeSeries(): Flow<List<ClimateSeries>> = series

    override suspend fun refresh(indicator: ClimateIndicator): DataResult<Int> {
        refreshed += indicator
        return refreshResult
    }
}

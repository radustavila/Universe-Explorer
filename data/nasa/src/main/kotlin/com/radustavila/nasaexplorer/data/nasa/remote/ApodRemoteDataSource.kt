package com.radustavila.nasaexplorer.data.nasa.remote

import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.data.nasa.remote.dto.ApodDto
import java.time.LocalDate

/**
 * Remote boundary of the NASA data module. Returns [DataResult] instead of throwing, so the
 * repository never has to reason about transport exceptions.
 */
interface ApodRemoteDataSource {
    suspend fun fetchRange(startDate: LocalDate, endDate: LocalDate): DataResult<List<ApodDto>>
    suspend fun fetchSingle(date: LocalDate): DataResult<ApodDto>
}

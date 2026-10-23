package com.radustavila.nasaexplorer.data.comets.remote

import com.radustavila.nasaexplorer.core.common.dispatchers.AppDispatcher
import com.radustavila.nasaexplorer.core.common.dispatchers.Dispatcher
import com.radustavila.nasaexplorer.core.common.error.AppError
import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.common.result.flatMap
import com.radustavila.nasaexplorer.core.common.result.map
import com.radustavila.nasaexplorer.core.network.http.safeNetworkCall
import com.radustavila.nasaexplorer.data.comets.remote.dto.CometCandidateDto
import com.radustavila.nasaexplorer.data.comets.remote.dto.CometEphemerisRowDto
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/** Remote boundary of the comets module. Returns [DataResult] instead of throwing. */
interface CometsRemoteDataSource {

    /** SBDB's comets near perihelion around [now]; an empty list is a valid answer. */
    suspend fun fetchCandidates(now: Instant): DataResult<List<CometCandidateDto>>

    /** Horizons' daily geocentric ephemeris of [designation] from [from] to [until], both included. */
    suspend fun fetchEphemeris(
        designation: String,
        from: LocalDate,
        until: LocalDate,
    ): DataResult<List<CometEphemerisRowDto>>
}

@Singleton
class RetrofitCometsRemoteDataSource @Inject constructor(
    private val sbdbService: SbdbQueryService,
    private val horizonsService: HorizonsCometService,
    @Dispatcher(AppDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
) : CometsRemoteDataSource {

    override suspend fun fetchCandidates(now: Instant): DataResult<List<CometCandidateDto>> =
        withContext(ioDispatcher) {
            safeNetworkCall { sbdbService.comets(constraints = SbdbCometQuery.constraints(now)) }
                .map(SbdbCometQuery::candidates)
        }

    override suspend fun fetchEphemeris(
        designation: String,
        from: LocalDate,
        until: LocalDate,
    ): DataResult<List<CometEphemerisRowDto>> = withContext(ioDispatcher) {
        safeNetworkCall {
            horizonsService.ephemeris(
                // The latest orbit solution (CAP) of the comet itself, not of its fragments.
                command = quoted("DES=$designation;CAP;NOFRAG"),
                startTime = quoted(from.toString()),
                stopTime = quoted(until.toString()),
            )
        }.flatMap { response ->
            // Horizons answers 200 with an "error" or a list of matches for an ambiguous name.
            val rows = response.takeIf { it.error == null }?.result
                ?.let(HorizonsCometParser::parse)
                ?.takeIf { it.isNotEmpty() }
            rows?.let { DataResult.Success(it) } ?: DataResult.Failure(AppError.Serialization(response.error))
        }
    }

    private fun quoted(value: String) = "'$value'"
}

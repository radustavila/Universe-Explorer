package com.radustavila.nasaexplorer.core.domain.usecase

import com.radustavila.nasaexplorer.core.domain.repository.ClimateRepository
import com.radustavila.nasaexplorer.core.domain.sync.SourceRefreshResult
import com.radustavila.nasaexplorer.core.model.ClimateIndicator
import com.radustavila.nasaexplorer.core.model.ClimateSeries
import java.time.Duration
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Refreshes the climate files, each at most once per [MIN_REFRESH_INTERVAL] unless [force]d by the
 * user, all at once: four small files on four hosts. The data is monthly (sea level lags months), so
 * a weekly look is plenty. Runs when the Earth tab's vital signs or a detail chart opens.
 */
class RefreshClimateUseCase @Inject constructor(
    private val repository: ClimateRepository,
    private val refreshSource: RefreshSourceUseCase,
) {
    suspend operator fun invoke(
        force: Boolean = false,
        indicators: Collection<ClimateIndicator> = ClimateIndicator.entries,
    ): Map<ClimateIndicator, SourceRefreshResult<Int>> = coroutineScope {
        indicators.map { indicator ->
            async {
                indicator to refreshSource(indicator.syncSource, minInterval = MIN_REFRESH_INTERVAL, force = force) {
                    repository.refresh(indicator)
                }
            }
        }.awaitAll().toMap()
    }

    companion object {
        val MIN_REFRESH_INTERVAL: Duration = Duration.ofDays(7)
    }
}

/** Every stored indicator, in [ClimateIndicator] order. */
class ObserveClimateUseCase @Inject constructor(
    private val repository: ClimateRepository,
) {
    operator fun invoke(): Flow<List<ClimateSeries>> = repository.observeSeries()

    /** One indicator's values; null before its first download. */
    operator fun invoke(indicator: ClimateIndicator): Flow<ClimateSeries?> =
        repository.observeSeries().map { all -> all.firstOrNull { it.indicator == indicator } }
}

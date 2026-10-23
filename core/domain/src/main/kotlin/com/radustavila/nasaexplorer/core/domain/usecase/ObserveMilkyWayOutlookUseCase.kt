package com.radustavila.nasaexplorer.core.domain.usecase

import com.radustavila.nasaexplorer.core.common.dispatchers.AppDispatcher
import com.radustavila.nasaexplorer.core.common.dispatchers.Dispatcher
import com.radustavila.nasaexplorer.core.common.time.TimeProvider
import com.radustavila.nasaexplorer.core.domain.sky.MilkyWayCalculator
import com.radustavila.nasaexplorer.core.model.GeoCoordinate
import com.radustavila.nasaexplorer.core.model.MilkyWayOutlook
import java.time.Duration
import java.time.ZoneOffset
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

/**
 * The Milky Way planner's coming nights at [observer] (F20), with the core's season. Recomputed
 * every [RECOMPUTE_INTERVAL], so the outlook rolls over to the next night on its own. Entirely
 * offline: nothing here touches the network or storage.
 */
class ObserveMilkyWayOutlookUseCase @Inject constructor(
    private val calculator: MilkyWayCalculator,
    private val timeProvider: TimeProvider,
    @Dispatcher(AppDispatcher.Default) private val computeDispatcher: CoroutineDispatcher,
) {
    operator fun invoke(observer: GeoCoordinate, nights: Int = NIGHTS): Flow<MilkyWayOutlook> = flow {
        val season = calculator.season(observer, timeProvider.now().atOffset(ZoneOffset.UTC).year)
        while (true) {
            emit(MilkyWayOutlook(observer, calculator.nights(observer, timeProvider.now(), nights), season))
            delay(RECOMPUTE_INTERVAL.toMillis())
        }
    }.flowOn(computeDispatcher)

    companion object {
        /** A month ahead: a full lunar cycle, so the next dark window is always in it when there is one. */
        const val NIGHTS = 30
        val RECOMPUTE_INTERVAL: Duration = Duration.ofMinutes(15)
    }
}

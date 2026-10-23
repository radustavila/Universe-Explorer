package com.radustavila.nasaexplorer.core.domain.usecase

import com.radustavila.nasaexplorer.core.common.dispatchers.AppDispatcher
import com.radustavila.nasaexplorer.core.common.dispatchers.Dispatcher
import com.radustavila.nasaexplorer.core.common.time.TimeProvider
import com.radustavila.nasaexplorer.core.domain.moon.LunarLandingSiteRepository
import com.radustavila.nasaexplorer.core.domain.moon.MoonIlluminationCalculator
import com.radustavila.nasaexplorer.core.domain.moon.MoonTilesRepository
import com.radustavila.nasaexplorer.core.model.LunarLandingSite
import com.radustavila.nasaexplorer.core.model.MoonIllumination
import com.radustavila.nasaexplorer.core.model.MoonTilesPack
import java.time.Duration
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

/** Every landing site on the Moon map (F25), oldest first; bundled, so it never waits on the network. */
class ObserveLunarLandingSitesUseCase @Inject constructor(
    private val repository: LunarLandingSiteRepository,
) {
    operator fun invoke(): Flow<List<LunarLandingSite>> = flow { emit(repository.sites()) }
}

/**
 * Where the Sun stands over the Moon now, recomputed every [UPDATE_INTERVAL] so the terminator on
 * the Moon map creeps along on its own (about 0.1° of longitude in ten minutes).
 */
class ObserveMoonIlluminationUseCase @Inject constructor(
    private val calculator: MoonIlluminationCalculator,
    private val timeProvider: TimeProvider,
    @Dispatcher(AppDispatcher.Default) private val computeDispatcher: CoroutineDispatcher,
) {
    operator fun invoke(): Flow<MoonIllumination> = flow {
        while (true) {
            emit(calculator.illumination(timeProvider.now()))
            delay(UPDATE_INTERVAL.toMillis())
        }
    }.flowOn(computeDispatcher)

    companion object {
        val UPDATE_INTERVAL: Duration = Duration.ofMinutes(10)
    }
}

/** The deeper Moon tiles' pack: its state, and downloading or removing it. */
class ObserveMoonTilesPackUseCase @Inject constructor(private val repository: MoonTilesRepository) {
    operator fun invoke(): Flow<MoonTilesPack> = repository.observePack()
}

class DownloadMoonTilesUseCase @Inject constructor(private val repository: MoonTilesRepository) {
    suspend operator fun invoke() = repository.download()
}

class RemoveMoonTilesUseCase @Inject constructor(private val repository: MoonTilesRepository) {
    suspend operator fun invoke() = repository.remove()
}

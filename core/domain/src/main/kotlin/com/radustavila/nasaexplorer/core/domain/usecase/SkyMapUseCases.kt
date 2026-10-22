package com.radustavila.nasaexplorer.core.domain.usecase

import com.radustavila.nasaexplorer.core.common.dispatchers.AppDispatcher
import com.radustavila.nasaexplorer.core.common.dispatchers.Dispatcher
import com.radustavila.nasaexplorer.core.domain.sky.SatelliteRepository
import com.radustavila.nasaexplorer.core.domain.sky.SkyCatalogRepository
import com.radustavila.nasaexplorer.core.domain.sky.SkyMapEphemeris
import com.radustavila.nasaexplorer.core.domain.sky.SkyMapPreferencesRepository
import com.radustavila.nasaexplorer.core.model.EquatorialCoordinate
import com.radustavila.nasaexplorer.core.model.GeoCoordinate
import com.radustavila.nasaexplorer.core.model.OrbitalElements
import com.radustavila.nasaexplorer.core.model.OrbitalElements.Companion.ISS_NORAD_ID
import com.radustavila.nasaexplorer.core.model.SatelliteSkyPosition
import com.radustavila.nasaexplorer.core.model.SkyCatalog
import com.radustavila.nasaexplorer.core.model.SkyMapBodies
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

/** The bundled stars and constellations, read off the main thread. */
class LoadSkyCatalogUseCase @Inject constructor(
    private val repository: SkyCatalogRepository,
    @Dispatcher(AppDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
) {
    suspend operator fun invoke(): SkyCatalog = withContext(ioDispatcher) { repository.catalog() }
}

/**
 * The Sun, Moon, planets and the ISS above [observer] at every instant [instants] emits, computed on
 * the device from the cached orbital elements. A slow device skips instants rather than falling
 * behind.
 */
class ObserveSkyMapBodiesUseCase @Inject constructor(
    private val ephemeris: SkyMapEphemeris,
    private val satelliteRepository: SatelliteRepository,
    @Dispatcher(AppDispatcher.Default) private val computeDispatcher: CoroutineDispatcher,
) {
    operator fun invoke(observer: GeoCoordinate, instants: Flow<Instant>): Flow<SkyMapBodies> =
        combine(instants.conflate(), satelliteRepository.observeElements(ISS_NORAD_ID)) { at, iss ->
            SkyMapBodies(
                solarSystem = ephemeris.solarSystem(observer, at),
                satellites = listOfNotNull(iss?.let { satellite(it, observer, at) }),
            )
        }.conflate().flowOn(computeDispatcher)

    private fun satellite(elements: OrbitalElements, observer: GeoCoordinate, at: Instant): SatelliteSkyPosition? {
        // Elements propagated far from their epoch put the station in the wrong part of the sky.
        if (elements.isTooOldAt(at)) return null
        val horizontal = ephemeris.satellite(elements, observer, at) ?: return null
        return SatelliteSkyPosition(elements.noradId, elements.name, horizontal)
    }
}

/** IAU abbreviation of the constellation a direction falls in. */
class FindConstellationUseCase @Inject constructor(private val ephemeris: SkyMapEphemeris) {
    operator fun invoke(position: EquatorialCoordinate): String = ephemeris.constellationAt(position)
}

class ObserveSkyMapNightModeUseCase @Inject constructor(private val repository: SkyMapPreferencesRepository) {
    operator fun invoke(): Flow<Boolean> = repository.observeNightMode()
}

class SetSkyMapNightModeUseCase @Inject constructor(private val repository: SkyMapPreferencesRepository) {
    suspend operator fun invoke(enabled: Boolean) = repository.setNightMode(enabled)
}

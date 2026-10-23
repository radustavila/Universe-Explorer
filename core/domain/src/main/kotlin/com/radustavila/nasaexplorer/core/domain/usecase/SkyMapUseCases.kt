package com.radustavila.nasaexplorer.core.domain.usecase

import com.radustavila.nasaexplorer.core.common.dispatchers.AppDispatcher
import com.radustavila.nasaexplorer.core.common.dispatchers.Dispatcher
import com.radustavila.nasaexplorer.core.domain.repository.CometRepository
import com.radustavila.nasaexplorer.core.domain.sky.CometSkyCalculator
import com.radustavila.nasaexplorer.core.domain.sky.SatelliteRepository
import com.radustavila.nasaexplorer.core.domain.sky.SkyCatalogRepository
import com.radustavila.nasaexplorer.core.domain.sky.SkyMapEphemeris
import com.radustavila.nasaexplorer.core.domain.sky.SkyMapPreferencesRepository
import com.radustavila.nasaexplorer.core.model.Comet
import com.radustavila.nasaexplorer.core.model.CometSkyPosition
import com.radustavila.nasaexplorer.core.model.EquatorialCoordinate
import com.radustavila.nasaexplorer.core.model.GeoCoordinate
import com.radustavila.nasaexplorer.core.model.OrbitalElements
import com.radustavila.nasaexplorer.core.model.SatelliteSet
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
 * The Sun, Moon, planets, the space stations and the bright comets above [observer] at every
 * instant [instants] emits, computed on the device from cached data: the ISS always, Hubble and
 * Tiangong once Sky tonight follows them (F15) and their elements are cached, and the comets F18
 * tracks that are predicted at the cut-off ([ObserveCometMagnitudeLimitUseCase]) or brighter at
 * that instant. A slow device skips instants rather than falling behind.
 */
class ObserveSkyMapBodiesUseCase @Inject constructor(
    private val ephemeris: SkyMapEphemeris,
    private val satelliteRepository: SatelliteRepository,
    private val cometRepository: CometRepository,
    private val cometCalculator: CometSkyCalculator,
    private val observeCometMagnitudeLimit: ObserveCometMagnitudeLimitUseCase,
    @Dispatcher(AppDispatcher.Default) private val computeDispatcher: CoroutineDispatcher,
) {
    operator fun invoke(observer: GeoCoordinate, instants: Flow<Instant>): Flow<SkyMapBodies> =
        combine(instants.conflate(), stationElements(), cometsToShow()) { at, stations, comets ->
            SkyMapBodies(
                solarSystem = ephemeris.solarSystem(observer, at),
                satellites = stations.mapNotNull { satellite(it, observer, at) },
                comets = comets.visibleAt(observer, at),
            )
        }.conflate().flowOn(computeDispatcher)

    /** Cached elements of every single-object set (ISS, Hubble, Tiangong); none are fetched here. */
    private fun stationElements(): Flow<List<OrbitalElements>> =
        combine(STATIONS.map(satelliteRepository::observeElements)) { elements -> elements.filterNotNull() }

    // Elements propagated far from their epoch put the station in the wrong part of the sky.
    private fun satellite(elements: OrbitalElements, observer: GeoCoordinate, at: Instant): SatelliteSkyPosition? =
        elements.takeUnless { it.isTooOldAt(at) }
            ?.let { ephemeris.satellite(it, observer, at) }
            ?.let { SatelliteSkyPosition(elements.noradId, elements.name, it) }

    private fun cometsToShow(): Flow<CometsToShow> =
        combine(cometRepository.observeComets(), observeCometMagnitudeLimit(), ::CometsToShow)

    private fun CometsToShow.visibleAt(observer: GeoCoordinate, at: Instant): List<CometSkyPosition> =
        comets.mapNotNull { cometCalculator.skyPosition(it, observer, at) }
            .filter { isBrightEnough(it.magnitude, magnitudeLimit) }
            .sortedWith(compareBy(nullsLast()) { it.magnitude })

    private data class CometsToShow(val comets: List<Comet>, val magnitudeLimit: Double?)

    private companion object {
        val STATIONS: List<Int> = SatelliteSet.entries.mapNotNull { it.noradId }
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

class ObserveSkyMapDeepSkyLayerUseCase @Inject constructor(private val repository: SkyMapPreferencesRepository) {
    operator fun invoke(): Flow<Boolean> = repository.observeDeepSkyLayer()
}

class SetSkyMapDeepSkyLayerUseCase @Inject constructor(private val repository: SkyMapPreferencesRepository) {
    suspend operator fun invoke(enabled: Boolean) = repository.setDeepSkyLayer(enabled)
}

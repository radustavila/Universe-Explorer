package com.radustavila.nasaexplorer.core.testing.fake

import com.radustavila.nasaexplorer.core.domain.sky.SkyCatalogRepository
import com.radustavila.nasaexplorer.core.domain.sky.SkyMapEphemeris
import com.radustavila.nasaexplorer.core.domain.sky.SkyMapPreferencesRepository
import com.radustavila.nasaexplorer.core.model.EquatorialCoordinate
import com.radustavila.nasaexplorer.core.model.GeoCoordinate
import com.radustavila.nasaexplorer.core.model.HorizontalCoordinate
import com.radustavila.nasaexplorer.core.model.OrbitalElements
import com.radustavila.nasaexplorer.core.model.SkyCatalog
import com.radustavila.nasaexplorer.core.model.SolarSystemPosition
import com.radustavila.nasaexplorer.core.testing.data.TestSkyMap
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** Returns [catalog], counting the reads. */
class FakeSkyCatalogRepository(var catalog: SkyCatalog = TestSkyMap.catalog()) : SkyCatalogRepository {

    var readCount: Int = 0
        private set

    override suspend fun catalog(): SkyCatalog {
        readCount++
        return catalog
    }
}

/** Returns [solarSystem] and [satellite] for any place and time, recording the instants asked for. */
class FakeSkyMapEphemeris(
    var solarSystem: List<SolarSystemPosition> = TestSkyMap.solarSystem(),
    var satellite: HorizontalCoordinate? = null,
    var constellation: String = "Ori",
) : SkyMapEphemeris {

    val instants = mutableListOf<Instant>()

    override fun solarSystem(observer: GeoCoordinate, at: Instant): List<SolarSystemPosition> {
        instants += at
        return solarSystem
    }

    override fun satellite(elements: OrbitalElements, observer: GeoCoordinate, at: Instant): HorizontalCoordinate? =
        satellite

    override fun constellationAt(position: EquatorialCoordinate): String = constellation
}

class FakeSkyMapPreferencesRepository : SkyMapPreferencesRepository {

    private val nightMode = MutableStateFlow(false)

    override fun observeNightMode(): Flow<Boolean> = nightMode

    override suspend fun setNightMode(enabled: Boolean) {
        nightMode.value = enabled
    }
}

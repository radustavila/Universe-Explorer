package com.radustavila.nasaexplorer.core.domain.usecase

import app.cash.turbine.test
import com.radustavila.nasaexplorer.core.model.EquatorialCoordinate
import com.radustavila.nasaexplorer.core.model.HorizontalCoordinate
import com.radustavila.nasaexplorer.core.model.OrbitalElements.Companion.ISS_NORAD_ID
import com.radustavila.nasaexplorer.core.model.SatelliteSet
import com.radustavila.nasaexplorer.core.testing.data.TestSatellites
import com.radustavila.nasaexplorer.core.testing.data.TestSky
import com.radustavila.nasaexplorer.core.testing.data.TestSkyMap
import com.radustavila.nasaexplorer.core.testing.fake.FakeSatelliteRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeSkyCatalogRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeSkyMapEphemeris
import com.radustavila.nasaexplorer.core.testing.fake.FakeSkyMapPreferencesRepository
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SkyMapUseCasesTest {

    private val now = Instant.parse("2026-10-22T20:00:00Z")
    private val ephemeris = FakeSkyMapEphemeris(satellite = HorizontalCoordinate(40.0, 200.0))
    private val satellites = FakeSatelliteRepository()

    @Test
    fun `bodies follow the instants, with the ISS from fresh elements`() = runTest {
        satellites.setElements(TestSky.issElements(epoch = now.minus(Duration.ofHours(6))))
        val instants = MutableStateFlow(now)
        val useCase = ObserveSkyMapBodiesUseCase(ephemeris, satellites, StandardTestDispatcher(testScheduler))

        useCase(TestSky.BUCHAREST, instants).test {
            val first = awaitItem()
            assertEquals(TestSkyMap.solarSystem(), first.solarSystem)
            assertEquals(ISS_NORAD_ID, first.satellites.single().noradId)
            assertEquals(HorizontalCoordinate(40.0, 200.0), first.satellites.single().horizontal)

            instants.value = now.plus(Duration.ofHours(2))
            awaitItem()
            assertEquals(now.plus(Duration.ofHours(2)), ephemeris.instants.last())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `Hubble joins once its elements are cached`() = runTest {
        satellites.setElements(TestSatellites.hubbleElements(epoch = now.minus(Duration.ofHours(12))))
        val useCase = ObserveSkyMapBodiesUseCase(ephemeris, satellites, StandardTestDispatcher(testScheduler))

        val bodies = useCase(TestSky.BUCHAREST, MutableStateFlow(now)).first()

        assertEquals(listOf(SatelliteSet.HUBBLE.noradId), bodies.satellites.map { it.noradId })
    }

    @Test
    fun `stale or missing elements leave the ISS off the map`() = runTest {
        val useCase = ObserveSkyMapBodiesUseCase(ephemeris, satellites, StandardTestDispatcher(testScheduler))

        assertTrue(useCase(TestSky.BUCHAREST, MutableStateFlow(now)).first().satellites.isEmpty())

        satellites.setElements(TestSky.issElements(epoch = now.minus(Duration.ofDays(30))))
        assertTrue(useCase(TestSky.BUCHAREST, MutableStateFlow(now)).first().satellites.isEmpty())
    }

    @Test
    fun `the catalogue is read off the main thread from the repository`() = runTest {
        val repository = FakeSkyCatalogRepository()

        val catalog = LoadSkyCatalogUseCase(repository, StandardTestDispatcher(testScheduler))()

        assertEquals(TestSkyMap.catalog(), catalog)
        assertEquals(1, repository.readCount)
    }

    @Test
    fun `constellations come from the ephemeris`() {
        assertEquals("Ori", FindConstellationUseCase(ephemeris)(EquatorialCoordinate(88.79, 7.41)))
    }

    @Test
    fun `night mode is off until switched on`() = runTest {
        val preferences = FakeSkyMapPreferencesRepository()
        val observe = ObserveSkyMapNightModeUseCase(preferences)

        assertFalse(observe().first())
        SetSkyMapNightModeUseCase(preferences)(true)
        assertTrue(observe().first())
    }
}

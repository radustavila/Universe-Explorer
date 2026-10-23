package com.radustavila.nasaexplorer.core.domain.usecase

import com.radustavila.nasaexplorer.core.common.error.AppError
import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.domain.sync.SourceRefreshResult
import com.radustavila.nasaexplorer.core.model.GeoCoordinate
import com.radustavila.nasaexplorer.core.model.LocationSource
import com.radustavila.nasaexplorer.core.model.ObserverLocation
import com.radustavila.nasaexplorer.core.model.SyncSource
import com.radustavila.nasaexplorer.core.testing.data.TestComets
import com.radustavila.nasaexplorer.core.testing.fake.FakeCometRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeCometSkyCalculator
import com.radustavila.nasaexplorer.core.testing.fake.FakeObserverLocationRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeSyncMetadataRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeTimeProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CometUseCasesTest {

    private val metadata = FakeSyncMetadataRepository()
    private val clock = FakeTimeProvider(currentInstant = TestComets.NOW)
    private val repository = FakeCometRepository()
    private val location = FakeObserverLocationRepository()
    private val calculator = FakeCometSkyCalculator()

    private val refreshComets = RefreshCometsUseCase(repository, RefreshSourceUseCase(metadata, clock))

    private fun TestScope.observeTonight() =
        ObserveCometsTonightUseCase(repository, location, calculator, clock, UnconfinedTestDispatcher(testScheduler))

    @Test
    fun `comets are asked for at most once in 20 hours unless forced`() = runTest {
        refreshComets()
        clock.currentInstant = clock.currentInstant.plus(RefreshCometsUseCase.MIN_REFRESH_INTERVAL).minusSeconds(1)

        assertTrue(refreshComets() is SourceRefreshResult.Skipped)
        refreshComets(force = true)

        assertEquals(2, repository.refreshCount)
        assertEquals(clock.currentInstant, metadata.statusOf(SyncSource.COMETS).lastSuccessAt)
    }

    @Test
    fun `a failed refresh is recorded and stays due`() = runTest {
        repository.refreshResult = DataResult.Failure(AppError.NoConnection)

        refreshComets()
        refreshComets()

        assertEquals(2, repository.refreshCount)
        assertTrue(metadata.statusOf(SyncSource.COMETS).isFailing)
    }

    @Test
    fun `comets are split by the telescope cut-off, brightest first, out-of-date ones dropped`() = runTest {
        val bright = TestComets.comet(designation = "2026 B1", name = "C/2026 B1 (Bright)")
        val medium = TestComets.comet(designation = "2026 C1", name = "C/2026 C1 (Medium)")
        val faint = TestComets.comet()
        val stale = TestComets.comet(designation = "2025 Z1", name = "C/2025 Z1 (Stale)")
        repository.comets.value = listOf(faint, medium, stale, bright)
        calculator.answers = mapOf(
            faint.designation to TestComets.tonight(faint, magnitude = 14.2),
            medium.designation to TestComets.tonight(medium, magnitude = 11.4),
            bright.designation to TestComets.tonight(bright, magnitude = 5.1),
        )

        val tonight = observeTonight()().first()

        assertEquals(listOf("2026 B1", "2026 C1"), tonight.visible.map { it.comet.designation })
        assertEquals(listOf("2026 A2"), tonight.faint.map { it.comet.designation })
        assertNull(tonight.observer)
    }

    @Test
    fun `the saved place is passed to the calculator`() = runTest {
        val bucharest = GeoCoordinate(latitude = 44.43, longitude = 26.10)
        location.setLocation(ObserverLocation(bucharest, "Bucharest", LocationSource.MANUAL))
        val comet = TestComets.comet()
        repository.comets.value = listOf(comet)
        calculator.answers = mapOf(comet.designation to TestComets.tonight(comet))

        val tonight = observeTonight()().first()

        assertEquals(bucharest, tonight.observer)
        assertEquals(bucharest, calculator.lastObserver)
        assertTrue(tonight.faint.single().sky != null)
    }

    @Test
    fun `one comet is observed by its designation`() = runTest {
        val comet = TestComets.comet()
        repository.comets.value = listOf(comet)
        calculator.answers = mapOf(comet.designation to TestComets.tonight(comet))
        val observeOne =
            ObserveCometTonightUseCase(repository, location, calculator, clock, UnconfinedTestDispatcher(testScheduler))

        assertEquals(comet, observeOne("2026 A2").first()!!.comet)
        assertNull(observeOne("1P").first())
    }
}

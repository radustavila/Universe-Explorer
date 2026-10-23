package com.radustavila.nasaexplorer.core.domain.usecase

import com.radustavila.nasaexplorer.core.model.DarkNight
import com.radustavila.nasaexplorer.core.model.DeepSkyTonight
import com.radustavila.nasaexplorer.core.model.GeoCoordinate
import com.radustavila.nasaexplorer.core.model.NightDarkness
import com.radustavila.nasaexplorer.core.model.ObservingConditions
import com.radustavila.nasaexplorer.core.testing.data.TestDeepSky
import com.radustavila.nasaexplorer.core.testing.fake.FakeDarkSkyCalculator
import com.radustavila.nasaexplorer.core.testing.fake.FakeDeepSkyCatalogRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeObservingLogRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeSkyPositionCalculator
import com.radustavila.nasaexplorer.core.testing.fake.FakeTimeProvider
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeepSkyUseCasesTest {

    private val log = FakeObservingLogRepository()
    private val time = FakeTimeProvider(currentInstant = Instant.parse("2026-10-23T18:00:00Z"))
    private val observer = GeoCoordinate(44.43, 26.10)

    private val darkStart = Instant.parse("2026-10-23T17:30:00Z")
    private val darkEnd = Instant.parse("2026-10-24T03:30:00Z")

    private fun night(darkness: NightDarkness = NightDarkness.ASTRONOMICAL) = DarkNight(
        sunset = darkStart.minus(Duration.ofMinutes(90)),
        sunrise = darkEnd.plus(Duration.ofMinutes(90)),
        darkness = darkness,
        darkStart = darkStart.takeIf { darkness != NightDarkness.NONE },
        darkEnd = darkEnd.takeIf { darkness != NightDarkness.NONE },
        moonSamples = emptyList(),
        moonIlluminatedFraction = 0.1,
    )

    @Test
    fun `marking as seen trims the note and stamps the time, and marks again replace`() = runTest {
        val mark = MarkDeepSkySeenUseCase(log, time)

        mark("NGC0224", LocalDate.of(2026, 10, 20), ObservingConditions.GOOD, "  with 10x50  ")
        mark("NGC0224", LocalDate.of(2026, 10, 21), null, "   ")

        val entries = ObserveObservingLogUseCase(log)().first()
        val entry = entries.getValue("NGC0224")
        assertEquals(1, entries.size)
        assertEquals(LocalDate.of(2026, 10, 21), entry.observedOn)
        assertNull(entry.note)
        assertNull(entry.conditions)
        assertEquals(time.now(), entry.loggedAt)
    }

    @Test
    fun `removing a mark leaves the others`() = runTest {
        log.save(TestDeepSky.entry(objectId = "NGC0224"))
        log.save(TestDeepSky.entry(objectId = "NGC1976"))

        RemoveDeepSkySeenUseCase(log)("NGC0224")

        assertEquals(setOf("NGC1976"), ObserveObservingLogUseCase(log)().first().keys)
    }

    @Test
    fun `the CSV export lists marks oldest first, with catalogue data and quoted fields`() = runTest {
        val note = "Trapezium, \"sharp\""
        log.save(TestDeepSky.entry(objectId = "NGC1976", observedOn = LocalDate.of(2026, 1, 12), note = note))
        log.save(TestDeepSky.entry(objectId = "C014", observedOn = LocalDate.of(2025, 11, 2), conditions = null))
        val catalog = FakeDeepSkyCatalogRepository()
        val export = ExportObservingLogUseCase(log, catalog, StandardTestDispatcher(testScheduler))

        val lines = export().split("\r\n")

        assertEquals(
            "object,designation,messier,caldwell,common_name,type,constellation,observed_on,conditions,note",
            lines[0],
        )
        assertEquals("C014,,,C14,Double Cluster,OPEN_CLUSTER,Per,2025-11-02,,", lines[1])
        assertEquals(
            "NGC1976,NGC 1976,M42,,Great Orion Nebula,NEBULA,Ori,2026-01-12,GOOD,\"Trapezium, \"\"sharp\"\"\"",
            lines[2],
        )
        assertEquals("", lines[3])
    }

    @Test
    fun `tonight keeps what climbs 30 degrees in the dark hours, at its highest`() = runTest {
        // M31 peaks at 70 degrees three hours into the night; M42 never clears 25.
        val peak = darkStart.plus(Duration.ofHours(3))
        val positions = FakeSkyPositionCalculator { position, at ->
            val hours = Duration.between(peak, at).toMinutes() / 60.0
            if (position == TestDeepSky.M31.position) 70.0 - 5 * kotlin.math.abs(hours) else 25.0
        }
        val useCase = ObserveDeepSkyTonightUseCase(
            FakeDarkSkyCalculator(listOf(night())),
            positions,
            time,
            StandardTestDispatcher(testScheduler),
        )

        val tonight = useCase.tonight(observer, listOf(TestDeepSky.M31, TestDeepSky.M42), time.now())!!

        assertEquals(darkStart, tonight.darkStart)
        assertEquals(darkEnd, tonight.darkEnd)
        assertEquals(setOf(TestDeepSky.M31.id), tonight.visible.keys)
        val m31 = tonight.visible.getValue(TestDeepSky.M31.id)
        assertEquals(peak, m31.bestTime)
        assertEquals(70.0, m31.altitudeDegrees, 1e-9)
    }

    @Test
    fun `a night that never gets dark has nothing to show`() = runTest {
        val useCase = ObserveDeepSkyTonightUseCase(
            FakeDarkSkyCalculator(listOf(night(NightDarkness.NONE))),
            FakeSkyPositionCalculator(),
            time,
            StandardTestDispatcher(testScheduler),
        )

        assertNull(useCase.tonight(observer, TestDeepSky.objects(), time.now()))
        assertNull(useCase(observer, TestDeepSky.objects()).first())
    }
}

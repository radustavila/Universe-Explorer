package com.radustavila.nasaexplorer.core.domain.usecase

import app.cash.turbine.test
import com.radustavila.nasaexplorer.core.model.MilkyWayUnavailable
import com.radustavila.nasaexplorer.core.testing.data.TestMilkyWay
import com.radustavila.nasaexplorer.core.testing.fake.FakeMilkyWayCalculator
import com.radustavila.nasaexplorer.core.testing.fake.FakeTimeProvider
import java.time.Instant
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ObserveMilkyWayOutlookUseCaseTest {

    private val noon = Instant.parse("2026-07-10T12:00:00Z")
    private val clock = FakeTimeProvider(currentInstant = noon)
    private val calculator = FakeMilkyWayCalculator(TestMilkyWay.nights(), TestMilkyWay.SEASON)

    @Test
    fun `the outlook holds the coming nights and the season, and is recomputed as time passes`() = runTest {
        val observe = ObserveMilkyWayOutlookUseCase(calculator, clock, StandardTestDispatcher(testScheduler))

        observe(TestMilkyWay.MADRID).test {
            val outlook = awaitItem()
            assertEquals(ObserveMilkyWayOutlookUseCase.NIGHTS, outlook.nights.size)
            assertEquals(TestMilkyWay.SEASON, outlook.season)
            assertNull(outlook.unavailable)
            assertEquals(TestMilkyWay.MADRID to noon, calculator.requests.single())

            clock.currentInstant = noon.plus(ObserveMilkyWayOutlookUseCase.RECOMPUTE_INTERVAL)
            advanceTimeBy(ObserveMilkyWayOutlookUseCase.RECOMPUTE_INTERVAL.toMillis() + 1)
            awaitItem()
            assertEquals(clock.currentInstant, calculator.requests.last().second)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `nights that never get dark explain themselves`() = runTest {
        calculator.nights = List(5) { TestMilkyWay.polarDay() }
        val observe = ObserveMilkyWayOutlookUseCase(calculator, clock, StandardTestDispatcher(testScheduler))

        observe(TestMilkyWay.MADRID, nights = 5).test {
            assertEquals(MilkyWayUnavailable.NO_DARKNESS, awaitItem().unavailable)
            cancelAndIgnoreRemainingEvents()
        }
    }
}

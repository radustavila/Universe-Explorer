package com.radustavila.nasaexplorer.core.domain.usecase

import app.cash.turbine.test
import com.radustavila.nasaexplorer.core.common.error.AppError
import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.common.time.TimeProvider
import com.radustavila.nasaexplorer.core.model.SyncSource
import com.radustavila.nasaexplorer.core.testing.data.TestDeepSpaceNetwork
import com.radustavila.nasaexplorer.core.testing.fake.FakeDeepSpaceNetworkRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeNetworkMonitor
import com.radustavila.nasaexplorer.core.testing.fake.FakeSyncMetadataRepository
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeepSpaceNetworkUseCasesTest {

    private val repository = FakeDeepSpaceNetworkRepository()
    private val metadata = FakeSyncMetadataRepository()
    private val network = FakeNetworkMonitor()

    /** A clock that follows the test's virtual time, so poll intervals elapse for the bookkeeping too. */
    private fun TestScope.virtualClock() = object : TimeProvider {
        override fun now(): Instant = START.plusMillis(testScheduler.currentTime)
        override fun today(): LocalDate = now().atZone(ZoneOffset.UTC).toLocalDate()
    }

    private fun TestScope.poll() =
        PollDeepSpaceNetworkUseCase(repository, RefreshSourceUseCase(metadata, virtualClock()), network)

    @Test
    fun `polls the live feed every interval while collected, and stops when cancelled`() = runTest {
        val poll = poll()
        val job = launch { poll() }
        runCurrent()
        assertEquals(1, repository.liveRefreshCount)

        testScheduler.advanceTimeBy(PollDeepSpaceNetworkUseCase.POLL_INTERVAL.toMillis() * 3)
        runCurrent()
        assertEquals(4, repository.liveRefreshCount)

        job.cancel()
        testScheduler.advanceTimeBy(Duration.ofMinutes(10).toMillis())
        runCurrent()
        assertEquals(4, repository.liveRefreshCount)
    }

    @Test
    fun `names and distances are fetched once while they stay fresh`() = runTest {
        val job = launch { poll()() }
        testScheduler.advanceTimeBy(Duration.ofHours(1).toMillis())
        runCurrent()
        job.cancel()

        assertEquals(1, repository.configRefreshCount)
        assertEquals(1, repository.distancesRefreshCount)
        assertEquals(START, metadata.statusOf(SyncSource.HORIZONS).lastSuccessAt)
        assertTrue(metadata.statusOf(SyncSource.DSN_NOW).lastSuccessAt!!.isAfter(START))
    }

    @Test
    fun `offline it waits without calling or recording failures`() = runTest {
        network.setOnline(false)
        val job = launch { poll()() }
        testScheduler.advanceTimeBy(Duration.ofMinutes(2).toMillis())
        runCurrent()

        assertEquals(0, repository.liveRefreshCount)
        assertEquals(false, metadata.statusOf(SyncSource.DSN_NOW).isFailing)

        network.setOnline(true)
        testScheduler.advanceTimeBy(PollDeepSpaceNetworkUseCase.POLL_INTERVAL.toMillis())
        runCurrent()
        job.cancel()
        assertEquals(1, repository.liveRefreshCount)
    }

    @Test
    fun `a failed poll is recorded and retried on the next one`() = runTest {
        repository.liveResult = DataResult.Failure(AppError.Timeout)
        val job = launch { poll()() }
        runCurrent()
        assertTrue(metadata.statusOf(SyncSource.DSN_NOW).isFailing)

        repository.liveResult = DataResult.Success(Unit)
        testScheduler.advanceTimeBy(PollDeepSpaceNetworkUseCase.POLL_INTERVAL.toMillis())
        runCurrent()
        job.cancel()

        assertEquals(2, repository.liveRefreshCount)
        assertEquals(false, metadata.statusOf(SyncSource.DSN_NOW).isFailing)
    }

    @Test
    fun `right now names the farthest spacecraft with a signal on`() = runTest {
        ObserveDsnRightNowUseCase(repository)().test {
            assertNull(awaitItem())

            repository.snapshot.value = TestDeepSpaceNetwork.snapshot()
            val rightNow = awaitItem()!!
            assertEquals("Voyager 2", rightNow.spacecraft.displayName)
            assertEquals("Canberra", rightNow.siteName)
            assertEquals(21_600_000_000.0, rightNow.distanceKm!!, 0.0)
        }
    }

    @Test
    fun `right now is empty while every dish is quiet`() = runTest {
        val quietDish = TestDeepSpaceNetwork.dish(links = listOf(TestDeepSpaceNetwork.link(active = false)))
        val quiet = TestDeepSpaceNetwork.snapshot(
            sites = listOf(
                TestDeepSpaceNetwork.madrid,
                TestDeepSpaceNetwork.canberra.copy(dishes = listOf(quietDish)),
            ),
        )
        repository.snapshot.value = quiet

        ObserveDsnRightNowUseCase(repository)().test {
            assertNull(awaitItem())
        }
    }

    @Test
    fun `a spacecraft without a distance is named only when nothing else is in contact`() = runTest {
        val talkingMro = TestDeepSpaceNetwork.mro.copy(downSignals = TestDeepSpaceNetwork.link().downSignals)
        val goldstone = TestDeepSpaceNetwork.goldstone.copy(
            dishes = listOf(TestDeepSpaceNetwork.dish(links = listOf(talkingMro))),
        )
        repository.snapshot.value = TestDeepSpaceNetwork.snapshot(sites = listOf(goldstone))

        ObserveDsnRightNowUseCase(repository)().test {
            assertEquals("MRO", awaitItem()!!.spacecraft.code)
        }
    }

    private companion object {
        val START: Instant = Instant.parse("2026-10-22T16:00:00Z")
    }
}

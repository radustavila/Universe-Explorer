package com.radustavila.nasaexplorer.core.domain.usecase

import com.radustavila.nasaexplorer.core.testing.data.TestApods
import com.radustavila.nasaexplorer.core.testing.fake.FakeApodRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeLatestApodWidgetUpdater
import com.radustavila.nasaexplorer.core.testing.fake.FakeNewApodNotifier
import com.radustavila.nasaexplorer.core.testing.fake.FakeSyncMetadataRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeUserPreferencesRepository
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnnounceLatestApodUseCaseTest {

    private val repository = FakeApodRepository()
    private val widgetUpdater = FakeLatestApodWidgetUpdater()
    private val notifier = FakeNewApodNotifier()

    private val today = LocalDate.of(2026, 9, 10)
    private val yesterday = today.minusDays(1)

    @Test
    fun `a newer entry is announced when notifications are enabled`() = runTest {
        repository.setEntries(TestApods.feed(size = 3, startDate = today))
        val metadata = FakeSyncMetadataRepository(initialLastAnnounced = yesterday)

        announce(metadata, notificationsEnabled = true)

        assertEquals(listOf(today), notifier.notified.map { it.date })
        assertEquals(today, metadata.currentLastAnnounced)
    }

    @Test
    fun `an entry already announced is not announced twice`() = runTest {
        repository.setEntries(TestApods.feed(size = 3, startDate = today))
        val metadata = FakeSyncMetadataRepository(initialLastAnnounced = today)

        announce(metadata, notificationsEnabled = true)

        assertTrue(notifier.notified.isEmpty())
    }

    @Test
    fun `the first run only records a baseline`() = runTest {
        repository.setEntries(TestApods.feed(size = 3, startDate = today))
        val metadata = FakeSyncMetadataRepository(initialLastAnnounced = null)

        announce(metadata, notificationsEnabled = true)

        assertTrue(notifier.notified.isEmpty())
        assertEquals(today, metadata.currentLastAnnounced)
    }

    @Test
    fun `with notifications disabled nothing is posted but the entry still counts as seen`() = runTest {
        repository.setEntries(TestApods.feed(size = 3, startDate = today))
        val metadata = FakeSyncMetadataRepository(initialLastAnnounced = yesterday)

        announce(metadata, notificationsEnabled = false)

        assertTrue(notifier.notified.isEmpty())
        assertEquals(today, metadata.currentLastAnnounced)
    }

    @Test
    fun `widgets are refreshed after every sync, even with nothing new to announce`() = runTest {
        repository.setEntries(TestApods.feed(size = 1, startDate = today))
        val metadata = FakeSyncMetadataRepository(initialLastAnnounced = today)

        announce(metadata, notificationsEnabled = false)

        assertEquals(1, widgetUpdater.updateCallCount)
    }

    @Test
    fun `an empty cache announces nothing`() = runTest {
        val metadata = FakeSyncMetadataRepository(initialLastAnnounced = yesterday)

        announce(metadata, notificationsEnabled = true)

        assertTrue(notifier.notified.isEmpty())
        assertEquals(yesterday, metadata.currentLastAnnounced)
    }

    private suspend fun announce(metadata: FakeSyncMetadataRepository, notificationsEnabled: Boolean) =
        AnnounceLatestApodUseCase(
            apodRepository = repository,
            syncMetadataRepository = metadata,
            userPreferencesRepository = FakeUserPreferencesRepository(notificationsEnabled),
            widgetUpdater = widgetUpdater,
            notifier = notifier,
        ).invoke()
}

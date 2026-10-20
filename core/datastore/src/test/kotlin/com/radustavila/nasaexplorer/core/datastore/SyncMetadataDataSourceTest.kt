package com.radustavila.nasaexplorer.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.radustavila.nasaexplorer.core.common.error.AppError
import com.radustavila.nasaexplorer.core.domain.model.SourceSyncStatus
import com.radustavila.nasaexplorer.core.model.SyncSource
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SyncMetadataDataSourceTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var syncMetadata: SyncMetadataDataSource

    @Before
    fun setUp() {
        dataStore = PreferenceDataStoreFactory.create(
            scope = TestScope(UnconfinedTestDispatcher()),
            produceFile = { temporaryFolder.newFile("sync_metadata.preferences_pb") },
        )
        syncMetadata = SyncMetadataDataSource(dataStore)
    }

    @Test
    fun `reports nothing recorded on a fresh install`() = runTest {
        assertTrue(syncMetadata.sourceStatuses.first().isEmpty())
        assertEquals(SourceSyncStatus(SyncSource.APOD), syncMetadata.observeSourceStatus(SyncSource.APOD).first())
        assertNull(syncMetadata.oldestRequestedDate.first())
        assertNull(syncMetadata.lastAnnouncedApodDate.first())
    }

    @Test
    fun `remembers the days without an entry across additions, ignoring garbage`() = runTest {
        assertTrue(syncMetadata.apodDatesWithoutEntry.first().isEmpty())

        syncMetadata.addApodDatesWithoutEntry(setOf(LocalDate.of(1995, 6, 21)))
        syncMetadata.addApodDatesWithoutEntry(setOf(LocalDate.of(2010, 10, 19)))
        dataStore.edit { it[stringSetPreferencesKey("apod_dates_without_entry")] = it[stringSetPreferencesKey("apod_dates_without_entry")].orEmpty() + "garbage" }

        assertEquals(
            setOf(LocalDate.of(1995, 6, 21), LocalDate.of(2010, 10, 19)),
            syncMetadata.apodDatesWithoutEntry.first(),
        )
    }

    @Test
    fun `records success, failure and the next allowed refresh per source`() = runTest {
        val success = Instant.parse("2026-09-10T09:30:00Z")
        val failure = Instant.parse("2026-09-10T10:30:00Z")
        val cooldown = Instant.parse("2026-09-10T10:31:30Z")

        syncMetadata.recordSyncSuccess(SyncSource.LAUNCHES, at = success, nextAllowedRefreshAt = success.plusSeconds(3_600))
        syncMetadata.recordSyncFailure(
            SyncSource.LAUNCHES,
            at = failure,
            error = AppError.RateLimited(retryAfterSeconds = 90),
            nextAllowedRefreshAt = cooldown,
        )

        val status = syncMetadata.observeSourceStatus(SyncSource.LAUNCHES).first()
        assertEquals(success, status.lastSuccessAt)
        assertEquals(failure, status.lastFailureAt)
        assertEquals("RateLimited(retryAfterSeconds=90)", status.lastErrorDescription)
        assertEquals(cooldown, status.nextAllowedRefreshAt)
        assertTrue(status.isFailing)
        // Other sources are untouched and only recorded ones are listed.
        assertEquals(listOf(SyncSource.LAUNCHES), syncMetadata.sourceStatuses.first().map { it.source })
    }

    @Test
    fun `a success without an interval clears a previous cooldown`() = runTest {
        val at = Instant.parse("2026-09-10T09:30:00Z")
        syncMetadata.recordSyncFailure(SyncSource.EPIC, at, AppError.Timeout, nextAllowedRefreshAt = at.plusSeconds(60))

        syncMetadata.recordSyncSuccess(SyncSource.EPIC, at = at.plusSeconds(120))

        assertNull(syncMetadata.observeSourceStatus(SyncSource.EPIC).first().nextAllowedRefreshAt)
    }

    @Test
    fun `the APOD last sync recorded before per-source bookkeeping is still reported`() = runTest {
        val legacy = Instant.parse("2026-09-01T08:00:00Z")
        dataStore.edit { it[longPreferencesKey("last_successful_sync_epoch_millis")] = legacy.toEpochMilli() }

        assertEquals(legacy, syncMetadata.observeSourceStatus(SyncSource.APOD).first().lastSuccessAt)
    }

    @Test
    fun `persists how far back the archive has been requested`() = runTest {
        val date = LocalDate.of(2026, 8, 20)

        syncMetadata.setOldestRequestedDate(date)

        assertEquals(date, syncMetadata.oldestRequestedDate.first())
    }

    @Test
    fun `persists the last announced entry`() = runTest {
        val date = LocalDate.of(2026, 9, 10)

        syncMetadata.setLastAnnouncedApodDate(date)

        assertEquals(date, syncMetadata.lastAnnouncedApodDate.first())
    }

    @Test
    fun `persists and forgets the last wallpaper entry`() = runTest {
        val date = LocalDate.of(2026, 10, 19)
        assertNull(syncMetadata.lastWallpaperApodDate.first())

        syncMetadata.setLastWallpaperApodDate(date)
        assertEquals(date, syncMetadata.lastWallpaperApodDate.first())

        syncMetadata.setLastWallpaperApodDate(null)
        assertNull(syncMetadata.lastWallpaperApodDate.first())
    }
}

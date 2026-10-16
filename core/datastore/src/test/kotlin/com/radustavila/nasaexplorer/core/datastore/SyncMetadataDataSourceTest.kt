package com.radustavila.nasaexplorer.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
        assertNull(syncMetadata.lastSuccessfulSync.first())
        assertNull(syncMetadata.oldestRequestedDate.first())
        assertNull(syncMetadata.lastAnnouncedApodDate.first())
    }

    @Test
    fun `persists and re-emits the last successful sync`() = runTest {
        val instant = Instant.parse("2026-09-10T09:30:00Z")

        syncMetadata.setLastSuccessfulSync(instant)

        assertEquals(instant, syncMetadata.lastSuccessfulSync.first())
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
}

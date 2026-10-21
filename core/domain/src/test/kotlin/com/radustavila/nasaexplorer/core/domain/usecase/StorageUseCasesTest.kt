package com.radustavila.nasaexplorer.core.domain.usecase

import com.radustavila.nasaexplorer.core.domain.storage.StorageEntry
import com.radustavila.nasaexplorer.core.domain.storage.StorageKind
import com.radustavila.nasaexplorer.core.model.SyncSource
import com.radustavila.nasaexplorer.core.testing.fake.FakeCacheStorage
import com.radustavila.nasaexplorer.core.testing.fake.FakeSyncMetadataRepository
import java.io.IOException
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StorageUseCasesTest {

    private val images = FakeCacheStorage(StorageKind.IMAGE_CACHE, bytes = 40_000_000)
    private val http = FakeCacheStorage(StorageKind.HTTP_CACHE, bytes = 2_000_000)
    private val launches = FakeCacheStorage(StorageKind.CACHED_DATA, bytes = 300_000)
    private val events = FakeCacheStorage(StorageKind.CACHED_DATA, bytes = 500_000, keptBytes = 100_000)
    private val metadata = FakeSyncMetadataRepository()

    @Test
    fun `measures one entry per kind, in a stable order`() = runTest {
        val measure = MeasureStorageUseCase(setOf(launches, http, images, events))

        assertEquals(
            listOf(
                StorageEntry(StorageKind.IMAGE_CACHE, 40_000_000),
                StorageEntry(StorageKind.HTTP_CACHE, 2_000_000),
                StorageEntry(StorageKind.CACHED_DATA, 800_000),
            ),
            measure(),
        )
    }

    @Test
    fun `a source that cannot be measured is left out`() = runTest {
        http.failure = IOException("disk")

        assertEquals(listOf(StorageKind.IMAGE_CACHE), MeasureStorageUseCase(setOf(images, http))().map { it.kind })
    }

    @Test
    fun `clearing runs every cleaner and lets every source refresh at once`() = runTest {
        val at = Instant.parse("2026-10-21T10:00:00Z")
        metadata.recordSyncSuccess(SyncSource.EONET, at, nextAllowedRefreshAt = at.plusSeconds(10_800))

        val cleared = ClearCacheUseCase(setOf(images, http, events), metadata)()

        assertTrue(cleared)
        assertEquals(listOf(1, 1, 1), listOf(images.clearCount, http.clearCount, events.clearCount))
        assertEquals(100_000L, events.bytes)
        assertNull(metadata.statusOf(SyncSource.EONET).nextAllowedRefreshAt)
        assertEquals(at, metadata.statusOf(SyncSource.EONET).lastSuccessAt)
    }

    @Test
    fun `one failing cleaner does not stop the others`() = runTest {
        http.failure = IOException("busy")

        val cleared = ClearCacheUseCase(setOf(http, images), metadata)()

        assertFalse(cleared)
        assertEquals(1, images.clearCount)
    }
}

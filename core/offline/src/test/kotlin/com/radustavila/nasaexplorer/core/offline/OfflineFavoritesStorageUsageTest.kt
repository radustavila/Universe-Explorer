package com.radustavila.nasaexplorer.core.offline

import com.radustavila.nasaexplorer.core.domain.storage.StorageEntry
import com.radustavila.nasaexplorer.core.domain.storage.StorageKind
import com.radustavila.nasaexplorer.core.testing.fake.FakeOfflineFavoritesStorage
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class OfflineFavoritesStorageUsageTest {

    @Test
    fun `reports the offline copies' size under their own kind`() = runTest {
        val usage = OfflineFavoritesStorageUsage(FakeOfflineFavoritesStorage(initialBytes = 2_500_000))

        assertEquals(listOf(StorageEntry(StorageKind.OFFLINE_FAVORITES, 2_500_000)), usage.measure())
    }
}
